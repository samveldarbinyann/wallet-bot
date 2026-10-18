package com.example.walletbot.asset.application;

import com.example.walletbot.asset.domain.AssetItemDto;
import com.example.walletbot.asset.domain.OutgoingReservationPort;
import com.example.walletbot.platform.blockchain.BlockchainServiceFactory;
import com.example.walletbot.shared.constant.BlockchainNetwork;
import com.example.walletbot.user.domain.UserEntity;
import com.example.walletbot.wallet.application.WalletNetworkBalanceService;
import com.example.walletbot.wallet.application.WalletService;
import com.example.walletbot.wallet.domain.WalletEntity;
import com.example.walletbot.wallet.domain.WalletRepository;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicLong;

@Slf4j
@Service
public class AssetService {

    private final BlockchainServiceFactory blockchainFactory;
    private final PriceService priceService;
    private final WalletRepository walletRepository;
    private final WalletNetworkBalanceService walletNetworkBalanceService;
    private final WalletService walletService;
    private final ExecutorService balanceExecutor;
    private final OutgoingReservationPort reservationPort;

    public AssetService(BlockchainServiceFactory blockchainFactory,
                        PriceService priceService,
                        WalletRepository walletRepository,
                        WalletNetworkBalanceService walletNetworkBalanceService,
                        WalletService walletService,
                        @Qualifier("balanceExecutor") ExecutorService balanceExecutor,
                        OutgoingReservationPort reservationPort) {
        this.blockchainFactory = blockchainFactory;
        this.priceService = priceService;
        this.walletRepository = walletRepository;
        this.walletNetworkBalanceService = walletNetworkBalanceService;
        this.walletService = walletService;
        this.balanceExecutor = balanceExecutor;
        this.reservationPort = reservationPort;
    }

    private static final long BALANCE_CACHE_TTL_MS = 15_000;
    private static final long DB_BALANCE_TTL_MS = 60_000;
    private static final long PORTFOLIO_ASSET_CACHE_TTL_MS = 25_000;

    private final Cache<String, String> balanceCache = Caffeine.newBuilder()
            .maximumSize(200_000)
            .expireAfterWrite(Duration.ofMillis(BALANCE_CACHE_TTL_MS))
            .build();

    private final Cache<Long, Map<BlockchainNetwork, BigDecimal>> portfolioAssetCache = Caffeine.newBuilder()
            .maximumSize(50_000)
            .expireAfterWrite(Duration.ofMillis(PORTFOLIO_ASSET_CACHE_TTL_MS))
            .build();

    private final Cache<Long, AtomicLong> balanceEpoch = Caffeine.newBuilder()
            .maximumSize(50_000)
            .expireAfterAccess(Duration.ofMinutes(30))
            .build();

    private AtomicLong epochCounter(Long userId) {
        return balanceEpoch.get(userId, u -> new AtomicLong(0));
    }

    private long getEpoch(Long userId) {
        if (userId == null)
            return 0L;
        return epochCounter(userId).get();
    }

    private static final List<BlockchainNetwork> SUPPORTED_NETWORKS = List.of(
            BlockchainNetwork.BTC, BlockchainNetwork.ETH, BlockchainNetwork.BNB,
            BlockchainNetwork.SOL, BlockchainNetwork.TON, BlockchainNetwork.TRX,
            BlockchainNetwork.USDT_ERC20, BlockchainNetwork.USDT_TRC20, BlockchainNetwork.USDT_TON,
            BlockchainNetwork.USDT_SOL, BlockchainNetwork.USDT_BEP20,
            BlockchainNetwork.USDC_ERC20, BlockchainNetwork.USDC_SOL, BlockchainNetwork.USDC_BEP20
    );

    private record BalanceTask(long userId, long walletId, String address, BlockchainNetwork network) {
    }

    private record BalanceResult(BlockchainNetwork network, BigDecimal amount) {
    }

    @Transactional(readOnly = true)
    public Map<BlockchainNetwork, BigDecimal> fetchNetworkBalances(UserEntity userEntity) {
        long uid = userEntity.getId();
        return fetchNetworkBalances(uid, walletRepository.findByUserEntityId(uid));
    }

    private Map<BlockchainNetwork, BigDecimal> fetchNetworkBalances(long uid, List<WalletEntity> wallets) {
        Map<BlockchainNetwork, BigDecimal> cached = portfolioAssetCache.getIfPresent(uid);
        if (cached != null) {
            return new HashMap<>(cached);
        }
        return computeAndCacheNetworkBalances(uid, wallets);
    }

    @Async("ioExecutor")
    public void warmUserBalances(Long userId) {
        if (userId == null) return;
        try {
            List<WalletEntity> wallets = walletRepository.findByUserEntityId(userId);
            if (!wallets.isEmpty()) {
                computeAndCacheNetworkBalances(userId, wallets);
            }
        } catch (Exception e) {
            log.debug("Balance warm failed for user {}: {}", userId, e.getMessage());
        }
    }

