package com.example.walletbot.swap.api;

import com.example.walletbot.platform.web.security.CurrentUser;
import com.example.walletbot.platform.web.validation.NetworkParser;
import com.example.walletbot.shared.constant.BlockchainNetwork;
import com.example.walletbot.swap.api.dto.*;
import com.example.walletbot.swap.api.mapper.SwapApiMapper;
import com.example.walletbot.swap.application.SwapExecutionService;
import com.example.walletbot.swap.application.SwapQuoteService;
import com.example.walletbot.swap.application.SwapRangeService;
import com.example.walletbot.swap.application.SwapStatusService;
import com.example.walletbot.swap.domain.SwapOrderEntity;
import com.example.walletbot.user.domain.UserEntity;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/miniapp")
@RequiredArgsConstructor
public class SwapController {

    private final SwapQuoteService quoteService;
    private final SwapExecutionService executionService;
    private final SwapStatusService statusService;
    private final SwapRangeService rangeService;
    private final SwapApiMapper mapper;
    private final NetworkParser networkParser;

    @GetMapping("/swap/range")
    public SwapRangeResponse range(@RequestParam("from") String from,
                                   @RequestParam("to") String to) {
        BlockchainNetwork fromNetwork = networkParser.parseForSwap(from);
        BlockchainNetwork toNetwork = networkParser.parseForSwap(to);
        return mapper.toRangeResponse(fromNetwork, toNetwork, rangeService.range(fromNetwork, toNetwork));
    }

    @PostMapping("/swap/quote")
    public SwapQuoteResponse quote(@CurrentUser UserEntity user,
                                   @Valid @RequestBody SwapQuoteRequest request) {
        BlockchainNetwork from = networkParser.parseForSwap(request.fromNetwork());
        BlockchainNetwork to = networkParser.parseForSwap(request.toNetwork());
        SwapQuoteService.QuotedSwap quoted = quoteService.quote(user, from, to, request.amount());
        return mapper.toQuoteResponse(quoted);
    }

    @GetMapping("/swap/source-fee")
    public SwapSourceFeeResponse sourceFee(@RequestParam("network") String network) {
        BlockchainNetwork from = networkParser.parseForSwap(network);
        SwapQuoteService.SourceFee fee = quoteService.sourceFee(from);
        return new SwapSourceFeeResponse(fee.networkFee(), fee.reserve());
    }

    @PostMapping("/swap/execute")
    public SwapExecuteResponse execute(@CurrentUser UserEntity user,
                                       @Valid @RequestBody SwapExecuteRequest request,
                                       @RequestHeader(value = "Idempotency-Key", required = false) String idemKey) {
        SwapOrderEntity order = executionService.execute(user, request.intentToken(), idemKey);
        return mapper.toExecuteResponse(order);
    }

    @GetMapping("/swap/orders/{id}")
    public SwapOrderStatusResponse getOrder(@CurrentUser UserEntity user,
                                            @PathVariable("id") long id) {
        return mapper.toStatusResponse(statusService.get(user, id));
    }
}
