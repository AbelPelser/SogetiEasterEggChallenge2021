package org.easteregg.chaincode;

import org.easteregg.exception.BlockchainDataNotFoundException;
import java.util.HashMap;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.hyperledger.fabric.shim.ledger.KeyModification;
import org.hyperledger.fabric.shim.ledger.QueryResultsIterator;
import org.json.JSONArray;
import org.json.JSONObject;

/*
 * This class contains functions to help with reading values from/updating values to the ledger
 */
@Slf4j
class LedgerService {
    public static final boolean LOG_BC_ACTIONS = true;

    private final Invocation invocation;

    LedgerService(Invocation invocation) {
        this.invocation = invocation;
    }

    // Retrieves the latest value for a key up until a certain timestamp/txId combination.
    // The transaction belonging to txId MUST have taken place at the given timestamp
    // Assumes that not two transactions can take place on the same timestamp
    String getStringStateWrapper(String key) throws BlockchainDataNotFoundException {
        String result = invocation.getChaincodeStub().getStringState(key);
        if (LOG_BC_ACTIONS) {
            log.info("getStringState({}) = {}", key, result);
        }
        if (result == null || result.isEmpty()) {
            throw new BlockchainDataNotFoundException(key);
        }
        return result;
    }

    // Writes a key/value pair to the ledger.
    // You could change this function to only write if the value is different from the one already on the chain.
    void putStringStateWrapper(String key, String value) {
        if (LOG_BC_ACTIONS) {
            log.info("putStringState({}, {})", key, value);
        }
        invocation.getChaincodeStub().putStringState(key, value);
    }

    // Returns the history for a key, contained in a JSON object.
    String queryHistoryWrapper(String key) {
        QueryResultsIterator<KeyModification> queryResultsIterator = invocation.getChaincodeStub().getHistoryForKey(key);
        return buildJsonFromQueryResult(queryResultsIterator);
    }

    // Helper function for queryHistoryWrapper.
    // This function demonstrates what values are contained in the return value of getHistoryForKey.
    private String buildJsonFromQueryResult(QueryResultsIterator<KeyModification> queryResultsIterator) {
        JSONArray jsonArray = new JSONArray();
        queryResultsIterator.forEach(keyModification -> {
            Map<String, Object> map = new HashMap<>();
            map.put("transactionId", keyModification.getTxId());
            map.put("timestamp", keyModification.getTimestamp().toString());
            map.put("value", keyModification.getStringValue());
            map.put("isDeleted", keyModification.isDeleted());
            jsonArray.put(map);
        });
        JSONObject jsonObject = new JSONObject();
        jsonObject.accumulate("transactions", jsonArray);
        return jsonObject.toString();
    }
}