package finance.omm.score.core.reward;

import static finance.omm.utils.constants.TimeConstants.DAY_IN_MICRO_SECONDS;
import static finance.omm.utils.constants.TimeConstants.MONTH_IN_MICRO_SECONDS;
import static finance.omm.utils.constants.TimeConstants.YEAR_IN_MICRO_SECONDS;
import static finance.omm.utils.math.MathUtils.ICX;
import static finance.omm.utils.math.MathUtils.MILLION;
import static finance.omm.utils.math.MathUtils.exaDivide;
import static finance.omm.utils.math.MathUtils.exaMultiply;
import static finance.omm.utils.math.MathUtils.pow;

import finance.omm.core.score.interfaces.RewardWeightController;
import finance.omm.libs.address.AddressProvider;
import finance.omm.libs.address.Contracts;
import finance.omm.libs.structs.WeightStruct;
import finance.omm.score.core.reward.db.AssetWeightDB;
import finance.omm.score.core.reward.db.TypeWeightDB;
import finance.omm.score.core.reward.exception.RewardException;
import finance.omm.score.core.reward.model.Asset;
import finance.omm.utils.constants.TimeConstants;
import finance.omm.utils.math.MathUtils;
import java.math.BigInteger;
import java.util.List;
import java.util.Map;
import score.Context;
import score.VarDB;
import score.annotation.EventLog;
import score.annotation.External;
import score.annotation.Optional;
import scorex.util.HashMap;

public class RewardWeightControllerImpl extends AddressProvider implements RewardWeightController {

    public static final String TAG = "Reward Controller";
    public static final BigInteger DAYS_PER_YEAR = BigInteger.valueOf(365L);
    public static final String TIMESTAMP_AT_START = "timestampAtStart";

    public final TypeWeightDB typeWeightDB = new TypeWeightDB("type");
    public final AssetWeightDB assetWeightDB = new AssetWeightDB("asset");

    private final VarDB<BigInteger> _timestampAtStart = Context.newVarDB(TIMESTAMP_AT_START, BigInteger.class);

    public RewardWeightControllerImpl(String addressProvider, BigInteger startTimestamp) {
        super(addressProvider);
        if (this._timestampAtStart.getOrDefault(null) == null) {
            this._timestampAtStart.set(startTimestamp);
        }
    }

    @External(readonly = true)
    public String name() {
        return TAG;
    }

    @External
    public String addType(String key, boolean transferToContract) {
        checkRewardDistribution();
        typeWeightDB.add(key, transferToContract);
        if (transferToContract) {
            String id = assetWeightDB.addAsset(key, key);
            WeightStruct weightStruct = new WeightStruct();
            weightStruct.weight = ICX;
            weightStruct.id = id;
            setTypeWeight(new WeightStruct[]{weightStruct}, BigInteger.ZERO);
            return id;
        }
        return null;
    }

    @External
    public void setTypeWeight(WeightStruct[] weights, @Optional BigInteger timestamp) {
        checkOwner();
        if (timestamp == null || timestamp.equals(BigInteger.ZERO)) {
            timestamp = TimeConstants.getBlockTimestamp();
        }

        typeWeightDB.setWeights(weights, timestamp);
        SetTypeWeight(timestamp, "Type weight updated");
    }

    @External(readonly = true)
    public Map<String, BigInteger> getTypeWeight(String typeId, @Optional BigInteger timestamp) {
        if (timestamp == null || timestamp.equals(BigInteger.ZERO)) {
            timestamp = TimeConstants.getBlockTimestamp();
        }
        return this.typeWeightDB.getWeight(typeId, timestamp);
    }

    @External(readonly = true)
    public Map<String, BigInteger> getALlTypeWeight(@Optional BigInteger timestamp) {
        if (timestamp == null || timestamp.equals(BigInteger.ZERO)) {
            timestamp = TimeConstants.getBlockTimestamp();
        }
        return this.typeWeightDB.weightOfAllTypes(timestamp);
    }


