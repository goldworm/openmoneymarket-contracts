package finance.omm.score.core.lendingpoolDataProvider.test.integration;

import com.eclipsesource.json.JsonObject;
import finance.omm.libs.address.Contracts;
import finance.omm.libs.test.integration.OMM;
import finance.omm.libs.test.integration.OMMClient;
import finance.omm.libs.test.integration.ScoreIntegrationTest;
import finance.omm.libs.test.integration.configs.Config;
import finance.omm.libs.test.integration.scores.LendingPoolScoreClient;
import finance.omm.score.core.lendingpoolDataProvider.test.integration.config.dataProviderConfig;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer.OrderAnnotation;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import foundation.icon.jsonrpc.Address;
import org.junit.jupiter.api.Nested;
import score.annotation.Optional;

import java.math.BigInteger;
import java.util.HashMap;
import java.util.Map;

import static finance.omm.utils.math.MathUtils.ICX;
import static finance.omm.utils.math.MathUtils.convertToExa;
import static finance.omm.utils.math.MathUtils.exaDivide;
import static finance.omm.utils.math.MathUtils.exaMultiply;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@TestInstance(Lifecycle.PER_CLASS)
@TestMethodOrder(OrderAnnotation.class)
public class LendingPoolDataProviderIT implements ScoreIntegrationTest {

    private static OMMClient ommClient;

    private static OMMClient testClient;

    private static Map<String, Address> addressMap;

    @BeforeAll
    static void setup() throws Exception {
        OMM omm = new OMM("conf/all-contracts.json");

        omm.setupOMM();
        addressMap = omm.getAddresses();
//        System.out.println("addressMap " + addressMap);
        Config config = new dataProviderConfig();
        omm.runConfig(config);
        ommClient = omm.defaultClient();
        testClient = omm.testClient();
        mintToken();
    }

    @Test
    void testName(){
        assertEquals("Omm Lending Pool Data Provider",ommClient.lendingPoolDataProvider.name());
    }

    Map<String, Boolean> STATES = new HashMap<>();

    @DisplayName("Deposit Transactions")
    @Nested
    @TestMethodOrder(OrderAnnotation.class)
    class DepositTest {


        Address icx_reserve = addressMap.get(Contracts.sICX.getKey());
        Address iusdc_addr = addressMap.get(Contracts.IUSDC.getKey());

        private void depositICX(OMMClient client, BigInteger amount){
            ((LendingPoolScoreClient)client.lendingPool).deposit(amount,amount);
        }

        private void depositIUSDC(OMMClient client, BigInteger amount){
            byte[] data = createByteArray("deposit",amount,null,null,null);
            client.iUSDC.transfer(addressMap.get(Contracts.LENDING_POOL.getKey()),
                    amount,data);
        }

        private void transfer(OMMClient client, BigInteger amount){
            ommClient.iUSDC.transfer(client.getAddress(),amount.multiply(BigInteger.valueOf(1000_000)),
                    new byte[]{});
        }

        @Test
        @Order(1)
        @DisplayName("user deposits ICX")
        void reserveAccountData_deposit_icx(){
            BigInteger amount_to_deposit = BigInteger.valueOf(2).multiply(ICX);
            depositICX(testClient,amount_to_deposit);

            Map<String,BigInteger> icxReserveData = getReserveAccountDataTest();

            BigInteger reservePrice = exaMultiply(BigInteger.valueOf(3).multiply(ICX).divide(BigInteger.TEN),getTodayRateInternal());


            // verification
            assertEquals(BigInteger.valueOf(2).multiply(reservePrice),icxReserveData.get("totalLiquidityBalanceUSD"));
            assertEquals(BigInteger.valueOf(2).multiply(reservePrice),icxReserveData.get("availableLiquidityBalanceUSD"));
            assertEquals(BigInteger.ZERO,icxReserveData.get("totalBorrowsBalanceUSD"));
            assertEquals(BigInteger.valueOf(2).multiply(reservePrice),icxReserveData.get("totalCollateralBalanceUSD"));
        }

