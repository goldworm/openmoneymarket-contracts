package finance.omm.libs.test.integration;


import static finance.omm.libs.test.integration.Environment.chain;

import finance.omm.core.score.interfaces.AddressManager;
import finance.omm.core.score.interfaces.AddressManagerScoreClient;
import finance.omm.core.score.interfaces.BoostedToken;
import finance.omm.core.score.interfaces.BoostedTokenScoreClient;
import finance.omm.core.score.interfaces.DAOFund;
import finance.omm.core.score.interfaces.DAOFundScoreClient;
import finance.omm.core.score.interfaces.Delegation;
import finance.omm.core.score.interfaces.DelegationScoreClient;
import finance.omm.core.score.interfaces.FeeProvider;
import finance.omm.core.score.interfaces.FeeProviderScoreClient;
import finance.omm.core.score.interfaces.Governance;
import finance.omm.core.score.interfaces.GovernanceScoreClient;
import finance.omm.core.score.interfaces.LendingPoolCore;
import finance.omm.core.score.interfaces.LendingPoolCoreScoreClient;
import finance.omm.core.score.interfaces.OMMToken;
import finance.omm.core.score.interfaces.OMMTokenScoreClient;
import finance.omm.core.score.interfaces.RewardDistribution;
import finance.omm.core.score.interfaces.RewardDistributionScoreClient;
import finance.omm.core.score.interfaces.RewardWeightController;
import finance.omm.core.score.interfaces.RewardWeightControllerScoreClient;
import finance.omm.core.score.interfaces.StakedLP;
import finance.omm.core.score.interfaces.StakedLPScoreClient;
import finance.omm.core.score.interfaces.WorkerToken;
import finance.omm.core.score.interfaces.WorkerTokenScoreClient;
import finance.omm.libs.address.Contracts;
import finance.omm.libs.test.integration.scores.DToken;
import finance.omm.libs.test.integration.scores.DTokenScoreClient;
import finance.omm.libs.test.integration.scores.DummyPriceOracle;
import finance.omm.libs.test.integration.scores.DummyPriceOracleScoreClient;
import finance.omm.libs.test.integration.scores.LendingPool;
import finance.omm.libs.test.integration.scores.LendingPoolDataProvider;
import finance.omm.libs.test.integration.scores.LendingPoolDataProviderScoreClient;
import finance.omm.libs.test.integration.scores.LendingPoolScoreClient;
import finance.omm.libs.test.integration.scores.LiquidationManager;
import finance.omm.libs.test.integration.scores.LiquidationManagerScoreClient;
import finance.omm.libs.test.integration.scores.OToken;
import finance.omm.libs.test.integration.scores.OTokenScoreClient;
import finance.omm.libs.test.integration.scores.PriceOracle;
import finance.omm.libs.test.integration.scores.PriceOracleScoreClient;
import finance.omm.libs.test.integration.scores.Staking;
import finance.omm.libs.test.integration.scores.StakingScoreClient;
import finance.omm.libs.test.integration.scores.SystemInterface;
import finance.omm.libs.test.integration.scores.SystemInterfaceScoreClient;
import finance.omm.libs.test.integration.scores.sICX;
import finance.omm.libs.test.integration.scores.sICXScoreClient;
import foundation.icon.icx.KeyWallet;
import foundation.icon.jsonrpc.Address;
import foundation.icon.score.client.ScoreClient;
import java.util.Map.Entry;
import java.util.NoSuchElementException;

public class OMMClient {

    private OMM omm;
    private KeyWallet wallet;

    //
    @ScoreClient
    public Governance governance;
    @ScoreClient
    public Delegation delegation;
    @ScoreClient
    public AddressManager addressManager;
    @ScoreClient
    public DAOFund daoFund;
    @ScoreClient
    public FeeProvider feeProvider;
    @ScoreClient
    public RewardWeightController rewardWeightController;
    @ScoreClient
    public RewardDistribution reward;
    @ScoreClient
    public OMMToken ommToken;
    @ScoreClient
    public BoostedToken bOMM;

    //python
    @ScoreClient
    public WorkerToken workerToken;
    @ScoreClient
    public LendingPoolCore lendingPoolCore;
    @ScoreClient
    public LendingPool lendingPool;
    @ScoreClient
    public LendingPoolDataProvider lendingPoolDataProvider;
    @ScoreClient
    public OToken oICX;
    @ScoreClient
    public DToken dICX;

    @ScoreClient
    public OToken oUSDC;
    @ScoreClient
    public DToken dUSDC;

    @ScoreClient
    public LiquidationManager liquidationManager;
    @ScoreClient
    public PriceOracle priceOracle;
    @ScoreClient
    public StakedLP stakedLP;


    //dummy
    @ScoreClient
    public sICX sICX;
    @ScoreClient
    public Staking staking;
    @ScoreClient
    public DummyPriceOracle dummyPriceOracle;
    @ScoreClient
    public SystemInterface systemScore;

