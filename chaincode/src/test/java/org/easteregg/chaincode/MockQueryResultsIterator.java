package org.easteregg.chaincode;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import org.hyperledger.fabric.shim.ledger.KeyModification;
import org.hyperledger.fabric.shim.ledger.QueryResultsIterator;

final class MockQueryResultsIterator implements QueryResultsIterator<KeyModification> {

    private final List<KeyModification> resultList = new ArrayList<>();

    MockQueryResultsIterator(KeyModification... modifications) {
        super();
        Collections.addAll(resultList, modifications);
    }

    @Override
    public Iterator<KeyModification> iterator() {
        return resultList.iterator();
    }

    @Override
    public void close() {
    }
}