        @Test
        @Order(1)
        @DisplayName("user deposits IUSDC")
        void reserveAccountData_deposit_iusdc(){

            BigInteger amount_to_deposit = BigInteger.valueOf(2).multiply(BigInteger.valueOf(1000_000));
            transfer(testClient,amount_to_deposit);
            depositIUSDC(testClient,amount_to_deposit);

            Map<String,BigInteger> iusdcReserveData = getReserveAccountDataTest();


            BigInteger liquidity = BigInteger.valueOf(600000000000000000L).add(BigInteger.valueOf(2).multiply(ICX));

            assertEquals(liquidity,iusdcReserveData.get("totalLiquidityBalanceUSD"));
            assertEquals(liquidity,iusdcReserveData.get("availableLiquidityBalanceUSD"));
            assertEquals(BigInteger.ZERO,iusdcReserveData.get("totalBorrowsBalanceUSD"));
            assertEquals(liquidity,iusdcReserveData.get("totalCollateralBalanceUSD"));

        }

        @Test
        @Order(2)
        @DisplayName("checking user account data")
        void userAccountData(){

            Map<String,Object> testClientAccountData = getUserAccountDataTest(testClient.getAddress());

            BigInteger liquidity = BigInteger.valueOf(600000000000000000L).add(BigInteger.valueOf(2).multiply(ICX));

            assertEquals(BigInteger.valueOf(1300000000000000000L),toBigInt((String) testClientAccountData.get("availableBorrowsUSD")));
            assertEquals(liquidity,toBigInt((String) testClientAccountData.get("totalLiquidityBalanceUSD")));
            assertEquals(liquidity, toBigInt((String)testClientAccountData.get("totalCollateralBalanceUSD")));
            assertEquals(BigInteger.valueOf(500000000000000000L),
                    toBigInt((String) testClientAccountData.get("currentLtv")));
            assertEquals(BigInteger.valueOf(650000000000000000L),
                    toBigInt((String) testClientAccountData.get("currentLiquidationThreshold")));
            assertEquals(BigInteger.ZERO,toBigInt((String)testClientAccountData.get("totalBorrowBalanceUSD")));

        }

        @Test
        @Order(2)
        @DisplayName("checking user data in icx and iusdc reseve")
        void userReserveData(){

            Map<String,BigInteger> userReserveData_sicx = userReserveDataTest(icx_reserve,testClient.getAddress());
            assertEquals(BigInteger.valueOf(2).multiply(ICX),userReserveData_sicx.get("currentOTokenBalance"));
            assertEquals(BigInteger.valueOf(2).multiply(ICX),userReserveData_sicx.get("principalOTokenBalance"));
            assertEquals(BigInteger.valueOf(600000000000000000L),userReserveData_sicx.get("currentOTokenBalanceUSD"));
            assertEquals(BigInteger.valueOf(600000000000000000L),userReserveData_sicx.get("principalOTokenBalanceUSD"));
            assertEquals(BigInteger.ZERO,userReserveData_sicx.get("currentBorrowBalance"));
            assertEquals(BigInteger.valueOf(18),userReserveData_sicx.get("decimals"));
            assertEquals(BigInteger.ONE.multiply(ICX),userReserveData_sicx.get("userLiquidityCumulativeIndex"));
            assertEquals(BigInteger.ZERO,userReserveData_sicx.get("userBorrowCumulativeIndex"));
            assertEquals(BigInteger.valueOf(20000000000000000L),userReserveData_sicx.get("borrowRate"));

            Map<String,BigInteger> userReserveData_iusdc = userReserveDataTest(iusdc_addr,testClient.getAddress());
            assertEquals(BigInteger.valueOf(2000000),userReserveData_iusdc.get("currentOTokenBalance"));
            assertEquals(BigInteger.valueOf(2000000),userReserveData_iusdc.get("principalOTokenBalance"));
            assertEquals(BigInteger.valueOf(2000000000000000000L),userReserveData_iusdc.get("currentOTokenBalanceUSD"));
            assertEquals(BigInteger.valueOf(2000000000000000000L),userReserveData_iusdc.get("principalOTokenBalanceUSD"));
            assertEquals(BigInteger.ZERO,userReserveData_iusdc.get("currentBorrowBalance"));
            assertEquals(BigInteger.valueOf(6),userReserveData_iusdc.get("decimals"));
            assertEquals(BigInteger.ONE.multiply(ICX),userReserveData_iusdc.get("userLiquidityCumulativeIndex"));
            assertEquals(BigInteger.ZERO,userReserveData_iusdc.get("userBorrowCumulativeIndex"));
            assertEquals(BigInteger.valueOf(20000000000000000L),userReserveData_iusdc.get("borrowRate"));

        }

