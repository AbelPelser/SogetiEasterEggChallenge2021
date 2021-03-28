package client;

import static java.nio.charset.StandardCharsets.UTF_8;
import static shared.GlobalConfig.COMPOSITE_EVENT;
import static shared.GlobalConfig.CcFunction;

import client.user.UserContext;
import client.util.Util;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.util.Collection;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Scanner;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.regex.Pattern;
import java.util.stream.IntStream;
import lombok.SneakyThrows;
import org.apache.commons.io.FileUtils;
import org.hyperledger.fabric.sdk.BlockEvent;
import org.hyperledger.fabric.sdk.ChaincodeEvent;
import org.hyperledger.fabric.sdk.ChaincodeEventListener;
import org.hyperledger.fabric.sdk.ChaincodeID;
import org.hyperledger.fabric.sdk.Channel;
import org.hyperledger.fabric.sdk.HFClient;
import org.hyperledger.fabric.sdk.NetworkConfig;
import org.hyperledger.fabric.sdk.NetworkConfig.CAInfo;
import org.hyperledger.fabric.sdk.NetworkConfig.OrgInfo;
import org.hyperledger.fabric.sdk.Peer;
import org.hyperledger.fabric.sdk.ProposalResponse;
import org.hyperledger.fabric.sdk.TransactionProposalRequest;
import org.hyperledger.fabric.sdk.TransactionRequest;
import org.hyperledger.fabric.sdk.exception.BaseException;
import org.hyperledger.fabric.sdk.exception.InvalidArgumentException;
import org.hyperledger.fabric.sdk.exception.ProposalException;
import org.hyperledger.fabric.sdk.security.CryptoSuite;
import org.hyperledger.fabric_ca.sdk.HFCAClient;
import org.hyperledger.fabric_ca.sdk.RegistrationRequest;
import org.json.JSONArray;
import org.json.JSONObject;


// The base class that all Clients inherit from.
public abstract class PiClient {

    public static final boolean LOG_TO_FILE = true;
    public static final int INVOCATION_TIMEOUT_S = 600;
    public static final int N_INVOCATION_RETRIES = 3;
    private static final String ADMIN_NAME = "Admin";
    private final PrintStream out = System.out;
    private final HashSet<String> receivedValidBlockEventTxIds = new HashSet<>();
    private final HashSet<String> receivedInvalidBlockEventTxIds = new HashSet<>();
    private final HashMap<String, Long> txSendTimes = new HashMap<>();
    private final ConcurrentLinkedQueue<String> pendingTxs = new ConcurrentLinkedQueue<>();
    protected JSONObject jo;
    protected OrgInfo orgInfo;
    private HFClient hfClient;
    private HFCAClient hfcaClient;
    private Channel channel;
    private String affiliation;
    private String ccName;
    private String msp;
    private String currentNetworkId;
    private String logFileName;

    protected PiClient(String jsonConfigFile) {
        setup(jsonConfigFile);
        setTxConfirmationListener();
    }