    @Override
    @External
    public String addAsset(String typeId, String name) {
        checkRewardDistribution();
        checkTypeId(typeId);
        return assetWeightDB.addAsset(typeId, name);
    }


    @External
    public void setAssetWeight(String typeId, WeightStruct[] weights, @Optional BigInteger timestamp) {
        checkOwner();
        if (timestamp == null || timestamp.equals(BigInteger.ZERO)) {
            timestamp = TimeConstants.getBlockTimestamp();
        }
        assetWeightDB.setWeights(typeId, weights, timestamp);
    }


    @External(readonly = true)
    public BigInteger tokenDistributionPerDay(BigInteger _day) {

        if (MathUtils.isLesThanEqual(_day, BigInteger.ZERO)) {
            return BigInteger.ZERO;
        } else if (MathUtils.isLesThanEqual(_day, BigInteger.valueOf(30L))) {
            return MILLION;
        } else if (MathUtils.isLesThanEqual(_day, DAYS_PER_YEAR)) {
            return BigInteger.valueOf(4L).multiply(MILLION).divide(BigInteger.TEN);
        } else if (MathUtils.isLesThanEqual(_day, DAYS_PER_YEAR.multiply(BigInteger.TWO))) {
            return BigInteger.valueOf(3L).multiply(MILLION).divide(BigInteger.TEN);
        } else if (MathUtils.isLesThanEqual(_day, BigInteger.valueOf(3L).multiply(DAYS_PER_YEAR))) {
            return BigInteger.valueOf(2L).multiply(MILLION).divide(BigInteger.TEN);
        } else if (MathUtils.isLesThanEqual(_day, BigInteger.valueOf(4L).multiply(DAYS_PER_YEAR))) {
            return BigInteger.valueOf(34L).multiply(MILLION).divide(BigInteger.TEN);
        } else {
            BigInteger index = _day.divide(DAYS_PER_YEAR).subtract(BigInteger.valueOf(4L));
            return pow(BigInteger.valueOf(103L), (index.intValue()))
                    .multiply(BigInteger.valueOf(3L))
                    .multiply(BigInteger.valueOf(383L).multiply(MILLION))
                    .divide(DAYS_PER_YEAR)
                    .divide(pow(BigInteger.valueOf(100L),
                            (index.intValue() + 1)));
        }
    }

    @External(readonly = true)
    public BigInteger getDay() {
        BigInteger timestamp = TimeConstants.getBlockTimestamp();
        return timestamp.subtract(_timestampAtStart.get()).divide(DAY_IN_MICRO_SECONDS);
    }

    private Map<String, BigInteger> getInflationRateByTimestamp(BigInteger tInMicroSeconds) {
        BigInteger startTimestamp = _timestampAtStart.get();
        BigInteger timeDelta = tInMicroSeconds.subtract(startTimestamp);
        if (timeDelta.compareTo(BigInteger.ZERO) > 0) {
            BigInteger numberOfYears = timeDelta.divide(YEAR_IN_MICRO_SECONDS);
            if (numberOfYears.equals(BigInteger.ZERO) && timeDelta.compareTo(MONTH_IN_MICRO_SECONDS) > 0) {
                startTimestamp = startTimestamp.add(MONTH_IN_MICRO_SECONDS);
            }
            startTimestamp = startTimestamp.add(numberOfYears.multiply(YEAR_IN_MICRO_SECONDS));
        }

        BigInteger delta = startTimestamp.subtract(_timestampAtStart.get());

        BigInteger numberOfDay = delta.divide(DAY_IN_MICRO_SECONDS).add(BigInteger.ONE);
        return Map.of(
                "startTimestamp", startTimestamp,
                "rate", getInflationRate(numberOfDay)
        );
    }

    private BigInteger getInflationRate(BigInteger _day) {
        return tokenDistributionPerDay(_day).divide(DAY_IN_MICRO_SECONDS);
    }


