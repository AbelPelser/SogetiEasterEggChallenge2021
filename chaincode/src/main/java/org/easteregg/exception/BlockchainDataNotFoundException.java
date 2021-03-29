package org.easteregg.exception;


public class BlockchainDataNotFoundException extends Exception {
    public BlockchainDataNotFoundException(String msg) {
        super("Data not on ledger: " + msg);
    }
}
