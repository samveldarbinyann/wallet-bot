package com.example.walletbot.swap.application;

import com.example.walletbot.asset.application.PriceService;
import com.example.walletbot.platform.blockchain.BlockchainServiceFactory;
import com.example.walletbot.shared.constant.BlockchainNetwork;
import com.example.walletbot.shared.exception.BadRequestException;
import com.example.walletbot.swap.application.intent.SwapIntentService;
import com.example.walletbot.swap.domain.SwapPair;
import com.example.walletbot.swap.domain.SwapQuote;
import com.example.walletbot.swap.domain.SwapRanges;
import com.example.walletbot.swap.domain.exception.SwapAmountAboveMaxException;
import com.example.walletbot.swap.domain.exception.SwapAmountBelowMinException;
import com.example.walletbot.swap.domain.exception.SwapQuoteUnavailableException;
import com.example.walletbot.swap.routing.SwapRouter;
import com.example.walletbot.transaction.domain.NetworkFee;
import com.example.walletbot.user.application.UserStatusGuard;
import com.example.walletbot.user.domain.UserEntity;
import com.example.walletbot.wallet.application.WalletService;
import com.example.walletbot.wallet.domain.WalletEntity;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class SwapQuoteService {

    private final SwapRouter router;
    private final WalletService walletService;
    private final PriceService priceService;
    private final SwapIntentService intentService;
    private final BlockchainServiceFactory blockchainFactory;
    private final UserStatusGuard userStatusGuard;

    public QuotedSwap quote(UserEntity user,
                            BlockchainNetwork fromNetwork,
                            BlockchainNetwork toNetwork,
                            BigDecimal amount) {
        userStatusGuard.requireCanTransact(user);
        if (fromNetwork == toNetwork) {
            throw new BadRequestException("error.swap_same_network");
        }
        if (amount == null || amount.signum() <= 0) {
            throw new BadRequestException("error.amount_not_positive");
        }
        WalletEntity fromWallet = walletService.getWalletForNetwork(user, fromNetwork);
        if (fromWallet == null) {
            throw new BadRequestException("error.no_source_wallet_for_network", fromNetwork.name());
        }
        WalletEntity toWallet = walletService.getWalletForNetwork(user, toNetwork);
        if (toWallet == null) {
            throw new BadRequestException("error.no_destination_wallet_for_network", toNetwork.name());
        }


        SwapPair pair = SwapPair.of(fromNetwork, toNetwork);

        Optional<SwapRanges> cachedRanges = router.rangesFor(pair);
        if (cachedRanges.isPresent()) {
            SwapRanges r = cachedRanges.get();
            if (r.min() != null && amount.compareTo(r.min()) < 0) {
                throw new SwapAmountBelowMinException(r.min(), fromNetwork.getSymbol());
            }
            if (r.max() != null && amount.compareTo(r.max()) > 0) {
                throw new SwapAmountAboveMaxException(r.max(), fromNetwork.getSymbol());
            }
        }

        SwapQuote quote;
        try {
            quote = router.selectBestQuote(pair, amount);
        } catch (SwapQuoteUnavailableException e) {
            log.info("Quote unavailable for pair {}->{}: {}", fromNetwork, toNetwork, e.getMessage());
            throw e;
        }

        SwapIntentService.IntentToken token =
                intentService.issue(user.getId(), quote, fromWallet.getAddress(), toWallet.getAddress());

        NetworkFee networkFee = buildNetworkFee(fromNetwork, fromWallet, quote);
        BigDecimal amountOutUsd = priceService.usdValue(toNetwork.getSymbol(), quote.amountOut());
        BigDecimal totalCostUsd = totalCostUsd(fromNetwork, quote, networkFee, amountOutUsd);
        return new QuotedSwap(quote, token, fromWallet, toWallet, networkFee, amountOutUsd, totalCostUsd);
    }

    private NetworkFee buildNetworkFee(BlockchainNetwork fromNetwork, WalletEntity fromWallet, SwapQuote quote) {
        if (!fromNetwork.isToken()) {
            return nativeMinerFee(fromNetwork);
        }
        BigDecimal gas = blockchainFactory.getService(fromNetwork)
                .estimateNativeBurn(fromWallet.getAddress(), null, quote.amountIn());
        return gasFee(fromNetwork, gas);
    }

    private NetworkFee nativeMinerFee(BlockchainNetwork fromNetwork) {
        return gasFee(fromNetwork, blockchainFactory.getService(fromNetwork).estimateSendFee());
    }

    private NetworkFee gasFee(BlockchainNetwork fromNetwork, BigDecimal amount) {
        if (amount == null || amount.signum() <= 0) {
            return null;
        }
        String symbol = fromNetwork.getGasNetwork().getSymbol();
        return new NetworkFee(amount, symbol, priceService.usdValue(symbol, amount));
    }

    public SourceFee sourceFee(BlockchainNetwork fromNetwork) {
        if (fromNetwork.isToken()) {
            return new SourceFee(null, null);
        }
        NetworkFee fee = nativeMinerFee(fromNetwork);
        if (fee == null) {
            return new SourceFee(null, null);
        }
        BigDecimal reserve = fee.amount().add(blockchainFactory.getService(fromNetwork).minRetainedBalance());
        return new SourceFee(fee, reserve);
    }

    public record SourceFee(NetworkFee networkFee, BigDecimal reserve) {
    }

    private BigDecimal totalCostUsd(BlockchainNetwork fromNetwork, SwapQuote quote,
                                    NetworkFee networkFee, BigDecimal amountOutUsd) {
        BigDecimal amountInUsd = priceService.usdValue(fromNetwork.getSymbol(), quote.amountIn());
        if (amountInUsd.signum() <= 0 || amountOutUsd.signum() <= 0) {
            return BigDecimal.ZERO;
        }
        BigDecimal networkUsd = networkFee == null ? BigDecimal.ZERO : networkFee.usdValue();
        return amountInUsd.add(networkUsd).subtract(amountOutUsd).max(BigDecimal.ZERO);
    }

    public record QuotedSwap(SwapQuote quote,
                             SwapIntentService.IntentToken intent,
                             WalletEntity fromWallet,
                             WalletEntity toWallet,
                             NetworkFee networkFee,
                             BigDecimal amountOutUsd,
                             BigDecimal totalCostUsd) {
    }
}