    @External(readonly = true)
    public BigInteger getIntegrateIndex(String assetId, BigInteger totalSupply, BigInteger lastUpdatedTimestamp) {
        if (totalSupply.compareTo(BigInteger.ZERO) <= 0) {
            return BigInteger.ZERO;
        }
        BigInteger timestamp = TimeConstants.getBlockTimestamp();
        BigInteger actual = timestamp;
        BigInteger integrateIndex = BigInteger.ZERO;
        Asset asset = assetWeightDB.getAsset(assetId);
        if (asset == null) {
            return BigInteger.ZERO;
        }
        BigInteger initialTimestamp = this._timestampAtStart.get();
        //TODO other condition to exit loop
        while (timestamp.compareTo(initialTimestamp) >= 0 && timestamp.compareTo(lastUpdatedTimestamp) > 0) {
            Map<String, BigInteger> result = calculateIntegrateIndex(asset, lastUpdatedTimestamp, timestamp, actual);
            integrateIndex = integrateIndex.add(exaDivide(result.get("integrateIndex"), totalSupply));
            actual = result.get("timestamp");
            timestamp = actual.subtract(BigInteger.ONE);
        }

        return integrateIndex;
    }


    private Map<String, BigInteger> calculateIntegrateIndex(Asset asset, BigInteger start, BigInteger timestamp,
            BigInteger actual) {
        Map<String, BigInteger> assetWeight = assetWeightDB.getWeight(asset, timestamp);
        int assetIndex = assetWeight.get("index").intValue();
        BigInteger aTimestamp = assetWeight.get("timestamp");
        BigInteger aWeight = assetWeight.get("value");

        Map<String, BigInteger> typeWeight = typeWeightDB.getWeight(asset.typeId, timestamp);
        int typeIndex = typeWeight.get("index").intValue();
        BigInteger tTimestamp = typeWeight.get("timestamp");
        BigInteger tWeight = typeWeight.get("value");

        Map<String, BigInteger> inflationRate = getInflationRateByTimestamp(timestamp);

        BigInteger maximum = aTimestamp.max(tTimestamp).max(inflationRate.get("startTimestamp")).max(start);

        if (maximum.equals(actual)) {
            return Map.of("integrateIndex", BigInteger.ZERO, "timestamp", actual);
        }
        BigInteger rate = exaMultiply(exaMultiply(inflationRate.get("rate"), tWeight), aWeight);
        BigInteger integrateIndex = rate.multiply(actual.subtract(maximum).divide(TimeConstants.SECOND));
        return Map.of("integrateIndex", integrateIndex, "timestamp", maximum);
    }

    private void checkRewardDistribution() {
        if (!Context.getCaller().equals(getAddress(Contracts.REWARDS.getKey()))) {
            throw RewardException.notAuthorized("require reward distribution contract access");
        }
    }

    private void checkOwner() {
        if (!Context.getOwner().equals(Context.getCaller())) {
            throw RewardException.notOwner();
        }
    }

    private void checkTypeId(String typeId) {
        if (!typeWeightDB.isValidId(typeId)) {
            throw RewardException.notValidTypeId(typeId);
        }

        if (typeWeightDB.isContractType(typeId)) {
            throw RewardException.unknown("Contract type can't have child assets");
        }
    }

    @External(readonly = true)
    public BigInteger getTypeCheckpointCount() {
        return BigInteger.valueOf(this.typeWeightDB.getCheckpointCount());
    }

    @External(readonly = true)
    public BigInteger getAssetCheckpointCount(String typeId) {
        return BigInteger.valueOf(this.assetWeightDB.getCheckpointCount(typeId));
    }

    @External(readonly = true)
    public Map<String, BigInteger> getTypeWeightByTimestamp(BigInteger timestamp) {
        return this.typeWeightDB.getWeightByTimestamp(timestamp);
    }


    @External(readonly = true)
    public Map<String, BigInteger> getAssetWeightByTimestamp(String typeId, BigInteger timestamp) {
        return this.assetWeightDB.getWeightByTimestamp(typeId, timestamp);
    }

    @External(readonly = true)
    public BigInteger getAssetWeight(String assetId, @Optional BigInteger timestamp) {
        if (timestamp == null || timestamp.equals(BigInteger.ZERO)) {
            timestamp = TimeConstants.getBlockTimestamp();
        }
        Map<String, BigInteger> result = this.assetWeightDB.getWeight(assetId, timestamp);
        return result.get("value");
    }