        @Test
        @DisplayName("checking reserve data")// after mathi ko
        @Order(3)
        void reserveData(){


            // another user adds Liquidity to icx reserve
            depositICX(ommClient,BigInteger.valueOf(100).multiply(ICX));

            Map<String,Object> reserveData_icx = getReserveData(icx_reserve);

            assertEquals(BigInteger.valueOf(3).multiply(ICX).divide(BigInteger.TEN),
                    toBigInt((String) reserveData_icx.get("exchangePrice")));
            assertEquals(getTodayRateInternal(), toBigInt((String) reserveData_icx.get("sICXRate")));
            assertEquals(BigInteger.valueOf(3060000000000000000L).multiply(BigInteger.TEN),
                    toBigInt((String) reserveData_icx.get("totalLiquidityUSD")));
            assertEquals(BigInteger.valueOf(3060000000000000000L).multiply(BigInteger.TEN),
                    toBigInt((String) reserveData_icx.get("availableLiquidityUSD")));
            assertEquals(BigInteger.ZERO, toBigInt((String) reserveData_icx.get("totalBorrowsUSD")));
            assertEquals(BigInteger.valueOf(125000000000000000L), toBigInt((String) reserveData_icx.get("borrowingPercentage")));
            assertEquals(BigInteger.valueOf(125000000000000000L), toBigInt((String) reserveData_icx.get("lendingPercentage")));
            assertEquals(BigInteger.valueOf(250000000000000000L), toBigInt((String) reserveData_icx.get("rewardPercentage")));


            Map<String,Object> reserveData_iusdc = getReserveData(iusdc_addr);

            assertEquals(ICX, toBigInt((String) reserveData_iusdc.get("exchangePrice")));
            assertNull( reserveData_iusdc.get("sICXRate"));
            assertEquals(BigInteger.valueOf(200000000000000000L).multiply(BigInteger.TEN),
                    toBigInt((String) reserveData_iusdc.get("totalLiquidityUSD")));
            assertEquals(BigInteger.valueOf(200000000000000000L).multiply(BigInteger.TEN),
                    toBigInt((String) reserveData_iusdc.get("availableLiquidityUSD")));
            assertEquals(BigInteger.ZERO, toBigInt((String) reserveData_iusdc.get("totalBorrowsUSD")));
            assertEquals(BigInteger.valueOf(125000000000000000L), toBigInt((String) reserveData_iusdc.get("borrowingPercentage")));
            assertEquals(BigInteger.valueOf(125000000000000000L), toBigInt((String) reserveData_iusdc.get("lendingPercentage")));
            assertEquals(BigInteger.valueOf(250000000000000000L), toBigInt((String) reserveData_iusdc.get("rewardPercentage")));


        }

        @DisplayName("Borrow Transactions")
        @Nested
        @TestMethodOrder(OrderAnnotation.class)
        class BorrowTest {

