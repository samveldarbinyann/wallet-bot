package com.example.walletbot.wallet.api;

import com.example.walletbot.platform.web.security.CurrentUser;
import com.example.walletbot.platform.web.validation.NetworkParser;
import com.example.walletbot.shared.constant.BlockchainNetwork;
import com.example.walletbot.shared.util.QrCodeService;
import com.example.walletbot.user.domain.UserEntity;
import com.example.walletbot.wallet.api.dto.ReceiveResponse;
import com.example.walletbot.wallet.api.dto.ShareQrResponse;
import com.example.walletbot.wallet.api.dto.ValidateAddressRequest;
import com.example.walletbot.wallet.api.dto.ValidateAddressResponse;
import com.example.walletbot.wallet.application.QrShareService;
import com.example.walletbot.wallet.application.WalletService;
import com.example.walletbot.wallet.domain.WalletEntity;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/miniapp")
@RequiredArgsConstructor
public class WalletController {

    private final WalletService walletService;
    private final QrCodeService qrCodeService;
    private final QrShareService qrShareService;
    private final NetworkParser networkParser;

    @GetMapping("/wallets/{network}/receive")
    public ReceiveResponse getReceiveAddress(@CurrentUser UserEntity user,
                                             @PathVariable String network) {
        BlockchainNetwork net = networkParser.parseStrict(network);
        WalletEntity wallet = walletService.getWalletForNetworkOrThrow(user, net);
        return new ReceiveResponse(wallet.getAddress(), net.name(), qrCodeService.generateQrDataUrl(wallet.getAddress()));
    }

    @PostMapping("/wallets/{network}/share-prepare")
    public ShareQrResponse prepareShareQr(@CurrentUser UserEntity user,
                                          @PathVariable String network) {
        BlockchainNetwork net = networkParser.parseStrict(network);
        return new ShareQrResponse(qrShareService.prepareShare(user, net));
    }

    @PostMapping("/validate/address")
    public ValidateAddressResponse validateAddress(@CurrentUser UserEntity user,
                                                   @Valid @RequestBody ValidateAddressRequest request) {
        BlockchainNetwork net = networkParser.parseStrict(request.network());
        WalletService.AddressValidation result = walletService.validateAddress(net, request.address(), user.getLang());
        return new ValidateAddressResponse(result.valid(), result.hint());
    }
}