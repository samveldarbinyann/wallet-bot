package com.example.walletbot.transaction.domain;

import com.example.walletbot.platform.blockchain.BlockchainService;
import com.example.walletbot.platform.blockchain.BlockchainServiceFactory;
import com.example.walletbot.shared.constant.BlockchainNetwork;
import com.example.walletbot.shared.exception.BadRequestException;
import com.example.walletbot.shared.util.AddressNormalizer;
import com.example.walletbot.user.domain.UserEntity;
import com.example.walletbot.wallet.application.WalletService;
import com.example.walletbot.wallet.domain.AddressPolicyService;
import com.example.walletbot.wallet.domain.WalletEntity;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

@Service
@RequiredArgsConstructor
public class TransferValidator {

    private final WalletService walletService;
    private final BlockchainServiceFactory blockchainFactory;
    private final AddressPolicyService addressPolicyService;
    private final AddressNormalizer normalizer;

    public Target validate(UserEntity user, BlockchainNetwork network,
                           String toAddress, BigDecimal amount) {

        BlockchainService svc = blockchainFactory.getService(network);

        if (!svc.isSendSupported()) {
            throw new BadRequestException("error.send_not_supported", network.getButtonLabel());
        }

        WalletEntity wallet = walletService.getWalletForNetwork(user, network);
        if (wallet == null) {
            throw new BadRequestException("error.no_wallet_for_network", network.name());
        }

        addressPolicyService.requireValid(network, toAddress);

        if (normalizer.sameAddress(network, wallet.getAddress(), toAddress)) {
            throw new BadRequestException("error.send_to_own_address");
        }

        validateAmount(network, amount);

        return new Target(wallet, normalizer.canonical(network, toAddress));
    }

    public void validateAmount(BlockchainNetwork network, BigDecimal amount) {
        if (amount == null || amount.signum() <= 0) {
            throw new BadRequestException("error.amount_not_positive");
        }
        validateAmountBounds(network, amount);
    }

    private void validateAmountBounds(BlockchainNetwork network, BigDecimal amount) {
        if (amount.stripTrailingZeros().scale() > network.getDecimals()) {
            throw new BadRequestException("error.amount_decimal_overflow",
                    network.getButtonLabel(), network.getDecimals());
        }
        if (amount.compareTo(network.getMinSend()) < 0) {
            throw new BadRequestException("error.amount_below_minimum",
                    network.getMinSend(), network.getSymbol());
        }
        if (amount.compareTo(network.getMaxSend()) > 0) {
            throw new BadRequestException("error.amount_above_maximum",
                    network.getMaxSend(), network.getSymbol());
        }
    }

    public record Target(WalletEntity wallet, String canonicalTo) {
    }
}