            @Test
            @DisplayName("user borrows from icx ")
            @Order(4)
            void borrow_icx(){


                // testClient deposit 50 again
                depositICX(testClient,BigInteger.valueOf(50).multiply(ICX));

                BigInteger amount = BigInteger.valueOf(10).multiply(ICX);
                BigInteger amountUSD = exaMultiply(amount,BigInteger.valueOf(3).multiply(ICX).divide(BigInteger.TEN));

                Map<String,BigInteger> icxReserveAccountData_before = getReserveAccountDataTest();

                assertEquals(BigInteger.valueOf(0),icxReserveAccountData_before.get("totalBorrowsBalanceUSD"));

                // borrow
                testClient.lendingPool.borrow(icx_reserve,amount);

                Map<String,BigInteger> icxReserveAccountData_after = getReserveAccountDataTest();
                Map<String,Object> icxReserveData_after = getReserveData(icx_reserve);
                Map<String,Object> icxUserAccountData_after = getUserAccountDataTest(testClient.getAddress());


                BigInteger totalBorrows = toBigInt((String) icxReserveData_after.get("totalBorrows"));
                BigInteger totalLiquidity = toBigInt((String) icxReserveData_after.get("totalLiquidity"));

                Map<String,BigInteger> expectedRate = getRates(totalBorrows,totalLiquidity,icx_reserve);

                assertEquals(icxReserveAccountData_before.get("totalLiquidityBalanceUSD"),icxReserveAccountData_after.get("totalLiquidityBalanceUSD"));
                assertEquals(icxReserveAccountData_before.get("totalCollateralBalanceUSD"),icxReserveAccountData_after.get("totalCollateralBalanceUSD"));
                assertEquals(icxReserveAccountData_before.get("availableLiquidityBalanceUSD").subtract(amountUSD),
                        icxReserveAccountData_after.get("availableLiquidityBalanceUSD"));
                assertEquals(icxReserveAccountData_before.get("totalBorrowsBalanceUSD").add(amountUSD),
                        icxReserveAccountData_after.get("totalBorrowsBalanceUSD"));

                // sICX balance should increase
                assertEquals(amount,ommClient.sICX.balanceOf(testClient.getAddress()));

                assertEquals(expectedRate.get("borrowRate"),toBigInt((String)icxReserveData_after.get("borrowRate")));
                assertEquals(expectedRate.get("liquidityRate"),toBigInt((String)icxReserveData_after.get("liquidityRate")));

                // healthFactor should change
                assertTrue(toBigInt((String) icxUserAccountData_after.get("healthFactor")).compareTo(BigInteger.ONE.negate())>0);

                STATES.put("borrow_icx",true);
            }