    private Map<BlockchainNetwork, BigDecimal> computeAndCacheNetworkBalances(
            long uid, List<WalletEntity> wallets) {
        long e0 = getEpoch(uid);
        List<BalanceTask> tasks = new ArrayList<>();
        for (WalletEntity w : wallets) {
            addBalanceTasks(tasks, uid, w, w.getNetwork());
            if (w.getNetwork() == BlockchainNetwork.ETH) {
                addBalanceTasks(tasks, uid, w, BlockchainNetwork.BNB);
            }
        }

        List<CompletableFuture<BalanceResult>> futures = tasks.stream()
                .map(t -> CompletableFuture.supplyAsync(() -> {
                    try {
                        String bal = getBalance(t.userId(), t.walletId(), t.address(), t.network());
                        return new BalanceResult(t.network(), new BigDecimal(bal));
                    } catch (Exception e) {
                        log.debug("Balance fetch {}: {}", t.network(), e.getMessage());
                        return new BalanceResult(t.network(), BigDecimal.ZERO);
                    }
                }, balanceExecutor))
                .toList();
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

        Map<BlockchainNetwork, BigDecimal> networkBalances = new HashMap<>();
        for (CompletableFuture<BalanceResult> f : futures) {
            BalanceResult r = f.join();
            networkBalances.merge(r.network(),
                    r.amount().setScale(8, RoundingMode.DOWN), BigDecimal::add);
        }

        if (getEpoch(uid) == e0) {
            portfolioAssetCache.put(uid, new HashMap<>(networkBalances));
        }
        return networkBalances;
    }

    private void addBalanceTasks(List<BalanceTask> tasks, long uid, WalletEntity w, BlockchainNetwork network) {
        tasks.add(new BalanceTask(uid, w.getId(), w.getAddress(), network));
        for (BlockchainNetwork tokenNet : tokenNetworksFor(network)) {
            tasks.add(new BalanceTask(uid, w.getId(), w.getAddress(), tokenNet));
        }
    }

