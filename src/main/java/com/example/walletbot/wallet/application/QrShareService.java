package com.example.walletbot.wallet.application;

import com.example.walletbot.notification.application.MessageService;
import com.example.walletbot.shared.constant.BlockchainNetwork;
import com.example.walletbot.shared.exception.BadRequestException;
import com.example.walletbot.shared.util.UrlUtils;
import com.example.walletbot.user.domain.UserEntity;
import com.example.walletbot.wallet.domain.InlineMessagePreparer;
import com.example.walletbot.wallet.domain.WalletEntity;
import com.example.walletbot.wallet.infrastructure.QrShareTokenStore;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class QrShareService {

    private final WalletService walletService;
    private final QrShareTokenStore tokenStore;
    private final InlineMessagePreparer inlineMessagePreparer;
    private final MessageService messageService;

    @Value("${app.public-url}")
    private String publicUrl;

    private String buildCaption(String lang, BlockchainNetwork network, String address) {
        String symbol = network.getSymbol();
        String networkLabel = network.getNetworkLabel();
        String header = symbol.equals(networkLabel) ? symbol : symbol + " · " + networkLabel;
        String warning = messageService.get(lang, "wallet.share.only_send", symbol);
        return header + "\n\n" + address + "\n\n" + warning;
    }

    public String prepareShare(UserEntity user, BlockchainNetwork network) {
        WalletEntity wallet = walletService.getWalletForNetworkOrThrow(user, network);
        String token = tokenStore.store(wallet.getAddress());
        String photoUrl = UrlUtils.stripTrailingSlash(publicUrl) + "/api/files/qr/" + token;
        String caption = buildCaption(user.getLang(), network, wallet.getAddress());
        String preparedMessageId = inlineMessagePreparer.preparePhotoMessage(
                user.getTelegramId(), photoUrl, caption);
        if (preparedMessageId == null) {
            throw new BadRequestException("error.share_prepare_failed");
        }
        return preparedMessageId;
    }
}
