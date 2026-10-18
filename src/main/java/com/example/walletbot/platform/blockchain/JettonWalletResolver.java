package com.example.walletbot.platform.blockchain;

import com.example.walletbot.platform.blockchain.cache.JettonWalletAddressCache;
import com.example.walletbot.platform.blockchain.client.TonCenterClient;
import com.example.walletbot.platform.blockchain.config.BlockchainProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.ton.java.address.Address;
import org.ton.java.cell.Cell;
import org.ton.java.cell.CellBuilder;
import org.ton.java.cell.CellSlice;

@Slf4j
@Component
public class JettonWalletResolver {

    private final TonCenterClient tonCenter;
    private final JettonWalletAddressCache cache;
    private final String jettonMaster;

    public JettonWalletResolver(TonCenterClient tonCenter, JettonWalletAddressCache cache,
                                BlockchainProperties blockchain) {
        this.tonCenter = tonCenter;
        this.cache = cache;
        this.jettonMaster = blockchain.token().usdtTonJettonMaster();
    }

    public String resolve(String ownerAddress) {
        return cache.resolve(ownerAddress, this::fetch);
    }

    public void invalidate(String ownerAddress) {
        cache.invalidate(ownerAddress);
    }

    private String fetch(String ownerAddress) {
        try {
            String ownerBocBase64 = CellBuilder.beginCell()
                    .storeAddress(Address.of(ownerAddress))
                    .endCell()
                    .toBase64();

            String cellBase64 = tonCenter.runGetMethodForJettonWalletAddress(jettonMaster, ownerBocBase64);
            if (cellBase64 == null) return null;

            Cell cell = CellBuilder.beginCell().fromBocBase64(cellBase64).endCell();
            return CellSlice.beginParse(cell).loadAddress().toNonBounceable();
        } catch (Exception e) {
            log.warn("Failed to resolve jetton wallet address for {}: {}", ownerAddress, e.getMessage());
            return null;
        }
    }
}