    private static void logToFile(String fileName, String contents) {
        try {
            PrintStream fileStream = new PrintStream(new FileOutputStream(fileName, true));
            fileStream.append(contents).append('\n');
            fileStream.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    protected abstract void run();

    @SneakyThrows
    private void setup(String jsonConfigFile) {
        File jsonFile = new File(jsonConfigFile);
        jo = new JSONObject(FileUtils.readFileToString(jsonFile, "utf-8"));
        logFileName = jo.getString("logfile");
        emptyFile(logFileName);
        NetworkConfig nc = NetworkConfig.fromJsonFile(new File(jo.getString("connection_profile")));
        affiliation = jo.getString("affiliation");
        ccName = jo.getJSONObject("chaincode").getString("name");
        orgInfo = nc.getOrganizationInfo(jo.getString("organization"));
        msp = orgInfo.getMspId();

        hfClient = HFClient.createNewInstance();
        hfClient.setCryptoSuite(CryptoSuite.Factory.getCryptoSuite());
        hfcaClient = HFCAClient.createNewInstance(orgInfo.getCertificateAuthorities().get(0));
        hfcaClient.setCryptoSuite(CryptoSuite.Factory.getCryptoSuite());

        currentNetworkId = jo.getString("network_version");
        Util.setWorkingDirectory(jo.getString("working_directory"));
        cleanupIfNeeded();
        String username = promptForUsername();
        UserContext userContext = getNormalUserContext(username);
        hfClient.setUserContext(userContext);
        String channelName = nc.getChannelNames().iterator().next();
        hfClient.loadChannelFromConfig(channelName, nc);
        channel = hfClient.getChannel(channelName);
    }

    @SneakyThrows
    private void cleanupIfNeeded() {
        String networkId = Optional.ofNullable(Util.readUserContext(orgInfo.getName(), ADMIN_NAME))
            .map(UserContext::getNetworkId)
            .orElse(null);
        if (!currentNetworkId.equals(networkId)) {
            Util.cleanUp();
        }
    }

    private String promptForUsername() {
        p("Enter your name: ", true);
        Scanner scanner = new Scanner(System.in);
        return scanner.nextLine();
    }

    private NetworkConfig.UserInfo getFirstRegistrar(CAInfo caInfo) {
        return caInfo.getRegistrars().stream().findFirst().orElse(null);
    }

    private NetworkConfig.UserInfo getCaRegistrar() {
        return orgInfo.getCertificateAuthorities().stream()
            .findFirst()
            .map(this::getFirstRegistrar)
            .orElseThrow(() -> new IllegalStateException("No CA registrars found"));
    }

    private void emptyFile(String logFileName) {
        try {
            new PrintWriter(logFileName).close();
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
    }

    private UserContext getBaseUserContext() {
        UserContext userContext = new UserContext();
        userContext.setAffiliation(orgInfo.getName());
        userContext.setNetworkId(currentNetworkId);
        userContext.setMspId(msp);
        return userContext;
    }

    @SneakyThrows
    private UserContext getAdminUserContext() {
        UserContext adminContext = Util.readUserContext(orgInfo.getName(), ADMIN_NAME);
        if (adminContext != null) {
            return adminContext;
        }
        adminContext = getBaseUserContext();
        adminContext.setName(ADMIN_NAME);

        NetworkConfig.UserInfo adminRegistrar = getCaRegistrar();
        adminContext.setEnrollment(hfcaClient.enroll(adminRegistrar.getName(), adminRegistrar.getEnrollSecret()));
        Util.writeUserContext(adminContext);
        return adminContext;
    }

    @SneakyThrows
    private UserContext getNormalUserContext(String username) {
        UserContext userContext = Util.readUserContext(affiliation, username);
        if (userContext != null) {
            p("Found existing enrollment for " + username);
            return userContext;
        }
        UserContext adminContext = getAdminUserContext();
        String userEnrollmentSecret = hfcaClient.register(new RegistrationRequest(username, affiliation), adminContext);
        userContext = getBaseUserContext();
        userContext.setName(username);
        userContext.setEnrollment(hfcaClient.enroll(username, userEnrollmentSecret));
        p("Storing user enrollment for " + username + " of " + affiliation);
        Util.writeUserContext(userContext);
        return userContext;
    }

    protected void initChannel() throws BaseException {
        channel.initialize();
    }

    // Register an event listener for BlockEvents, which will be used for detecting finished/failed transactions.
    @SneakyThrows
    private void setTxConfirmationListener() {
        channel.registerBlockListener(this::processBlockEvent);
    }

    // Looks at the transactions that are stored in a block, and checks if any pending transactions are now finished.
    private void processBlockEvent(BlockEvent blockEvent) {
        for (BlockEvent.TransactionEvent txEvent : blockEvent.getTransactionEvents()) {
            String txId = txEvent.getTransactionID();
            // Check if we have processed it before, and add it to the right set.
            boolean isNew = txEvent.isValid() ? addValidBlockEvent(txId) : addInvalidBlockEvent(txId);
            // If this event is about a pending transaction, we must mark it as done by removing it from the list (even if invalid)
            markAsDone(txEvent, txId);
            if (isNew) {
                printResponseTime(txId);
            }
        }
    }

    private synchronized boolean addInvalidBlockEvent(String txId) {
        boolean isNew = !receivedInvalidBlockEventTxIds.contains(txId);
        if (isNew) {
            p("Detected invalid transaction " + txId + "!");
            receivedInvalidBlockEventTxIds.add(txId);
        }
        return isNew;
    }

    private synchronized boolean addValidBlockEvent(String txId) {
        boolean isNew = !receivedValidBlockEventTxIds.contains(txId);
        if (isNew) {
            receivedValidBlockEventTxIds.add(txId);
        }
        return isNew;
    }

    private synchronized void markAsDone(BlockEvent.TransactionEvent txEvent, String txId) {
        if (pendingTxs.contains(txId)) {
            p("Pending transaction " + txId + " completed");
            pendingTxs.remove(txEvent.getTransactionID());
        }
    }

    private synchronized void printResponseTime(String txId) {
        if (txSendTimes.containsKey(txId)) {
            long currentTime = System.nanoTime();
            long txSendTime = txSendTimes.get(txId);
            p("Event response time for " + txId + " was " + (currentTime - txSendTime) + " nanoseconds!");
        }
    }

    // Convenience wrappers for invokeCC
    protected String invokeCC(CcFunction function, String... args) {
        return invokeCC(function, channel.getPeers(EnumSet.of(Peer.PeerRole.ENDORSING_PEER)), args);
    }

    protected String invokeCC(CcFunction function, Collection<Peer> peers, String... args) {
        return invokeCC(function, peers, N_INVOCATION_RETRIES, args);
    }

    protected String invokeCC(CcFunction function, Collection<Peer> peers, int nRetries, String... args) {
        return invokeCC(function, peers, nRetries, true, args);
    }

    // Perform a Chaincode invocation.
    // Args:
    //   - function: member of a custom enum in shared.GlobalConfig, make sure to register your own Chaincode functions there
    //   - peers: The set of peers to request endorsement from; you can probably leave this alone (ie. call this function without providing it),
    //         unless you want to limit the number of peers to enhance performance
    //         DEFAULT: all endorsing peers in the channel.
    //   - nRetries: The number of retries of this invocation, in case it fails.
    //         DEFAULT: N_INVOCATION_RETRIES
    //   - blocking: If set to false, the function will return after obtaining endorsements. That means the invocation will not have been confirmed!
    //         Additionally, no retries will be attempted if set to false (you could change the code to support this).
    //         DEFAULT: true
    //   - args: The parameters for the Chaincode invocation
    // Returns:
    //   - The reply to the invocation
    private String invokeCC(CcFunction function, Collection<Peer> peers, int nRetries, boolean blocking,
        String... args) {
        long startTime = System.nanoTime();
        String dump = dumpInvocation(function, args);

        try {
            TransactionProposalRequest request = makeInvocationRequest(function, args);
            Collection<ProposalResponse> response = channel.sendTransactionProposal(request, peers);
            long stopTime = System.nanoTime();
            String txId = response.iterator().next().getTransactionID();
            txSendTimes.put(txId, stopTime);
            long timePassed = stopTime - startTime;
            p("Sending PROPOSAL for " + dump + "(" + txId + ") took " + timePassed + " nanoseconds!");
            return processProposalResponse(response, blocking);
        } catch (ProposalException e) {
            p(dump + " failed, key collision?  " + nRetries + " attempts to go!");
            if (nRetries == 0) {
                p(e.getMessage());
                e.printStackTrace();
                return "";
            } else {
                return invokeCC(function, peers, nRetries - 1, blocking, args);
            }
        } catch (InvalidArgumentException e) {
            return dump + " failed because it was invalid!";
        }
    }

    private String dumpInvocation(CcFunction function, String... args) {
        return "invokeCC(" + function.getName() + ", " + String.join(", ", args) + ")";
    }

    private TransactionProposalRequest makeInvocationRequest(CcFunction function, String[] args)
        throws InvalidArgumentException {
        TransactionProposalRequest request = hfClient.newTransactionProposalRequest();
        ChaincodeID ccid = ChaincodeID.newBuilder().setName(ccName).build();
        request.setChaincodeID(ccid);
        request.setProposalWaitTime(1000000);
        request.setFcn(function.getName());
        request.setArgs(args);
        request.setChaincodeLanguage(TransactionRequest.Type.JAVA);
        Map<String, byte[]> tm = new HashMap<>();
        tm.put("HyperLedgerFabric", "TransactionProposalRequest:JavaSDK".getBytes(UTF_8));
        tm.put("method", "TransactionProposalRequest".getBytes(UTF_8));
        request.setTransientMap(tm);
        return request;
    }

    private String processProposalResponse(Collection<ProposalResponse> response, boolean blocking)
        throws ProposalException {
        String result = null, txId = null;
        // What do all of these peers think of this idea?
        for (ProposalResponse pres : response) {
            result = pres.getMessage();
            txId = pres.getTransactionID();
            try {
                pres.getChaincodeActionResponsePayload();
            } catch (InvalidArgumentException e) {
                p("Transaction proposal FAILED on channel " + channel.getName() + " by peer " + pres.getPeer()
                    .getName() + "!\n");
                p("With message: " + pres.getMessage() + "\n" +
                    "Status: " + pres.getStatus() + "\n" +
                    "And transaction id: " + pres.getTransactionID());
            }
        }
        // Send off to the ordering service!
        pendingTxs.add(txId);
        channel.sendTransaction(response);
        long startWaitingTime = System.currentTimeMillis();
        if (blocking) {
            // Wait until the transaction gets confirmed
            while (true) {
                synchronized (pendingTxs) {
                    if (!pendingTxs.contains(txId)) {
                        break;
                    }
                }
                long timeWaitedSeconds = (System.currentTimeMillis() - startWaitingTime) / 1000;
                if (timeWaitedSeconds > INVOCATION_TIMEOUT_S) {
                    throw new ProposalException("Timeout reached without transaction confirmation!");
                }
                try {
                    Thread.sleep(10);
                } catch (InterruptedException ignored) {
                }
            }
            if (receivedInvalidBlockEventTxIds.contains(txId)) {
                throw new ProposalException("Transaction returned as invalid!");
            }
        }
        return result;
    }

    public void p(Object o) {
        p(o, false);
    }

    // Wrapper for System.out.print(ln).
    // Is synchronized to prevent mangled output, so multiple threads may use this function on the same object.
    // Does not add a newline if isPrompt is true.
    protected void p(Object o, boolean isPrompt) {
        if (o instanceof String && ((String) o).length() == 0) {
            return;
        }
        String output = "[" + getClass().getName() + "]: " + o;
        synchronized (out) {
            if (!isPrompt) {
                output += '\n';
            }
            out.print(output);
            out.flush();
        }

        if (LOG_TO_FILE) {
            logToFile(logFileName, output);
        }
    }
}
