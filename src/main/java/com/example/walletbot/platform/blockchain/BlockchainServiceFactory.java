package com.example.walletbot.platform.blockchain;

import com.example.walletbot.platform.blockchain.cache.SpentUtxoCache;
import com.example.walletbot.platform.blockchain.cache.TronAccountInFlightCache;
import com.example.walletbot.platform.blockchain.chain.*;
import com.example.walletbot.platform.blockchain.client.SolRpcClient;
import com.example.walletbot.platform.blockchain.client.TonCenterClient;
import com.example.walletbot.platform.blockchain.client.TronGridClient;
import com.example.walletbot.platform.blockchain.config.BlockchainProperties;
import com.example.walletbot.platform.blockchain.config.RpcProperties;
import com.example.walletbot.platform.blockchain.token.ErcTokenService;
import com.example.walletbot.platform.blockchain.token.SolTokenService;
import com.example.walletbot.platform.blockchain.token.TonTokenService;
import com.example.walletbot.platform.blockchain.token.TrcTokenService;
import com.example.walletbot.platform.blockchain.util.RpcEndpointResolver;
import com.example.walletbot.shared.constant.BlockchainNetwork;
import com.example.walletbot.shared.constant.TokenInfo;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bitcoinj.params.MainNetParams;
import org.bitcoinj.params.TestNet3Params;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.EnumMap;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class BlockchainServiceFactory {

    private final RestTemplate restTemplate;
    private final SpentUtxoCache spentUtxoCache;
    private final TronAccountInFlightCache tronAccountInFlightCache;
    private final JettonWalletResolver jettonWalletResolver;
    private final TonCenterClient tonCenter;
    private final TronGridClient tronGrid;
    private final SolRpcClient solRpcClient;
    private final RpcProperties rpc;
    private final BlockchainProperties blockchain;

    private final Map<BlockchainNetwork, BlockchainService> services = new EnumMap<>(BlockchainNetwork.class);

    @PostConstruct
    public void init() {
        String ethEffective = RpcEndpointResolver.buildUrl(rpc.eth().url(), rpc.eth().key());
        String bnbEffective = RpcEndpointResolver.buildUrl(rpc.bnb().url(), rpc.bnb().key());

        BlockchainProperties.Evm evm = blockchain.evm();
        BlockchainProperties.Token token = blockchain.token();

        EvmBlockchainService ethService = new EvmBlockchainService(
                BlockchainNetwork.ETH, ethEffective, evm.strictChecksum(), evm.ethChainId());
        EvmBlockchainService bnbService = new EvmBlockchainService(
                BlockchainNetwork.BNB, bnbEffective, evm.strictChecksum(), evm.bnbChainId());
        services.put(BlockchainNetwork.ETH, ethService);
        services.put(BlockchainNetwork.BNB, bnbService);
        services.put(BlockchainNetwork.USDT_ERC20,
                new ErcTokenService(BlockchainNetwork.USDT_ERC20, TokenInfo.USDT, ethService,
                        token.usdtErc20Contract()));
        services.put(BlockchainNetwork.USDC_ERC20,
                new ErcTokenService(BlockchainNetwork.USDC_ERC20, TokenInfo.USDC, ethService,
                        token.usdcErc20Contract()));
        services.put(BlockchainNetwork.USDT_BEP20,
                new ErcTokenService(BlockchainNetwork.USDT_BEP20, TokenInfo.USDT, bnbService,
                        token.usdtBep20Contract()));
        services.put(BlockchainNetwork.USDC_BEP20,
                new ErcTokenService(BlockchainNetwork.USDC_BEP20, TokenInfo.USDC, bnbService,
                        token.usdcBep20Contract()));

        BlockchainProperties.Btc btc = blockchain.btc();
        var btcParams = "testnet".equalsIgnoreCase(btc.network()) ? TestNet3Params.get() : MainNetParams.get();
        services.put(BlockchainNetwork.BTC,
                new BtcBlockchainService(restTemplate, rpc.btc().url(), spentUtxoCache,
                        btcParams, btc.mempoolBaseUrl()));

        SolBlockchainService solService = new SolBlockchainService(solRpcClient);
        services.put(BlockchainNetwork.SOL, solService);
        services.put(BlockchainNetwork.USDT_SOL,
                new SolTokenService(BlockchainNetwork.USDT_SOL, solService, solRpcClient,
                        token.usdtSolMint(), BlockchainNetwork.USDT_SOL.getDecimals()));
        services.put(BlockchainNetwork.USDC_SOL,
                new SolTokenService(BlockchainNetwork.USDC_SOL, solService, solRpcClient,
                        token.usdcSolMint(), BlockchainNetwork.USDC_SOL.getDecimals()));

        BlockchainProperties.Ton tonCfg = blockchain.ton();
        int tonWalletId = tonCfg.walletId();
        TonBlockchainService tonService = new TonBlockchainService(
                tonCenter, tonWalletId, tonCfg.transferComment(), tonCfg.txValidUntilSeconds());
        services.put(BlockchainNetwork.TON, tonService);
        services.put(BlockchainNetwork.USDT_TON,
                new TonTokenService(BlockchainNetwork.USDT_TON, tonService, tonCenter, jettonWalletResolver,
                        tonWalletId));

        TrxBlockchainService trxService =
                new TrxBlockchainService(tronGrid, tronAccountInFlightCache);
        services.put(BlockchainNetwork.TRX, trxService);
        services.put(BlockchainNetwork.USDT_TRC20,
                new TrcTokenService(BlockchainNetwork.USDT_TRC20, trxService, tronGrid,
                        token.usdtTrc20Contract()));

        if ("testnet".equalsIgnoreCase(btc.network())) {
            log.warn("Running in TESTNET mode (btcNetwork={}, tonWalletId={}, ethChainId={}, bnbChainId={}).",
                    btc.network(), tonWalletId, evm.ethChainId(), evm.bnbChainId());
        }
        log.info("Initialized {} blockchain services (btcNetwork={}, tonWalletId={}): {}",
                services.size(), btc.network(), tonWalletId, services.keySet());
    }

    public BlockchainService getService(BlockchainNetwork network) {
        BlockchainService service = services.get(network);
        if (service == null) {
            throw new UnsupportedOperationException("Blockchain " + network + " is not yet supported");
        }
        return service;
    }
}