            @Test
            @DisplayName("user borrows from iusdc ")
            @Order(5)
            void borrow_iusdc(){

//                System.out.println(STATES.entrySet());
//                if (STATES.getOrDefault("borrow_icx",false)){
//                    return;
//                }
                // remove this
//                borrow_icx();

                BigInteger amount = BigInteger.valueOf(15).multiply(BigInteger.valueOf(1000_00));
                BigInteger amountUsd = exaMultiply(convertToExa(amount,BigInteger.valueOf(6)),ICX);

                Map<String,BigInteger> iusdcReserveAccountData_before = getReserveAccountDataTest();
                Map<String,Object> userAccountData_before = getUserAccountDataTest(testClient.getAddress());
                BigInteger balance_before = ommClient.iUSDC.balanceOf(testClient.getAddress());

                assertEquals(BigInteger.valueOf(3000000000000000000L),iusdcReserveAccountData_before.get("totalBorrowsBalanceUSD"));

                // borrow
                testClient.lendingPool.borrow(iusdc_addr,amount);

                BigInteger balance_after = ommClient.iUSDC.balanceOf(testClient.getAddress());


                Map<String,BigInteger> iusdcReserveAccountData_after = getReserveAccountDataTest();
                Map<String,Object> iusdcReserveData_after = getReserveData(iusdc_addr);
                Map<String,Object> userAccountData_after = getUserAccountDataTest(testClient.getAddress());

                BigInteger totalBorrows = toBigInt((String) iusdcReserveData_after.get("totalBorrows"));
                BigInteger totalLiquidity = toBigInt((String) iusdcReserveData_after.get("totalLiquidity"));

                Map<String,BigInteger> expectedRate = getRates(totalBorrows,totalLiquidity,icx_reserve);


                assertEquals(iusdcReserveAccountData_before.get("totalLiquidityBalanceUSD"),
                        iusdcReserveAccountData_after.get("totalLiquidityBalanceUSD"));

                assertEquals(iusdcReserveAccountData_before.get("availableLiquidityBalanceUSD").subtract(amountUsd),
                        iusdcReserveAccountData_after.get("availableLiquidityBalanceUSD"));

                assertEquals(iusdcReserveAccountData_before.get("totalBorrowsBalanceUSD").add(amountUsd),
                        iusdcReserveAccountData_after.get("totalBorrowsBalanceUSD"));

                assertEquals(iusdcReserveAccountData_before.get("totalCollateralBalanceUSD"),
                        iusdcReserveAccountData_after.get("totalCollateralBalanceUSD"));


                // balance should increase
                assertEquals(balance_before.add(amount),balance_after);

                assertEquals(expectedRate.get("borrowRate"),toBigInt((String)iusdcReserveData_after.get("borrowRate")));
                assertEquals(expectedRate.get("liquidityRate"),toBigInt((String)iusdcReserveData_after.get("liquidityRate")));

                // healthFactor should decrease
                assertTrue(toBigInt((String) userAccountData_before.get("healthFactor")).
                        compareTo(toBigInt((String)userAccountData_after.get("healthFactor")))>0);

            }


            @DisplayName("Repay Transactions")
            @Nested
            @TestMethodOrder(OrderAnnotation.class)
            class repay {

                @Test
                @DisplayName("partial repay of borrowed icx ")
                @Order(6)
                void repay_icx() throws InterruptedException {

                    BigInteger repay_amount = BigInteger.valueOf(5).multiply(ICX);
                    BigInteger repay_amountUSD = exaMultiply(repay_amount,BigInteger.valueOf(3).multiply(ICX).divide(BigInteger.TEN));
                    byte[] repay_data = createByteArray("repay",null,null,null,null);


                    BigInteger balance_before = ommClient.sICX.balanceOf(testClient.getAddress());
                    Map<String,Object> icxUserAccountData_before = getUserAccountDataTest(testClient.getAddress());

                    // repay
                    testClient.sICX.transfer(addressMap.get(Contracts.LENDING_POOL.getKey()), repay_amount, repay_data);

                    Thread.sleep(300);

                    BigInteger balance_after = ommClient.sICX.balanceOf(testClient.getAddress());
                    Map<String,Object> icxUserAccountData_after = getUserAccountDataTest(testClient.getAddress());
                    BigInteger feeUSD = toBigInt((String) icxUserAccountData_after.get("totalFeesUSD"));

                    assertEquals(balance_after,balance_before.subtract(repay_amount));

                    // available borrow should increase
                    float delta = (ICX.divide(BigInteger.valueOf(1000))).floatValue();
                    assertEquals((toBigInt((String) icxUserAccountData_before.get("availableBorrowsUSD"))
                                    .add(repay_amountUSD.subtract(feeUSD))).longValue(),
                            (toBigInt((String) icxUserAccountData_after.get("availableBorrowsUSD"))).longValue(),delta);


                    // totalBorrowBalanceUSD decrease
                    assertTrue((toBigInt((String) icxUserAccountData_before.get("totalBorrowBalanceUSD"))).compareTo(
                            toBigInt((String) icxUserAccountData_after.get("totalBorrowBalanceUSD")))>0);


                    // healthFactor increase
                    assertTrue(toBigInt((String) icxUserAccountData_after.get("healthFactor")).
                            compareTo(toBigInt((String)icxUserAccountData_before.get("healthFactor")))>0);

                    // totalCollateralBalanceUSD remains almost same
                    assertEquals((toBigInt((String) icxUserAccountData_after.get("totalCollateralBalanceUSD"))).longValue(),
                            toBigInt((String) icxUserAccountData_before.get("totalCollateralBalanceUSD")).longValue(),delta);


                }

