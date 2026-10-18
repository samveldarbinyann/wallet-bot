package com.example.walletbot.swap.provider.simpleswap;

import com.example.walletbot.platform.blockchain.BlockchainService;
import com.example.walletbot.platform.blockchain.BlockchainServiceFactory;
import com.example.walletbot.shared.exception.BlockchainRpcException;
import com.example.walletbot.shared.exception.InsufficientFundsException;
import com.example.walletbot.swap.domain.*;
import com.example.walletbot.swap.domain.exception.*;
import com.example.walletbot.swap.provider.SwapProvider;
import com.example.walletbot.swap.provider.simpleswap.dto.SimpleSwapExchangeDto;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.*;

@Slf4j
@Component
public class SimpleSwapProvider implements SwapProvider {

    public static final String ID = "simpleswap";

    private final SimpleSwapClient client;
    private final SimpleSwapCache cache;
    private final SimpleSwapCurrencyMap currencyMap;
    private final SimpleSwapProperties props;
    private final BlockchainServiceFactory blockchainFactory;

    public SimpleSwapProvider(SimpleSwapClient client,
                              SimpleSwapCache cache,
                              SimpleSwapCurrencyMap currencyMap,
                              SimpleSwapProperties props,
                              BlockchainServiceFactory blockchainFactory) {
        this.client = client;
        this.cache = cache;
        this.currencyMap = currencyMap;
        this.props = props;
        this.blockchainFactory = blockchainFactory;
    }

    @Override
    public String providerId() {
        return ID;
    }

    @Override
    public boolean supports(SwapPair pair) {
        return props.isConfigured()
                && currencyMap.supports(pair.from())
                && currencyMap.supports(pair.to());
    }

    @Override
    public int priority() {
        return props.priority();
    }

    @Override
    public Optional<SwapRanges> ranges(SwapPair pair) {
        if (!supports(pair)) {
            return Optional.empty();
        }
        SimpleSwapCurrency from = currencyMap.currency(pair.from());
        SimpleSwapCurrency to = currencyMap.currency(pair.to());
        return cache.ranges(from, to);
    }

