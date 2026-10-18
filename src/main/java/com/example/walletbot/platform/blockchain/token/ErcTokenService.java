package com.example.walletbot.platform.blockchain.token;

import com.example.walletbot.platform.blockchain.BlockchainService;
import com.example.walletbot.platform.blockchain.chain.EvmBlockchainService;
import com.example.walletbot.shared.constant.BlockchainNetwork;
import com.example.walletbot.shared.constant.TokenInfo;

import java.math.BigDecimal;

public class ErcTokenService implements BlockchainService {

    private final BlockchainNetwork network;
    private final TokenInfo token;
    private final EvmBlockchainService evmService;
    private final String contractOverride;

    public ErcTokenService(BlockchainNetwork network, TokenInfo token, EvmBlockchainService evmService,
                           String contractOverride) {
        this.network = network;
        this.token = token;
        this.evmService = evmService;
        this.contractOverride = contractOverride;
    }

    @Override
    public BlockchainNetwork getNetwork() {
        return network;
    }

    @Override
    public WalletKeys generateWallet() {
        return evmService.generateWallet();
    }

    @Override
    public String getBalance(String address) {
        if (contractOverride != null) {
            return evmService.getTokenBalance(address, contractOverride, network.getDecimals());
        }
        return evmService.getTokenBalance(address, token, network);
    }

    @Override
    public String send(String privateKey, String toAddress, BigDecimal amount) {
        if (contractOverride != null) {
            return evmService.sendToken(privateKey, toAddress, amount, contractOverride, network.getDecimals());
        }
        return evmService.sendToken(privateKey, toAddress, amount, token, network);
    }

    @Override
    public boolean isValidAddress(String address) {
        return evmService.isValidAddress(address);
    }

    @Override
    public BigDecimal estimateSendFee() {
        if (contractOverride != null) {
            return evmService.estimateTokenGasCost(contractOverride);
        }
        return evmService.estimateTokenGasCost(token, network);
    }

    @Override
    public String deriveAddress(String privateKey) {
        return evmService.deriveAddress(privateKey);
    }

    @Override
    public String getFeeSymbol() {
        return evmService.getNetwork().getSymbol();
    }

    @Override
    public TxStatusResult getTxStatus(String txHash, String fromAddress) {
        return evmService.getTxStatus(txHash, fromAddress);
    }
}