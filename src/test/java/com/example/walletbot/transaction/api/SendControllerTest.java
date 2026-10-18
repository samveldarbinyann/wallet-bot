package com.example.walletbot.transaction.api;

import com.example.walletbot.asset.application.PriceService;
import com.example.walletbot.platform.web.validation.NetworkParser;
import com.example.walletbot.shared.constant.BlockchainNetwork;
import com.example.walletbot.testsupport.MiniAppMvc;
import com.example.walletbot.transaction.application.RecipientLookupRateLimiter;
import com.example.walletbot.transaction.application.RecipientResolver;
import com.example.walletbot.transaction.application.SendIntentService;
import com.example.walletbot.transaction.application.SendTransactionService;
import com.example.walletbot.transaction.domain.TransferValidator;
import com.example.walletbot.user.domain.UserEntity;
import com.example.walletbot.wallet.application.WalletService;
import com.example.walletbot.wallet.domain.WalletEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.concurrent.Executor;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.http.MediaType.IMAGE_JPEG;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class SendControllerTest {

    private final UserEntity user = new UserEntity();
    private SendTransactionService sendTransactionService;
    private TransferValidator transferValidator;
    private SendIntentService sendIntentService;
    private WalletService walletService;
    private PriceService priceService;
    private RecipientResolver recipientResolver;
    private RecipientLookupRateLimiter recipientLookupRateLimiter;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        user.setId(1L);
        sendTransactionService = mock(SendTransactionService.class);
        transferValidator = mock(TransferValidator.class);
        sendIntentService = mock(SendIntentService.class);
        walletService = mock(WalletService.class);
        priceService = mock(PriceService.class);
        recipientResolver = mock(RecipientResolver.class);
        recipientLookupRateLimiter = mock(RecipientLookupRateLimiter.class);
        when(recipientLookupRateLimiter.isAllowed(any())).thenReturn(true);
        when(recipientLookupRateLimiter.isAllowedForEstimate(any(), any())).thenReturn(true);
        Executor ioExecutor = Runnable::run;
        SendController controller = new SendController(sendTransactionService, new NetworkParser(),
                transferValidator, sendIntentService, walletService, priceService,
                recipientResolver, recipientLookupRateLimiter, ioExecutor);
        mvc = MiniAppMvc.forController(controller, user);
    }

    private WalletEntity wallet() {
        WalletEntity w = new WalletEntity();
        w.setId(50L);
        w.setAddress("0xFrom");
        return w;
    }

    private SendTransactionService.EstimateResult estimate() {
        return new SendTransactionService.EstimateResult(
                null, new BigDecimal("9.5"), true, null, new BigDecimal("1.0"));
    }

    @Test
    void estimate_validRequest_returnsIntentToken() throws Exception {
        when(transferValidator.validate(eq(user), eq(BlockchainNetwork.ETH), any(), any()))
                .thenReturn(new TransferValidator.Target(wallet(), "0xCanon"));
        when(sendTransactionService.buildEstimate(eq(user), any(), eq(BlockchainNetwork.ETH), any(), eq(false)))
                .thenReturn(estimate());
        when(sendIntentService.create(eq(1L), eq(50L), eq(BlockchainNetwork.ETH), eq("0xCanon"), any(), eq(false)))
                .thenReturn("INTENT");

        mvc.perform(post("/api/miniapp/send/estimate")
                        .contentType(APPLICATION_JSON)
                        .content("{\"network\":\"ETH\",\"address\":\"0x1234567890abcdef\",\"amount\":\"1.0\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.intentToken").value("INTENT"))
                .andExpect(jsonPath("$.networkSymbol").value("ETH"));
    }

    @Test
    void estimate_nonPositiveAmount_returns422() throws Exception {
        mvc.perform(post("/api/miniapp/send/estimate")
                        .contentType(APPLICATION_JSON)
                        .content("{\"network\":\"ETH\",\"address\":\"0x1234567890abcdef\",\"amount\":\"0\"}"))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void lookupRecipient_found_returnsRecipient() throws Exception {
        when(recipientResolver.lookup(eq("bobby"), eq(user)))
                .thenReturn(new RecipientResolver.RecipientLookup(true, "bobby", "Bob Jones", false));

        mvc.perform(get("/api/miniapp/send/recipient").param("username", "bobby"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.found").value(true))
                .andExpect(jsonPath("$.username").value("bobby"))
                .andExpect(jsonPath("$.displayName").value("Bob Jones"))
                .andExpect(jsonPath("$.self").value(false));
    }

    @Test
    void lookupRecipient_rateLimited_returns429() throws Exception {
        when(recipientLookupRateLimiter.isAllowed(1L)).thenReturn(false);

        mvc.perform(get("/api/miniapp/send/recipient").param("username", "bobby"))
                .andExpect(status().isTooManyRequests());
        verifyNoInteractions(recipientResolver);
    }

    @Test
    void recipientPhoto_found_returnsJpeg() throws Exception {
        byte[] photo = {1, 2, 3};
        when(recipientResolver.fetchPhoto("bobby")).thenReturn(Optional.of(photo));

        MvcResult mvcResult = mvc.perform(get("/api/miniapp/send/recipient/photo").param("username", "bobby"))
                .andExpect(request().asyncStarted())
                .andReturn();

        mvc.perform(asyncDispatch(mvcResult))
                .andExpect(status().isOk())
                .andExpect(content().contentType(IMAGE_JPEG));
    }

    @Test
    void recipientPhoto_notFound_returns404() throws Exception {
        when(recipientResolver.fetchPhoto("ghost")).thenReturn(Optional.empty());

        MvcResult mvcResult = mvc.perform(get("/api/miniapp/send/recipient/photo").param("username", "ghost"))
                .andExpect(request().asyncStarted())
                .andReturn();

        mvc.perform(asyncDispatch(mvcResult))
                .andExpect(status().isNotFound());
    }

    @Test
    void recipientPhoto_rateLimited_returns429() throws Exception {
        when(recipientLookupRateLimiter.isAllowedForEstimate(1L, "bobby")).thenReturn(false);

        mvc.perform(get("/api/miniapp/send/recipient/photo").param("username", "bobby"))
                .andExpect(status().isTooManyRequests());
        verifyNoInteractions(recipientResolver);
    }

    @Test
    void estimateByUsername_resolvesRecipientAddress_returnsIntentToken() throws Exception {
        UserEntity recipient = new UserEntity();
        recipient.setId(2L);
        WalletEntity recipientWallet = new WalletEntity();
        recipientWallet.setId(60L);
        recipientWallet.setAddress("0xRecipient");
        when(recipientResolver.resolve("bobby")).thenReturn(recipient);
        when(walletService.getWalletForNetworkOrThrow(recipient, BlockchainNetwork.ETH)).thenReturn(recipientWallet);
        when(transferValidator.validate(eq(user), eq(BlockchainNetwork.ETH), eq("0xRecipient"), any()))
                .thenReturn(new TransferValidator.Target(wallet(), "0xCanon"));
        when(sendTransactionService.buildEstimate(eq(user), any(), eq(BlockchainNetwork.ETH), any(), eq(false)))
                .thenReturn(estimate());
        when(sendIntentService.create(eq(1L), eq(50L), eq(BlockchainNetwork.ETH), eq("0xCanon"), any(), eq(false)))
                .thenReturn("INTENT");

        mvc.perform(post("/api/miniapp/send/estimate/username")
                        .contentType(APPLICATION_JSON)
                        .content("{\"network\":\"ETH\",\"username\":\"bobby\",\"amount\":\"1.0\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.intentToken").value("INTENT"))
                .andExpect(jsonPath("$.networkSymbol").value("ETH"));
    }

    @Test
    void estimateByUsername_rateLimited_returns429() throws Exception {
        when(recipientLookupRateLimiter.isAllowedForEstimate(1L, "bobby")).thenReturn(false);

        mvc.perform(post("/api/miniapp/send/estimate/username")
                        .contentType(APPLICATION_JSON)
                        .content("{\"network\":\"ETH\",\"username\":\"bobby\",\"amount\":\"1.0\"}"))
                .andExpect(status().isTooManyRequests());
        verifyNoInteractions(recipientResolver);
    }

    @Test
    void feePreview_noWallet_returns404() throws Exception {
        when(walletService.getWalletForNetwork(user, BlockchainNetwork.ETH)).thenReturn(null);

        mvc.perform(post("/api/miniapp/send/fee-preview")
                        .contentType(APPLICATION_JSON).content("{\"network\":\"ETH\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void feePreview_wallet_returnsPreview() throws Exception {
        when(walletService.getWalletForNetwork(user, BlockchainNetwork.ETH)).thenReturn(wallet());
        when(sendTransactionService.buildEstimate(eq(user), any(), eq(BlockchainNetwork.ETH), eq(null), eq(false)))
                .thenReturn(estimate());
        when(priceService.usdValue(any(), any())).thenReturn(new BigDecimal("3"));

        mvc.perform(post("/api/miniapp/send/fee-preview")
                        .contentType(APPLICATION_JSON).content("{\"network\":\"ETH\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hasEnoughGas").value(true));
    }

    @Test
    void send_withIdempotencyKey_executesAndReturnsTxHash() throws Exception {
        SendIntentService.ResolvedTransfer transfer = new SendIntentService.ResolvedTransfer(
                "jti-1", BlockchainNetwork.ETH, wallet(), "0xCanon", new BigDecimal("1"), false);
        when(sendIntentService.resolve(eq(user), eq("INTENT"))).thenReturn(transfer);
        when(sendTransactionService.executeWithIdempotency(eq(user), eq(transfer), eq("key-1")))
                .thenReturn(SendTransactionService.SendResult.broadcast(99L, "0xhash"));

        mvc.perform(post("/api/miniapp/send")
                        .header("Idempotency-Key", "key-1")
                        .contentType(APPLICATION_JSON).content("{\"intentToken\":\"INTENT\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.txHash").value("0xhash"))
                .andExpect(jsonPath("$.txId").value(99));

        verify(sendTransactionService).executeWithIdempotency(user, transfer, "key-1");
    }

    @Test
    void send_noHeader_passesNullIdemKeyToService() throws Exception {
        SendIntentService.ResolvedTransfer transfer = new SendIntentService.ResolvedTransfer(
                "jti-1", BlockchainNetwork.ETH, wallet(), "0xCanon", new BigDecimal("1"), false);
        when(sendIntentService.resolve(eq(user), eq("INTENT"))).thenReturn(transfer);
        when(sendTransactionService.executeWithIdempotency(eq(user), eq(transfer), eq(null)))
                .thenReturn(SendTransactionService.SendResult.broadcast(99L, "0xhash"));

        mvc.perform(post("/api/miniapp/send")
                        .contentType(APPLICATION_JSON).content("{\"intentToken\":\"INTENT\"}"))
                .andExpect(status().isOk());

        verify(sendTransactionService).executeWithIdempotency(user, transfer, null);
    }
}