                @Test
                @DisplayName("repay all iusdc borrow")
                @Order(7)
                void repay_all_borrowed_iusdc() throws InterruptedException{
                    Map<String,BigInteger> user_iusdc_reserve_data= userReserveDataTest(iusdc_addr,testClient.getAddress());

                    BigInteger borrowBalance = user_iusdc_reserve_data.get("currentBorrowBalance");
                    BigInteger originationFee = user_iusdc_reserve_data.get("originationFee");
                    BigInteger borrowedAmount = BigInteger.valueOf(15).multiply(BigInteger.valueOf(1000_00));
                    BigInteger repayAmount = borrowedAmount.add(originationFee);
                    byte[] repay_data = createByteArray("repay",null,null,null,null);

                    BigInteger balance_before = ommClient.iUSDC.balanceOf(testClient.getAddress());
                    Map<String,Object> iusdcUserAccountData_before = getUserAccountDataTest(testClient.getAddress());

                    BigInteger reapy_AmountUSD = exaMultiply(convertToExa(repayAmount,BigInteger.valueOf(6)),ICX);
                    Thread.sleep(3000);
                    // repay
                    testClient.iUSDC.transfer(addressMap.get(Contracts.LENDING_POOL.getKey()), borrowedAmount.add(originationFee), repay_data);

                    Map<String,BigInteger> user_iusdc_reserve_data_after= userReserveDataTest(iusdc_addr,testClient.getAddress());
                    BigInteger borrowBalanceAfter = user_iusdc_reserve_data_after.get("currentBorrowBalance");

                    BigInteger balance_after = ommClient.iUSDC.balanceOf(testClient.getAddress());

                    assertEquals(borrowBalance.subtract(borrowedAmount),borrowBalanceAfter);
                    assertEquals(balance_before.subtract(borrowedAmount.add(originationFee)),balance_after);
                    assertEquals(BigInteger.ZERO,user_iusdc_reserve_data_after.get("userBorrowCumulativeIndex"));

                    Map<String,Object> iusdcUserAccountData_after = getUserAccountDataTest(testClient.getAddress());
                    BigInteger feeUSD = toBigInt((String) iusdcUserAccountData_before.get("totalFeesUSD"));


                    float delta = (ICX.divide(BigInteger.valueOf(1000))).floatValue();

//                    System.out.println("float " + delta); // delta is the difference between two and it should be 0.01
                    //TODO:
//                    assertEquals((toBigInt((String) iusdcUserAccountData_before.get("availableBorrowsUSD"))
//                                    .add(reapy_AmountUSD.subtract(feeUSD))).longValue(),
//                            (toBigInt((String) iusdcUserAccountData_after.get("availableBorrowsUSD"))).longValue(),delta);


                    // totalBorrowBalanceUSD decrease
                    assertTrue((toBigInt((String) iusdcUserAccountData_before.get("totalBorrowBalanceUSD"))).compareTo(
                            toBigInt((String) iusdcUserAccountData_after.get("totalBorrowBalanceUSD")))>0);


                    // healthFactor increase
                    assertTrue(toBigInt((String) iusdcUserAccountData_after.get("healthFactor")).
                            compareTo(toBigInt((String)iusdcUserAccountData_before.get("healthFactor")))>0);

                    // totalCollateralBalanceUSD remains almost same
                    assertEquals((toBigInt((String) iusdcUserAccountData_after.get("totalCollateralBalanceUSD"))).longValue(),
                            toBigInt((String) iusdcUserAccountData_before.get("totalCollateralBalanceUSD")).longValue(),delta);

//                    System.out.println(user_iusdc_reserve_data_after);
                    // this should change
                    assertEquals(user_iusdc_reserve_data.get("userLiquidityCumulativeIndex"),
                            user_iusdc_reserve_data_after.get("userLiquidityCumulativeIndex"));


                }

