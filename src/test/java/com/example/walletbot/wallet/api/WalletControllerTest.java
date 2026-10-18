package com.example.walletbot.wallet.api;

import com.example.walletbot.platform.web.validation.NetworkParser;
import com.example.walletbot.shared.constant.BlockchainNetwork;
import com.example.walletbot.shared.util.QrCodeService;
import com.example.walletbot.testsupport.MiniAppMvc;
import com.example.walletbot.user.domain.UserEntity;
import com.example.walletbot.wallet.application.QrShareService;
import com.example.walletbot.wallet.application.WalletService;
import com.example.walletbot.wallet.domain.WalletEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class WalletControllerTest {

    private final UserEntity user = new UserEntity();
    private WalletService walletService;
    private QrCodeService qrCodeService;
    private QrShareService qrShareService;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        user.setId(1L);
        user.setLang("en");
        walletService = mock(WalletService.class);
        qrCodeService = mock(QrCodeService.class);
        qrShareService = mock(QrShareService.class);
        mvc = MiniAppMvc.forController(
                new WalletController(walletService, qrCodeService, qrShareService, new NetworkParser()), user);
    }

    @Test
    void getReceiveAddress_validNetwork_returnsAddressAndQr() throws Exception {
        WalletEntity wallet = new WalletEntity();
        wallet.setAddress("0xABC");
        when(walletService.getWalletForNetwork(user, BlockchainNetwork.ETH)).thenReturn(wallet);
        when(qrCodeService.generateQrDataUrl("0xABC")).thenReturn("data:image/png;base64,xxx");

        mvc.perform(get("/api/miniapp/wallets/ETH/receive"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.address").value("0xABC"))
                .andExpect(jsonPath("$.network").value("ETH"));
    }

    @Test
    void getReceiveAddress_unknownNetwork_returns400() throws Exception {
        mvc.perform(get("/api/miniapp/wallets/NOPE/receive"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getReceiveAddress_noWallet_returns400() throws Exception {
        when(walletService.getWalletForNetwork(user, BlockchainNetwork.ETH)).thenReturn(null);

        mvc.perform(get("/api/miniapp/wallets/ETH/receive"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void validateAddress_returnsValidationResult() throws Exception {
        when(walletService.validateAddress(eq(BlockchainNetwork.ETH), any(), eq("en")))
                .thenReturn(new WalletService.AddressValidation(true, null));

        mvc.perform(post("/api/miniapp/validate/address")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"address\":\"0x1234567890abcdef\",\"network\":\"ETH\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valid").value(true));
    }

    @Test
    void validateAddress_illegalCharacters_returns422() throws Exception {
        mvc.perform(post("/api/miniapp/validate/address")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"address\":\"bad address!!\",\"network\":\"ETH\"}"))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void prepareShareQr_delegatesToShareService() throws Exception {
        when(qrShareService.prepareShare(user, BlockchainNetwork.ETH)).thenReturn("msg-1");

        mvc.perform(post("/api/miniapp/wallets/ETH/share-prepare"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.preparedMessageId").value("msg-1"));
    }
}
