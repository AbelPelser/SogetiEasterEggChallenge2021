package org.easteregg.chaincode;

import lombok.extern.slf4j.Slf4j;
import org.hyperledger.fabric.shim.ChaincodeBase;
import org.hyperledger.fabric.shim.ChaincodeStub;

@Slf4j
class SkeletonCC extends ChaincodeBase {

    // Only exists for unit testing
    Invocation makeInvocation(ChaincodeStub chaincodeStub) {
        return new Invocation(chaincodeStub);
    }

    // Called when instantiating the Chaincode
    @Override
    public Response init(ChaincodeStub chaincodeStub) {
        log.info(createConspicuousString("INIT"));
        return makeInvocation(chaincodeStub).performInit();
    }

    // Called when invoking the Chaincode
    @Override
    public Response invoke(ChaincodeStub chaincodeStub) {
        log.info(createConspicuousString("INVOCATION"));
        Invocation invocation = makeInvocation(chaincodeStub);
        Response result = invocation.performInvocation();
        String payloadStr = result.getPayload() == null ? "" : result.getStringPayload();
        log.info("Result of invocation: payload = {}, message = {}", payloadStr, result.getMessage());
        return result;
    }

    private static String createConspicuousString(String s) {
        return "\n\n================ " + s + " ================\n\n";
    }

    public static void main(String[] args) {
        new SkeletonCC().start(args);
    }
}