                @DisplayName("redeem Transactions")
                @Nested
                @TestMethodOrder(OrderAnnotation.class)
                class redeem{

                    @Test
                    @DisplayName("redeem from icx")
                    @Order(7)
                    void redeem_icx(){

                        Address oICX = addressMap.get(Contracts.oICX.getKey());
                        Map<String,Object> reserveData = getReserveData(icx_reserve);

                        Map<String,BigInteger> userReserveDataBefore = userReserveDataTest(icx_reserve,testClient.getAddress());
                        System.out.println(userReserveDataBefore);
                        System.out.println(reserveData);
                        System.out.println(testClient.sICX.balanceOf(testClient.getAddress()));

                        Map<String,Object> icxUserAccountData_before = getUserAccountDataTest(testClient.getAddress());
                        System.out.println(icxUserAccountData_before);

                        BigInteger icxBalance_before = testClient.sICX.balanceOf(testClient.getAddress());


                        BigInteger redeem_amount = BigInteger.valueOf(4).multiply(ICX);
                        BigInteger redeem_amount_USD = exaMultiply(redeem_amount,BigInteger.valueOf(3).multiply(ICX).divide(BigInteger.TEN));
                        testClient.lendingPool.redeem(oICX,redeem_amount,false);

                        Map<String,Object> icxReserveDataAfter = getReserveData(icx_reserve);
                        Map<String,BigInteger> userReserveDataAfter = userReserveDataTest(icx_reserve,testClient.getAddress());
                        System.out.println(userReserveDataAfter);
                        System.out.println(icxReserveDataAfter);

                        Map<String,Object> icxUserAccountData_after = getUserAccountDataTest(testClient.getAddress());
//                        System.out.println(icxUserAccountData_after);

                        // Liquidity should decrease
                        BigInteger availableLiquidity_before = toBigInt((String) reserveData.get("availableLiquidity"));
                        BigInteger availableLiquidity_after = toBigInt((String)icxReserveDataAfter.get("availableLiquidity"));
                        assertEquals(availableLiquidity_before.subtract(redeem_amount),availableLiquidity_after);

                        // principal oToken should decrease
                        BigInteger principleOtoken_before =userReserveDataBefore.get("principalOTokenBalance");
                        BigInteger principleOtoken_after =userReserveDataAfter.get("principalOTokenBalance");
                        float delta = (ICX.divide(BigInteger.valueOf(1000))).floatValue();
                        System.out.println(delta);
                        assertEquals(principleOtoken_before.subtract(redeem_amount).floatValue(),
                                principleOtoken_after.floatValue(),delta);

                        // user liquidity cumulative index should increase
                        BigInteger userLiquidityCumulativeIndex_before = userReserveDataBefore.get("userLiquidityCumulativeIndex");
                        BigInteger userLiquidityCumulativeIndex_after = userReserveDataAfter.get("userLiquidityCumulativeIndex");
                        assertTrue(userLiquidityCumulativeIndex_after.compareTo(userLiquidityCumulativeIndex_before)>0);

                        // collateral should decrease
                        BigInteger collateralBalance_before = toBigInt((String)icxUserAccountData_before.get("totalCollateralBalanceUSD"));
                        BigInteger collateralBalance_after = toBigInt((String)icxUserAccountData_after.get("totalCollateralBalanceUSD"));
                        assertEquals(collateralBalance_before.subtract(redeem_amount_USD).floatValue(),collateralBalance_after.floatValue(),delta);

                        // health factor should decrease
                        assertTrue(toBigInt((String) icxUserAccountData_before.get("healthFactor")).
                                compareTo(toBigInt((String)icxUserAccountData_after.get("healthFactor")))>0);

                        // sicx balance should increase

                        BigInteger icxBalance_after = testClient.sICX.balanceOf(testClient.getAddress());
                        System.out.println(icxBalance_before);
                        assertEquals(icxBalance_before.add(redeem_amount),icxBalance_after);


                    }

                }
            }

        }


    }

