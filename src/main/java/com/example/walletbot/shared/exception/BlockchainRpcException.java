package com.example.walletbot.shared.exception;

public class BlockchainRpcException extends RuntimeException {
    public BlockchainRpcException(String message) {
        super(message);
    }
}