    @Override
    public SwapQuote quote(SwapPair pair, BigDecimal amountIn) {
        if (!supports(pair)) {
            throw new SwapQuoteUnavailableException("SimpleSwap does not support pair " + pair);
        }
        SimpleSwapCurrency from = currencyMap.currency(pair.from());
        SimpleSwapCurrency to = currencyMap.currency(pair.to());

        SwapRanges ranges = cache.ranges(from, to).orElse(null);
        BigDecimal min = ranges != null ? ranges.min() : null;
        BigDecimal max = ranges != null ? ranges.max() : null;
        if (min != null && amountIn.compareTo(min) < 0) {
            throw new SwapAmountBelowMinException(min, pair.from().getSymbol());
        }
        if (max != null && amountIn.compareTo(max) > 0) {
            throw new SwapAmountAboveMaxException(max, pair.from().getSymbol());
        }

        BigDecimal estimated = Optional.ofNullable(cache.estimate(from, to, amountIn))
                .orElseThrow(() -> new SwapQuoteUnavailableException(
                        "SimpleSwap could not estimate " + pair.from() + "→" + pair.to()));

        BigDecimal rate = amountIn.signum() > 0
                ? estimated.divide(amountIn, 18, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

        BigDecimal amountOutMin = estimated.multiply(BigDecimal.ONE.subtract(props.slippagePercent()))
                .setScale(18, RoundingMode.HALF_DOWN);

        Map<String, String> meta = new HashMap<>();
        if (min != null) meta.put("minAmount", min.toPlainString());
        if (max != null) meta.put("maxAmount", max.toPlainString());

        BigDecimal feePercent = props.partnerFeePercent();
        BigDecimal feeAmount = estimated.multiply(feePercent).setScale(18, RoundingMode.HALF_UP);
        SwapFeeBreakdown fees = new SwapFeeBreakdown(feePercent, feeAmount);

        return new SwapQuote(
                ID,
                null,
                pair,
                amountIn.setScale(18, RoundingMode.HALF_DOWN),
                estimated.setScale(18, RoundingMode.HALF_DOWN),
                amountOutMin,
                rate,
                fees,
                Instant.now().plusSeconds(props.quoteTtlSeconds()),
                Collections.unmodifiableMap(meta)
        );
    }

    @Override
    public PreparedSwap prepare(SwapQuote quote, String fromAddress, String toAddress) {
        SimpleSwapCurrency from = currencyMap.currency(quote.pair().from());
        SimpleSwapCurrency to = currencyMap.currency(quote.pair().to());

        SimpleSwapExchangeDto exchange = client.createExchange(
                from, to, quote.amountIn(), toAddress, fromAddress);

        Map<String, String> meta = new HashMap<>();
        meta.put("addressTo", toAddress);

        return new PreparedSwap(
                ID,
                exchange.id(),
                quote.pair(),
                quote.amountIn(),
                exchange.addressFrom(),
                exchange.extraIdFrom(),
                Collections.unmodifiableMap(meta)
        );
    }

    @Override
    public SubmittedSwap submit(PreparedSwap prepared, WalletKeySource keys) {
        BlockchainService chainService = blockchainFactory.getService(prepared.pair().from());
        String memo = prepared.memo();
        String txHash;
        try {
            if (memo != null && !memo.isBlank()) {
                txHash = chainService.sendWithMemo(
                        keys.privateKey(), prepared.depositAddress(), prepared.amountIn(), memo);
            } else {
                txHash = chainService.send(
                        keys.privateKey(), prepared.depositAddress(), prepared.amountIn());
            }
        } catch (UnsupportedOperationException e) {
            throw new SwapProviderUnavailableException(
                    "SimpleSwap requires a deposit memo that " + prepared.pair().from()
                            + " does not support yet — this pair is temporarily unavailable");
        } catch (InsufficientFundsException e) {
            throw new SwapInsufficientGasException(
                    "Not enough " + prepared.pair().from().getGasNetwork().getSymbol()
                            + " to cover the network fee for this swap");
        } catch (BlockchainRpcException e) {
            throw new SwapProviderUnavailableException("Source chain send failed", e);
        }
        if (txHash == null || txHash.isBlank()) {
            throw new SwapProviderUnavailableException("Source chain returned empty tx hash");
        }
        client.confirmExchange(prepared.providerOrderId(), txHash,
                prepared.depositAddress(), prepared.meta().get("addressTo"));
        return new SubmittedSwap(ID, prepared.providerOrderId(), txHash);
    }

    @Override
    public SwapStatusUpdate track(SubmittedSwap submitted) {
        SimpleSwapExchangeDto exchange = client.getExchange(submitted.providerOrderId()).orElse(null);
        if (exchange == null || exchange.status() == null) {
            return SwapStatusUpdate.noChange();
        }
        String raw = exchange.status().toLowerCase(Locale.ROOT);
        return switch (raw) {
            case "waiting" -> SwapStatusUpdate.noChange();
            case "confirming" -> new SwapStatusUpdate(SwapOrderStatus.SOURCE_CONFIRMED, null, null, null);
            case "exchanging", "sending" -> new SwapStatusUpdate(SwapOrderStatus.DEST_AWAITING, null, null, null);
            case "verifying" -> new SwapStatusUpdate(SwapOrderStatus.DEST_AWAITING, null, null,
                    "Provider verifying");
            case "finished" -> {
                BigDecimal actual = exchange.amountTo() == null ? null : new BigDecimal(exchange.amountTo());
                yield new SwapStatusUpdate(SwapOrderStatus.COMPLETED, exchange.txTo(), actual, null);
            }
            case "expired" -> new SwapStatusUpdate(SwapOrderStatus.EXPIRED, null, null, "Provider expired");
            case "failed" -> new SwapStatusUpdate(SwapOrderStatus.FAILED_PROVIDER, null, null,
                    "SimpleSwap exchange failed");
            case "refunded" -> new SwapStatusUpdate(SwapOrderStatus.REFUNDED, null, null,
                    "Provider refunded source");
            default -> {
                log.warn("Unknown SimpleSwap status: {}", raw);
                yield SwapStatusUpdate.noChange();
            }
        };
    }
}