    @Transactional
    public void invalidateUserBalances(Long userId) {
        if (userId == null)
            return;
        List<WalletEntity> userWallets = walletRepository.findByUserEntityId(userId);
        List<Long> walletIds = userWallets.stream().map(WalletEntity::getId).toList();

        walletNetworkBalanceService.deleteAllForWalletIds(walletIds);

        portfolioAssetCache.invalidate(userId);
        epochCounter(userId).incrementAndGet();
        for (WalletEntity w : userWallets) {
            String prefix = w.getId() + ":";
            balanceCache.asMap().keySet().removeIf(key -> key.startsWith(prefix));
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void creditBalance(Long userId, Long walletId, BlockchainNetwork network, BigDecimal amount) {
        if (userId == null || walletId == null || network == null
                || amount == null || amount.signum() <= 0) {
            return;
        }
        String cacheKey = walletId + ":" + network.name();
        balanceCache.invalidate(cacheKey);
        walletNetworkBalanceService.deleteByWalletIdAndNetwork(walletId, network);
        epochCounter(userId).incrementAndGet();
        portfolioAssetCache.invalidate(userId);
    }

    public String getBalance(Long userId, Long walletId, String address, BlockchainNetwork network) {
        if (walletId == null || address == null || network == null) {
            try {
                return fetchBalance(address, network);
            } catch (Exception e) {
                log.warn("Balance fetch failed for {} {}: {}", network, address, e.getMessage());
                return "0";
            }
        }
        String cacheKey = walletId + ":" + network.name();

        String cached = balanceCache.getIfPresent(cacheKey);
        if (cached != null) {
            return cached;
        }
        Optional<String> dbBalance = walletNetworkBalanceService.findFreshBalance(walletId, network,
                DB_BALANCE_TTL_MS);
        if (dbBalance.isPresent()) {
            String value = dbBalance.get();
            balanceCache.put(cacheKey, value);
            return value;
        }

        long e0 = getEpoch(userId);
        String balance;
        try {
            balance = fetchBalance(address, network);
        } catch (Exception e) {
            log.warn("Balance fetch failed for {} wallet {}: {} — serving last known value",
                    network, walletId, e.getMessage());
            return walletNetworkBalanceService.findLastKnownBalance(walletId, network).orElse("0");
        }
        walletNetworkBalanceService.upsertBalance(walletId, network, balance);

        if (getEpoch(userId) == e0) {
            balanceCache.put(cacheKey, balance);
        }
        return balance;
    }

    private String fetchBalance(String address, BlockchainNetwork network) {
        return blockchainFactory.getService(network).getBalance(address);
    }

    public String getFreshBalance(String address, BlockchainNetwork network) {
        return fetchBalance(address, network);
    }

    private List<BlockchainNetwork> tokenNetworksFor(BlockchainNetwork baseNetwork) {
        if (baseNetwork == null || !baseNetwork.isBaseNetwork())
            return List.of();
        return Arrays.stream(BlockchainNetwork.values())
                .filter(n -> n.isToken() && n.getParentNetwork() == baseNetwork)
                .toList();
    }

    @Transactional
    public PortfolioSummary buildPortfolio(UserEntity user) {
        List<WalletEntity> wallets = walletRepository.findByUserEntityId(user.getId());
        Map<BlockchainNetwork, BigDecimal> networkBalances = fetchNetworkBalances(user.getId(), wallets);
        Map<String, PriceService.CoinPrice> prices = priceService.getPrices();
        Map<BlockchainNetwork, WalletEntity> walletByNetwork = walletService.groupByNetwork(wallets);

        Set<Long> walletIds = new HashSet<>();
        for (WalletEntity w : walletByNetwork.values()) {
            walletIds.add(w.getId());
        }
        Map<Long, Map<BlockchainNetwork, BigDecimal>> reservedByWallet =
                reservationPort.reservedOutgoingBatch(walletIds);

        List<AssetItemDto> items = new ArrayList<>();
        BigDecimal totalUsd = BigDecimal.ZERO;
        BigDecimal change24hUsd = BigDecimal.ZERO;

        for (BlockchainNetwork net : SUPPORTED_NETWORKS) {
            WalletEntity wallet = net.isToken()
                    ? walletByNetwork.get(net.getParentNetwork())
                    : walletByNetwork.get(net);
            if (wallet == null) continue;

            String symbol = net.getSymbol();
            BigDecimal confirmed = networkBalances.getOrDefault(net, BigDecimal.ZERO);
            BigDecimal reserved = reservedByWallet
                    .getOrDefault(wallet.getId(), Map.of())
                    .getOrDefault(net, BigDecimal.ZERO);
            BigDecimal effectiveConfirmed = reservationPort.effectiveConfirmed(wallet.getId(), net, confirmed, reserved);
            BigDecimal pendingOutgoing = reserved.min(effectiveConfirmed);
            BigDecimal balance = effectiveConfirmed.subtract(reserved).max(BigDecimal.ZERO);

            PriceService.CoinPrice cp = prices.get(symbol);
            BigDecimal currentPrice = (cp != null && cp.price() != null) ? cp.price() : BigDecimal.ZERO;
            BigDecimal valueUsd = balance.multiply(currentPrice);
            BigDecimal usdValue = valueUsd.setScale(6, RoundingMode.HALF_UP);
            totalUsd = totalUsd.add(valueUsd);

            BigDecimal change24h = (cp != null && cp.change24h() != null)
                    ? cp.change24h().setScale(2, RoundingMode.HALF_UP)
                    : BigDecimal.ZERO;
            if (cp != null && cp.change24h() != null && valueUsd.signum() > 0) {
                BigDecimal pct = cp.change24h().divide(BigDecimal.valueOf(100), 8, RoundingMode.HALF_UP);
                BigDecimal denominator = BigDecimal.ONE.add(pct);
                if (denominator.compareTo(BigDecimal.ZERO) > 0) {
                    BigDecimal initialValueUsd = valueUsd.divide(denominator, 8, RoundingMode.HALF_UP);
                    change24hUsd = change24hUsd.add(valueUsd.subtract(initialValueUsd));
                }
            }

            String name = net.isToken() ? net.getButtonLabel() : net.getAsset().getDisplayName();
            String id = net.name().toLowerCase();

            items.add(new AssetItemDto(
                    id,
                    net.name(),
                    name,
                    symbol,
                    balance.setScale(8, RoundingMode.DOWN).stripTrailingZeros(),
                    usdValue,
                    currentPrice.setScale(2, RoundingMode.HALF_UP),
                    change24h,
                    wallet.getAddress(),
                    id,
                    priceService.getSparkline(symbol),
                    pendingOutgoing.setScale(8, RoundingMode.DOWN).stripTrailingZeros()));
        }

        BigDecimal change24hPercent = totalUsd.signum() > 0
                ? change24hUsd.divide(totalUsd, 6, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100))
                : BigDecimal.ZERO;

        return new PortfolioSummary(
                totalUsd.setScale(6, RoundingMode.HALF_UP),
                change24hUsd.setScale(6, RoundingMode.HALF_UP),
                change24hPercent.setScale(2, RoundingMode.HALF_UP),
                items);
    }

    public record PortfolioSummary(BigDecimal totalUsd, BigDecimal change24hUsd, BigDecimal change24hPercent,
                                   List<AssetItemDto> items) {
    }
}