    public OMMClient(OMM omm, KeyWallet wallet) {
        this.omm = omm;
        this.wallet = wallet;
        init();
    }

    private void init() {
        for (Entry<String, Address> entry : this.omm.getAddresses().entrySet()) {
            switch (entry.getKey()) {
                case "governance":
                    governance = new GovernanceScoreClient(chain.getEndpointURL(), chain.networkId, wallet,
                            entry.getValue());
                    break;
                case "delegation":
                    delegation = new DelegationScoreClient(chain.getEndpointURL(), chain.networkId, wallet,
                            entry.getValue());
                    break;
                case "addressProvider":
                    addressManager = new AddressManagerScoreClient(chain.getEndpointURL(), chain.networkId, wallet,
                            entry.getValue());
                    break;
                case "daoFund":
                    daoFund = new DAOFundScoreClient(chain.getEndpointURL(), chain.networkId, wallet,
                            entry.getValue());
                    break;
                case "feeProvider":
                    feeProvider = new FeeProviderScoreClient(chain.getEndpointURL(), chain.networkId, wallet,
                            entry.getValue());
                    break;
                case "rewardWeightController":
                    rewardWeightController = new RewardWeightControllerScoreClient(chain.getEndpointURL(),
                            chain.networkId, wallet,
                            entry.getValue());
                    break;
                case "rewards":
                    reward = new RewardDistributionScoreClient(chain.getEndpointURL(), chain.networkId, wallet,
                            entry.getValue());
                    break;
                case "ommToken":
                    ommToken = new OMMTokenScoreClient(chain.getEndpointURL(), chain.networkId, wallet,
                            entry.getValue());
                    break;
                case "bOMM":
                    bOMM = new BoostedTokenScoreClient(chain.getEndpointURL(), chain.networkId, wallet,
                            entry.getValue());
                    break;
                case "workerToken":
                    workerToken = new WorkerTokenScoreClient(chain.getEndpointURL(), chain.networkId, wallet,
                            entry.getValue());
                    break;
                case "sICX":
                    sICX = new sICXScoreClient(chain.getEndpointURL(), chain.networkId, wallet,
                            entry.getValue());
                    break;
                case "staking":
                    staking = new StakingScoreClient(chain.getEndpointURL(), chain.networkId, wallet,
                            entry.getValue());
                    break;
                case "bandOracle":
                    dummyPriceOracle = new DummyPriceOracleScoreClient(chain.getEndpointURL(), chain.networkId, wallet,
                            entry.getValue());
                    break;
                case "systemScore":
                    systemScore = new SystemInterfaceScoreClient(chain.getEndpointURL(), chain.networkId, wallet,
                            entry.getValue());
                    break;
                case "lendingPoolCore":
                    lendingPoolCore = new LendingPoolCoreScoreClient(chain.getEndpointURL(), chain.networkId, wallet,
                            entry.getValue());
                    break;
                case "lendingPool":
                    lendingPool = new LendingPoolScoreClient(chain.getEndpointURL(), chain.networkId, wallet,
                            entry.getValue());
                    break;
                case "lendingPoolDataProvider":
                    lendingPoolDataProvider = new LendingPoolDataProviderScoreClient(chain.getEndpointURL(),
                            chain.networkId, wallet,
                            entry.getValue());
                    break;
                case "dICX":
                    dICX = new DTokenScoreClient(chain.getEndpointURL(), chain.networkId, wallet,
                            entry.getValue());
                    break;

                case "oICX":
                    oICX = new OTokenScoreClient(chain.getEndpointURL(), chain.networkId, wallet,
                            entry.getValue());
                    break;
                case "oUSDC":
                    oUSDC = new OTokenScoreClient(chain.getEndpointURL(), chain.networkId, wallet,
                            entry.getValue());
                    break;
                case "liquidationManager":
                    liquidationManager = new LiquidationManagerScoreClient(chain.getEndpointURL(), chain.networkId,
                            wallet,
                            entry.getValue());
                    break;
                case "dUSDC":
                    dUSDC = new DTokenScoreClient(chain.getEndpointURL(), chain.networkId, wallet,
                            entry.getValue());
                    break;
                case "priceOracle":
                    priceOracle = new PriceOracleScoreClient(chain.getEndpointURL(), chain.networkId, wallet,
                            entry.getValue());
                    break;
                case "stakedLP":
                    stakedLP = new StakedLPScoreClient(chain.getEndpointURL(), chain.networkId, wallet,
                            entry.getValue());
                    break;

                default:
                    throw new NoSuchElementException(entry.getKey() + " score not found!!");

            }

        }
    }

    public score.Address getAddress() {
        return score.Address.fromString(wallet.getAddress().toString());
    }

    public score.Address getAddress(Contracts contract) {
        return score.Address.fromString(wallet.getAddress().toString());
    }

    public KeyWallet getWallet() {
        return wallet;
    }
}