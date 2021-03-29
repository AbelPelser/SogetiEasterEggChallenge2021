package org.easteregg.chaincode;

import java.time.Instant;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.hyperledger.fabric.shim.ledger.KeyModification;

@Getter
@RequiredArgsConstructor
class MockKeyModification implements KeyModification {
    private final String txId;
    private final String stringValue;
    private final Instant timestamp;
    private final byte[] value = new byte[0];
    private final boolean deleted;
}