    @External(readonly = true)
    public Map<String, ?> getAllAssetDistributionPercentage(@Optional BigInteger timestamp) {
        if (timestamp == null || timestamp.equals(BigInteger.ZERO)) {
            timestamp = TimeConstants.getBlockTimestamp();
        }
        List<String> typeIds = this.typeWeightDB.getTypeIds();
        HashMap<String, Map<String, BigInteger>> response = new HashMap<>();
        for (String typeId : typeIds) {
            Map<String, BigInteger> typeWeight = typeWeightDB.getWeight(typeId, timestamp);
            BigInteger tWeight = typeWeight.get("value");
            Map<String, BigInteger> assetWeights = this.assetWeightDB.getAggregatedWeight(tWeight, typeId, timestamp);
            response.put(typeId, assetWeights);
        }
        return response;
    }

    @External(readonly = true)
    public Map<String, ?> getDailyRewards(@Optional BigInteger _day) {
        BigInteger timestamp = TimeConstants.getBlockTimestamp();
        if (_day == null || BigInteger.ZERO.equals(_day)) {
            _day = getDay();
        } else {
            timestamp = this._timestampAtStart.get().add(_day.multiply(DAY_IN_MICRO_SECONDS));
        }
        BigInteger _distribution = tokenDistributionPerDay(_day);
        List<String> typeIds = this.typeWeightDB.getTypeIds();
        HashMap<String, Object> response = new HashMap<>();
        BigInteger totalRewards = BigInteger.ZERO;
        for (String typeId : typeIds) {
            Map<String, BigInteger> typeWeight = typeWeightDB.getWeight(typeId, timestamp);
            BigInteger tWeight = typeWeight.get("value");
            BigInteger _distributionValue = exaMultiply(_distribution, tWeight);
            Map<String, BigInteger> assetWeights = this.assetWeightDB.getAggregatedWeight(_distributionValue, typeId,
                    timestamp);
            response.put(typeId, assetWeights);
            totalRewards = totalRewards.add(_distributionValue);
        }
        response.put("day", _day);
        response.put("total", totalRewards);
        return response;
    }


    @External(readonly = true)
    public Map<String, BigInteger> getDistPercentageOfLP(@Optional BigInteger timestamp) {
        if (timestamp == null || timestamp.equals(BigInteger.ZERO)) {
            timestamp = TimeConstants.getBlockTimestamp();
        }
        Map<String, BigInteger> lpAssetIds = (Map<String, BigInteger>) Context.call(
                getAddress(Contracts.REWARDS.toString()), "getLiquidityProviders");

        Map<String, BigInteger> response = new HashMap<>();
        for (Map.Entry<String, BigInteger> entry : lpAssetIds.entrySet()) {
            String assetId = entry.getKey();
            BigInteger lpID = entry.getValue();
            Asset asset = this.assetWeightDB.getAsset(assetId);
            if (asset != null) {
                BigInteger value = this.assetWeightDB.getWeight(assetId, timestamp).get("value");
                response.put(lpID.toString(10), value);
            }
        }
        return response;
    }

    @External(readonly = true)
    public BigInteger getStartTimestamp() {
        return this._timestampAtStart.get();
    }


    @External(readonly = true)
    public Map<String, ?> distributionInfo(BigInteger day) {
        Map<String, Object> response = new HashMap<>() {{
            put("isValid", true);
        }};
        BigInteger today = getDay();

        if (day.compareTo(today) > 0) {
            response.put("isValid", false);
            return response;
        }
        BigInteger distribution = BigInteger.ZERO;
        for (int i = day.intValue(); i <= today.intValue(); i++) {
            distribution = distribution.add(tokenDistributionPerDay(BigInteger.valueOf(i)));
        }
        response.put("distribution", distribution);
        response.put("day", today);
        return response;
    }

    @EventLog(indexed = 2)
    public void SetTypeWeight(BigInteger timestamp, String message) {
    }

}
