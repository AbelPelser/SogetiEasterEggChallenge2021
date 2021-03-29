package org.easteregg.chaincode;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.bouncycastle.asn1.x500.style.BCStyle.CN;
import static shared.GlobalConfig.COMPOSITE_EVENT;
import static shared.GlobalConfig.CcFunction;

import com.google.protobuf.ByteString;
import org.easteregg.exception.BlockchainDataNotFoundException;
import java.io.Reader;
import java.io.StringReader;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import lombok.Getter;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.math3.util.Pair;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.openssl.PEMParser;
import org.hyperledger.fabric.protos.msp.Identities;
import org.hyperledger.fabric.shim.ChaincodeStub;
import org.json.JSONArray;
import org.json.JSONObject;

/*
 * Class used for managing a single invocation.
 * Extends SkeletonCC to have access to methods such as new{Success|Error}Response().
 */
@Slf4j
@Getter
public class Invocation extends SkeletonCC {

    static final String INCORRECT_N_ARGS_MSG = "Incorrect number of arguments!";
    static final String UNSUPPORTED_FUNCTION_MSG = " function is currently not supported!";
    private static final EggMetrics EGG_METRICS = new EggMetrics(30, 22, 50, 20, Color.WHITE.getColor(),
        Color.GREEN.getColor());
    private final ChaincodeStub chaincodeStub;
    private final LedgerService ledgerService = new LedgerService(this);
    private final ArrayList<Pair<String, JSONObject>> eventList = new ArrayList<>();
    private long timestamp;
    private String invokingId;

    public Invocation(ChaincodeStub chaincodeStub) {
        this.chaincodeStub = chaincodeStub;
        dumpInvocation();
        setup();
    }

    private void setup() {
        timestamp = Optional.ofNullable(chaincodeStub.getTxTimestamp())
            .map(Instant::toEpochMilli)
            .orElse(0L);
        invokingId = getCN();
    }

    // Parse certificate and retrieve common name (CN)
    @SneakyThrows
    private String getCN() {
        byte[] idByteArray = chaincodeStub.getCreator();
        ByteString idByteString = Identities.SerializedIdentity.parseFrom(idByteArray).getIdBytes();
        log.info("ID {}", Arrays.toString(idByteArray));
        Reader pemReader = new StringReader(new String(idByteString.toByteArray()));
        PEMParser pemParser = new PEMParser(pemReader);
        X509CertificateHolder cert = (X509CertificateHolder) pemParser.readObject();
        pemParser.close();
        String cn = cert.getSubject().getRDNs(CN)[0].getFirst().getValue().toString();
        log.info("CN = {}", cn);
        return cn;
    }

    Response performInit() {
        if (!chaincodeStub.getParameters().isEmpty()) {
            return newErrorResponse(INCORRECT_N_ARGS_MSG);
        }
        setup();
        return newSuccessResponse();
    }

    private void dumpInvocation() {
        log.info("{}({})", chaincodeStub.getFunction(), String.join(", ", chaincodeStub.getParameters()));
    }

    private void validateInvocation(CcFunction function, String methodName, List<String> paramList) {
        String errorMessage = null;
        if (function == null) {
            errorMessage = methodName + UNSUPPORTED_FUNCTION_MSG;
        } else if (paramList.size() != function.getNArgs()) {
            errorMessage = INCORRECT_N_ARGS_MSG;
        }
        if (errorMessage != null) {
            throw new IllegalArgumentException(errorMessage);
        }
    }

    // The central function that handles every invocation
    Response performInvocation() {
        setup();
        dumpInvocation();

        String methodName = chaincodeStub.getFunction();
        List<String> paramList = chaincodeStub.getParameters();
        CcFunction invokedFunction = CcFunction.fromString(methodName);
        try {
            validateInvocation(invokedFunction, methodName, paramList);
            Response response = executeMethodByName(methodName, paramList);
            sendEventQueue();
            return response;
        } catch (InvocationTargetException e) {
            log.error(e.getTargetException().getMessage());
            e.getTargetException().printStackTrace();
            return newSuccessResponse(e.getTargetException().getMessage());
        } catch (Exception e) {
            log.error(e.getMessage());
            e.printStackTrace();
            return newErrorResponse(e.getMessage());
        }
    }

    private Response executeMethodByName(String methodName, List<String> paramList)
        throws InvocationTargetException, NoSuchMethodException, IllegalAccessException {
        log.info("executeMethodByName({}, {})", methodName, String.join(", ", paramList));
        Method method = getClass().getDeclaredMethod(methodName, List.class);
        method.setAccessible(true);
        return (Response) method.invoke(this, paramList);
    }

    @Invokable
    private Response queryHistory(List<String> paramList) {
        return newSuccessResponse(ledgerService.queryHistoryWrapper(paramList.get(0)));
    }

    @Invokable
    private Response queryTestVar(List<String> paramList) throws BlockchainDataNotFoundException {
        return newSuccessResponse(ledgerService.getStringStateWrapper(paramList.get(0)));
    }

    @Invokable
    private Response setTestVar(List<String> paramList) {
        String varName = paramList.get(0);
        String newValue = paramList.get(1);
        ledgerService.putStringStateWrapper(varName, newValue);
        return newSuccessResponse();
    }

    @Invokable
    private Response getEgg(List<String> paramList) {
        return newSuccessResponse(createEgg());
    }

    private String createEgg() {
        int hashCode = invokingId.hashCode();
        double colorFactor = 15 + (hashCode % 35);
        double colorPowerFactor = 0.2 + (hashCode % 1.8);
        return new EasterEggBuilder(EGG_METRICS, colorFactor, colorPowerFactor).build();
    }

    @SuppressWarnings("SameParameterValue")
    void queueEvent(String eventName, String contents) {
        JSONObject jsonContents = new JSONObject();
        jsonContents.put("contents", contents);
        queueEvent(eventName, jsonContents);
    }

    void queueEvent(String eventName, JSONObject contents) {
        log.info("Queueing {} ({})", eventName, contents.toString());
        eventList.add(new Pair<>(eventName, contents));
    }

    private JSONArray getEventsAsJsonArray() {
        JSONArray eventsArrayJson = new JSONArray();
        for (Pair<String, JSONObject> eventPair : eventList) {
            JSONObject eventJson = new JSONObject();
            eventJson.put(eventPair.getKey(), eventPair.getValue());
            eventsArrayJson.put(eventJson);
        }
        return eventsArrayJson;
    }

    private JSONObject createCompositeEvent(JSONArray eventsJsonArray) {
        JSONObject compositeEventJson = new JSONObject();
        compositeEventJson.put("txId", chaincodeStub.getTxId());
        compositeEventJson.put("txTimestamp", timestamp);
        compositeEventJson.put("events", eventsJsonArray);
        return compositeEventJson;
    }

    private void sendEventQueue() {
        JSONArray eventsJsonArray = getEventsAsJsonArray();
        eventList.clear();
        if (!eventsJsonArray.isEmpty()) {
            JSONObject compositeEventJson = createCompositeEvent(eventsJsonArray);
            log.info("Setting event: {} ({})", COMPOSITE_EVENT, compositeEventJson.toString());
            chaincodeStub.setEvent(COMPOSITE_EVENT, compositeEventJson.toString().getBytes(UTF_8));
        }
    }
}