    private Map<String,BigInteger> getRates(BigInteger totalBorrow, BigInteger totalLiquidity, Address reserve){
        BigInteger utilizationRate = exaDivide(totalBorrow,totalLiquidity);

        Map<String, Object> constant= getReserveConfiguration(reserve);
        BigInteger optimal_rate = toBigInt((String) constant.get("optimalUtilizationRate"));
        BigInteger slope_rate_1 = toBigInt((String) constant.get("slopeRate1"));
        BigInteger slope_rate_2 = toBigInt((String) constant.get("slopeRate2"));
        BigInteger base_borrow = toBigInt((String) constant.get("baseBorrowRate"));
        BigInteger borrowRate;

        if (utilizationRate.compareTo(optimal_rate) < 0){
            borrowRate = base_borrow.add(exaMultiply(
                    exaDivide(utilizationRate,optimal_rate),slope_rate_1));
        }
        else {
            borrowRate = base_borrow.add(slope_rate_1).add(exaMultiply(
                    exaDivide((utilizationRate.subtract(optimal_rate)), (ICX.subtract(optimal_rate))),
                            slope_rate_2)
            );
        }
        BigInteger liquidityRate = exaMultiply(exaMultiply(borrowRate,utilizationRate),
                BigInteger.valueOf(9).multiply(ICX).divide(BigInteger.TEN));

        return Map.of("borrowRate",borrowRate,
                "liquidityRate",liquidityRate);
    }

    private Map<String, Object> getReserveConfiguration(Address reserve){
        return ommClient.lendingPoolCore.getReserveConstants(reserve);
    }



    private Map<String,Object> getReserveData(score.Address reserve){
        return ommClient.lendingPoolDataProvider.getReserveData(reserve);
    }

    private Map<String,BigInteger> userReserveDataTest(score.Address reserve,score.Address user){
        return ommClient.lendingPoolDataProvider.getUserReserveData(reserve,user);
    }



    private Map<String, Object> reserveUnitPrice(Address reserve){
        return ommClient.lendingPoolCore.getReserveConfiguration(reserve);
    }

    private BigInteger toBigInt(String inputString) {
        return new BigInteger(inputString.substring(2), 16);
    }

    private Map<String,Object> getUserAccountDataTest(score.Address address){
        return ommClient.lendingPoolDataProvider.getUserAccountData(address);
    }

    private BigInteger getTodayRateInternal(){
        return ommClient.staking.getTodayRate();
    }

    private void reserveDecimals(){
        // check collateral enable or not
        // reserve check
        // current ltv
    }

    private BigInteger getReservePrice(String symbol){
        return ommClient.dummyPriceOracle.get_reference_data(symbol,"USD");
    }


    private Map<String,BigInteger> getReserveAccountDataTest(){
        return ommClient.lendingPoolDataProvider.getReserveAccountData();
    }

    private byte[] createByteArray(String methodName, BigInteger value,
                                   @Optional score.Address collateral, @Optional score.Address reserve, @Optional score.Address user) {

        JsonObject internalParameters = new JsonObject()
                .add("amount",String.valueOf(value))
                .add("_collateral",String.valueOf(collateral))
                .add("_reserve",String.valueOf(reserve))
                .add("_user",String.valueOf(user));


        JsonObject jsonData = new JsonObject()
                .add("method", methodName)
                .add("params", internalParameters);

        return jsonData.toString().getBytes();

    }

    private static void mintToken(){
        BigInteger amount = BigInteger.valueOf(100_000_000_000L).multiply(ICX);
        ommClient.iUSDC.addIssuer(ommClient.getAddress());
        ommClient.iUSDC.approve(ommClient.getAddress(),amount);
        ommClient.iUSDC.mintTo(ommClient.getAddress(),amount);
    }



}
