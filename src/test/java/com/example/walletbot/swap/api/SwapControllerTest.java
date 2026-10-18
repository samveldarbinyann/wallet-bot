package com.example.walletbot.swap.api;

import com.example.walletbot.platform.web.validation.NetworkParser;
import com.example.walletbot.shared.constant.BlockchainNetwork;
import com.example.walletbot.swap.api.mapper.SwapApiMapper;
import com.example.walletbot.swap.application.SwapExecutionService;
import com.example.walletbot.swap.application.SwapQuoteService;
import com.example.walletbot.swap.application.SwapRangeService;
import com.example.walletbot.swap.application.SwapStatusService;
import com.example.walletbot.swap.domain.SwapOrderEntity;
import com.example.walletbot.testsupport.MiniAppMvc;
import com.example.walletbot.transaction.domain.NetworkFee;
import com.example.walletbot.user.domain.UserEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class SwapControllerTest {

    private final UserEntity user = new UserEntity();
    private SwapQuoteService quoteService;
    private SwapExecutionService executionService;
    private SwapStatusService statusService;
    private SwapRangeService rangeService;
    private SwapApiMapper mapper;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        user.setId(1L);
        quoteService = mock(SwapQuoteService.class);
        executionService = mock(SwapExecutionService.class);
        statusService = mock(SwapStatusService.class);
        rangeService = mock(SwapRangeService.class);
        mapper = mock(SwapApiMapper.class);
        SwapController controller = new SwapController(
                quoteService, executionService, statusService, rangeService, mapper, new NetworkParser());
        mvc = MiniAppMvc.forController(controller, user);
    }

    @Test
    void range_parsesNetworksAndDelegates() throws Exception {
        mvc.perform(get("/api/miniapp/swap/range").param("from", "BTC").param("to", "ETH"))
                .andExpect(status().isOk());
        verify(rangeService).range(BlockchainNetwork.BTC, BlockchainNetwork.ETH);
        verify(mapper).toRangeResponse(eq(BlockchainNetwork.BTC), eq(BlockchainNetwork.ETH), any());
    }

    @Test
    void quote_parsesNetworksAndDelegates() throws Exception {
        when(quoteService.quote(eq(user), eq(BlockchainNetwork.BTC), eq(BlockchainNetwork.ETH), any()))
                .thenReturn(mock(SwapQuoteService.QuotedSwap.class));

        mvc.perform(post("/api/miniapp/swap/quote")
                        .contentType(APPLICATION_JSON)
                        .content("{\"fromNetwork\":\"BTC\",\"toNetwork\":\"ETH\",\"amount\":\"1.0\"}"))
                .andExpect(status().isOk());

        verify(quoteService).quote(eq(user), eq(BlockchainNetwork.BTC), eq(BlockchainNetwork.ETH), eq(new BigDecimal("1.0")));
    }

    @Test
    void quote_usdtAlias_resolvedForSwap() throws Exception {
        when(quoteService.quote(eq(user), eq(BlockchainNetwork.USDT_TRC20), any(), any()))
                .thenReturn(mock(SwapQuoteService.QuotedSwap.class));

        mvc.perform(post("/api/miniapp/swap/quote")
                        .contentType(APPLICATION_JSON)
                        .content("{\"fromNetwork\":\"USDT\",\"toNetwork\":\"ETH\",\"amount\":\"1.0\"}"))
                .andExpect(status().isOk());

        verify(quoteService).quote(eq(user), eq(BlockchainNetwork.USDT_TRC20), eq(BlockchainNetwork.ETH), any());
    }

    @Test
    void sourceFee_parsesNetworkAndDelegates() throws Exception {
        when(quoteService.sourceFee(BlockchainNetwork.BTC)).thenReturn(
                new SwapQuoteService.SourceFee(
                        new NetworkFee(new BigDecimal("0.00000564"), "BTC", new BigDecimal("0.36")),
                        new BigDecimal("0.00000564")));

        mvc.perform(get("/api/miniapp/swap/source-fee").param("network", "BTC"))
                .andExpect(status().isOk());

        verify(quoteService).sourceFee(BlockchainNetwork.BTC);
    }

    @Test
    void execute_passesIntentAndIdempotencyKey() throws Exception {
        when(executionService.execute(eq(user), eq("INTENT"), eq("key-1")))
                .thenReturn(mock(SwapOrderEntity.class));

        mvc.perform(post("/api/miniapp/swap/execute")
                        .header("Idempotency-Key", "key-1")
                        .contentType(APPLICATION_JSON).content("{\"intentToken\":\"INTENT\"}"))
                .andExpect(status().isOk());

        verify(executionService).execute(user, "INTENT", "key-1");
    }

    @Test
    void getOrder_delegatesToStatusService() throws Exception {
        when(statusService.get(user, 7L)).thenReturn(mock(SwapOrderEntity.class));

        mvc.perform(get("/api/miniapp/swap/orders/7")).andExpect(status().isOk());

        verify(statusService).get(user, 7L);
    }
}
