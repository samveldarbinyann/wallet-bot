package com.example.walletbot.wallet.application;

import com.example.walletbot.notification.application.MessageService;
import com.example.walletbot.platform.blockchain.BlockchainService;
import com.example.walletbot.platform.blockchain.BlockchainServiceFactory;
import com.example.walletbot.shared.constant.BlockchainNetwork;
import com.example.walletbot.shared.event.WalletCreatedEvent;
import com.example.walletbot.shared.exception.BadRequestException;
import com.example.walletbot.user.domain.UserEntity;
import com.example.walletbot.wallet.domain.WalletEntity;
import com.example.walletbot.wallet.domain.WalletRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class WalletService {
    private final WalletRepository walletRepository;
    private final BlockchainServiceFactory blockchainFactory;
    private final MessageService messageService;
    private final ApplicationEventPublisher eventPublisher;

    private static final List<BlockchainNetwork> BASE_NETWORKS = Arrays.stream(BlockchainNetwork.values())
            .filter(BlockchainNetwork::isBaseNetwork)
            .filter(n -> n != BlockchainNetwork.BNB)
            .toList();

    @Transactional
    public void createAllWallets(UserEntity userEntity) {
        Set<BlockchainNetwork> existing = walletRepository.findByUserEntityId(userEntity.getId()).stream()
                .map(WalletEntity::getNetwork)
                .collect(Collectors.toSet());
        for (BlockchainNetwork network : BASE_NETWORKS) {
            if (existing.contains(network)) {
                continue;
            }
            try {
                BlockchainService service = blockchainFactory.getService(network);
                BlockchainService.WalletKeys keys = service.generateWallet();
                WalletEntity wallet = new WalletEntity();
                wallet.setAddress(keys.address());
                wallet.setPrivateKey(keys.privateKey());
                wallet.setNetwork(network);
                wallet.setUserEntity(userEntity);
                walletRepository.save(wallet);
                eventPublisher.publishEvent(
                        new WalletCreatedEvent(userEntity.getId(), network, keys.address()));
            } catch (RuntimeException e) {
                log.warn("Failed to create {} wallet for user {}: {}",
                        network, userEntity.getTelegramId(), e.getMessage());
            }
        }
    }

    @Transactional(readOnly = true)
    public boolean needsWalletsForUser(Long userId) {
        return !walletRepository.existsByUserEntityId(userId);
    }

    public Map<BlockchainNetwork, WalletEntity> groupByNetwork(List<WalletEntity> wallets) {
        Map<BlockchainNetwork, WalletEntity> byNetwork = wallets.stream()
                .collect(Collectors.toMap(WalletEntity::getNetwork, w -> w, (a, b) -> a));
        WalletEntity ethWallet = byNetwork.get(BlockchainNetwork.ETH);
        if (ethWallet != null) {
            byNetwork.put(BlockchainNetwork.BNB, ethWallet);
        }
        return byNetwork;
    }

    @Transactional(readOnly = true)
    public WalletEntity getWalletForNetwork(UserEntity userEntity, BlockchainNetwork network) {
        if (network == null)
            return null;
        List<WalletEntity> wallets = walletRepository.findByUserEntityId(userEntity.getId());
        return findWalletForNetwork(wallets, network);
    }

    @Transactional(readOnly = true)
    public WalletEntity getWalletForNetworkOrThrow(UserEntity userEntity, BlockchainNetwork network) {
        WalletEntity wallet = getWalletForNetwork(userEntity, network);
        if (wallet == null) {
            throw new BadRequestException("error.no_wallet_for_network", network.name());
        }
        return wallet;
    }

    private WalletEntity findWalletForNetwork(List<WalletEntity> wallets, BlockchainNetwork network) {
        if (network == null)
            return null;
        WalletEntity direct = wallets.stream()
                .filter(w -> w.getNetwork() == network)
                .findFirst().orElse(null);
        if (direct != null)
            return direct;
        if (network == BlockchainNetwork.BNB) {
            return wallets.stream()
                    .filter(w -> w.getNetwork() == BlockchainNetwork.ETH)
                    .findFirst().orElse(null);
        }
        BlockchainNetwork parent = network.getParentNetwork();
        if (parent != null) {
            return findWalletForNetwork(wallets, parent);
        }
        return null;
    }

    public AddressValidation validateAddress(BlockchainNetwork net, String address, String lang) {
        boolean valid = blockchainFactory.getService(net).isValidAddress(address.trim());
        String hint = null;
        if (!valid) {
            String hintKey = net.getAddressHintKey();
            if (hintKey != null) {
                hint = messageService.get(lang, hintKey);
            }
        }
        return new AddressValidation(valid, hint);
    }

    public record AddressValidation(boolean valid, String hint) {
    }
}