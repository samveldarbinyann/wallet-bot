package com.example.walletbot.swap.api.mapper;

import com.example.walletbot.shared.constant.BlockchainNetwork;
import com.example.walletbot.swap.api.dto.*;
import com.example.walletbot.swap.application.SwapQuoteService;
import com.example.walletbot.swap.application.intent.SwapIntentService;
import com.example.walletbot.swap.domain.SwapOrderEntity;
import com.example.walletbot.swap.domain.SwapQuote;
import com.example.walletbot.swap.domain.SwapRanges;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component
public class SwapApiMapper {

    public SwapQuoteResponse toQuoteResponse(SwapQuoteService.QuotedSwap quoted) {
        SwapQuote quote = quoted.quote();
        SwapIntentService.IntentToken intent = quoted.intent();
        BigDecimal minAmount = quote.providerMeta().containsKey("minAmount")
                ? new BigDecimal(quote.providerMeta().get("minAmount"))
                : BigDecimal.ZERO;
        return new SwapQuoteResponse(
                intent.jti(),
                intent.token(),
                new SwapRouteDto(quote.providerId(),
                        quote.pair().from().name(),
                        quote.pair().to().name()),
                quote.amountIn(),
                quote.amountOut(),
                quote.amountOutMin(),
                quote.rate(),
                new SwapFeeBreakdownDto(
                        quote.fees().providerFeePercent(),
                        quote.fees().providerFeeAmount(),
                        quoted.networkFee()),
                quoted.amountOutUsd(),
                quoted.totalCostUsd(),
                minAmount,
                quote.pair().from().getSymbol(),
                quote.pair().to().getSymbol(),
                intent.validUntil()
        );
    }

    public SwapExecuteResponse toExecuteResponse(SwapOrderEntity order) {
        return new SwapExecuteResponse(
                order.getId(),
                order.getStatus(),
                order.getProvider(),
                order.getFromNetwork().getSymbol(),
                order.getToNetwork().getSymbol(),
                order.getAmountIn(),
                order.getAmountOutExpected(),
                order.getSourceTxHash(),
                order.getDepositAddress()
        );
    }

    public SwapRangeResponse toRangeResponse(BlockchainNetwork from, BlockchainNetwork to,
                                             SwapRanges ranges) {
        return new SwapRangeResponse(from.name(), to.name(), ranges.min(), ranges.max());
    }

    public SwapOrderStatusResponse toStatusResponse(SwapOrderEntity order) {
        return new SwapOrderStatusResponse(
                order.getId(),
                order.getStatus(),
                order.getStatusReason(),
                order.getProvider(),
                order.getFromNetwork().name(),
                order.getToNetwork().name(),
                order.getFromNetwork().getSymbol(),
                order.getToNetwork().getSymbol(),
                order.getAmountIn(),
                order.getAmountOutExpected(),
                order.getAmountOutActual(),
                order.getSourceTxHash(),
                order.getDestTxHash(),
                order.getDepositAddress(),
                order.getCreatedAt(),
                order.getUpdatedAt()
        );
    }
}
