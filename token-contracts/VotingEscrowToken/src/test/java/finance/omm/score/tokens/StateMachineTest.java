/*
 * Copyright (c) 2021-2021 Balanced.network.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package finance.omm.score.tokens;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.spy;

import com.iconloop.score.test.Account;
import com.iconloop.score.test.Score;
import com.iconloop.score.test.ServiceManager;
import com.iconloop.score.test.TestBase;
import com.iconloop.score.token.irc2.IRC2Mintable;
import finance.omm.libs.address.Contracts;
import finance.omm.libs.test.VarargAnyMatcher;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import org.json.JSONObject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.mockito.ArgumentMatchers;

@DisplayName("Statemachine Tests")
public class StateMachineTest extends TestBase {

    private static final Long WEEK = 7 * 86400L * 1000000L;
    private static final Long MAX_TIME = 4 * 365 * 86400L * 1000000L;
    private static final BigInteger MINT_AMOUNT = BigInteger.TEN.pow(40);
    private static final ServiceManager sm = getServiceManager();
    private static final Account owner = sm.createAccount();

    private Account addressProvider = Account.newScoreAccount(1001);

    private final ArrayList<Account> accounts = new ArrayList<>();
    private final long MAXIMUM_LOCK_WEEKS = 208;
    private final long BLOCK_TIME = 2 * 1000000;

    private Score bOmmScore;
    private Score tokenScore;

    private BoostedOMM scoreSpy;

    public static class OmmToken extends IRC2Mintable {

        public OmmToken(String _name, String _symbol, int _decimals) {
            super(_name, _symbol, _decimals);
        }
    }

    private static class VotingBalance {

        public BigInteger value;
        public BigInteger unlockTime;

        public VotingBalance(BigInteger value, BigInteger unlockTime) {
            this.value = value;
            this.unlockTime = unlockTime;
        }

        public VotingBalance() {
            this.value = BigInteger.ZERO;
            this.unlockTime = BigInteger.ZERO;
        }
    }

    private final Map<Account, BigInteger> tokenBalances = new HashMap<>();
    private final Map<Account, VotingBalance> votingBalances = new HashMap<>();

    private void setupAccounts() {
        int numberOfAccounts = 10;
        for (int accountNumber = 0; accountNumber < numberOfAccounts; accountNumber++) {
            Account account = sm.createAccount();
            accounts.add(account);
            tokenScore.invoke(owner, "mintTo", account.getAddress(), MINT_AMOUNT);
            tokenBalances.put(account, MINT_AMOUNT);
            votingBalances.put(account, new VotingBalance());
        }
    }

    @BeforeEach
    public void setup() throws Exception {
        tokenScore = sm.deploy(owner, OmmToken.class, "OMM Token", "OMM", 18);
        bOmmScore = sm.deploy(owner, BoostedOMM.class, addressProvider.getAddress(), tokenScore.getAddress(),
                "Boosted Omm", "bOMM");
        scoreSpy = (BoostedOMM) spy(bOmmScore.getInstance());
        bOmmScore.setInstance(scoreSpy);

        bOmmScore.invoke(owner, "setMinimumLockingAmount", ICX);
        setupAccounts();
    }

    public byte[] tokenData(String method, Map<String, Object> params) {
        Map<String, Object> map = new HashMap<>();
        map.put("method", method);
        map.put("params", params);
        JSONObject data = new JSONObject(map);
        return data.toString().getBytes();
    }

    public long addWeeksToCurrentTimestamp(long numberOfWeeks) {
        return ((sm.getBlock().getTimestamp() + numberOfWeeks * WEEK) / WEEK) * WEEK;
    }

    public void expectErrorMessage(Executable contractCall, String errorMessage) {
        AssertionError e = Assertions.assertThrows(AssertionError.class, contractCall);
        assertEquals(errorMessage, e.getMessage());
    }

    public void createLock(Account account, BigInteger value, long unlockTime) {
        byte[] createLockParams = tokenData("createLock", Map.of("unlockTime", unlockTime));
        VarargAnyMatcher<Object> matcher = new VarargAnyMatcher<>();

        doNothing().when(scoreSpy)
                .scoreCall(eq(Contracts.DELEGATION), eq("updateDelegations"),
                        ArgumentMatchers.<Object>argThat(matcher));
        doNothing().when(scoreSpy)
                .scoreCall(eq(Contracts.REWARDS), eq("handleAction"), ArgumentMatchers.<Object>argThat(matcher));

        VotingBalance vote = votingBalances.getOrDefault(account, new VotingBalance());
        vote.value = vote.value.add(value);
        try {
            tokenScore.invoke(account, "transfer", bOmmScore.getAddress(), value, createLockParams);
        } catch (AssertionError e) {
            votingBalances.put(account, vote);
            throw e;
        }

        vote.unlockTime = BigInteger.valueOf(unlockTime);
        votingBalances.put(account, vote);
    }

    public void increaseAmount(Account account, BigInteger value) {
        byte[] increaseAmountParams = tokenData("increaseAmount", Map.of());
        VotingBalance vote = votingBalances.getOrDefault(account, new VotingBalance());
        vote.value = vote.value.add(value);
        votingBalances.put(account, vote);

        tokenScore.invoke(account, "transfer", bOmmScore.getAddress(), value, increaseAmountParams);
    }

    public void increaseUnlockTime(Account account, BigInteger unlockTime) {
        bOmmScore.invoke(account, "increaseUnlockTime", unlockTime);

        //Only if not AssertionError is NOT thrown
        VotingBalance vote = votingBalances.getOrDefault(account, new VotingBalance());
        vote.unlockTime = unlockTime;
        votingBalances.put(account, vote);
    }

    @DisplayName("Create Lock with")
    @Nested
    class CreateLockTests {

        private BigInteger value;
        private long unlockTime;
        private long lockDuration;

        @BeforeEach
        void configuration() {
            value = BigInteger.TEN.pow(20);
            this.lockDuration = 5;
            unlockTime = addWeeksToCurrentTimestamp(this.lockDuration);
        }

        @DisplayName("zero locked amount")
        @Test
        void createLockTest() {
            Account account = accounts.get(0);
            Executable createLockCall = () -> createLock(account, BigInteger.ZERO, unlockTime);

            String expectedErrorMessage = "Token Fallback: Token value should be a positive number";
            expectErrorMessage(createLockCall, expectedErrorMessage);
        }

        @DisplayName("balanceOf for future")
        @Test
        void invalidBalanceOf() {
            Account account = accounts.get(0);
            createLock(account, value, unlockTime);

            long deltaBlock = (addWeeksToCurrentTimestamp(lockDuration) - sm.getBlock()
                    .getTimestamp()) / BLOCK_TIME + 1;
            sm.getBlock().increase(deltaBlock);
            Executable balanceOf = () -> bOmmScore.call("balanceOf", accounts.get(0)
                            .getAddress(),
                    BigInteger.valueOf(deltaBlock + 1));

            String expectedErrorMessage = "subtraction underflow for unsigned numbers";
            expectErrorMessage(balanceOf, expectedErrorMessage);
        }


        @DisplayName("unlock time less than current time")
        @Test
        void unlockTimeLessThanCurrentTime() {
            Account account = accounts.get(0);
            final Long unlockTimeLessThanBlockTime = addWeeksToCurrentTimestamp(-2);

            Executable createLock = () -> createLock(account, value, unlockTimeLessThanBlockTime);

            String expectedErrorMessage = "Create Lock: Can only lock until time in the future";
            expectErrorMessage(createLock, expectedErrorMessage);
        }

        @DisplayName("unlock time greater than max unlock time")
        @Test
        void lockGreaterThanMaxTime() {
            Account account = accounts.get(1);
            final long unlockTimeGreaterThanMaxTime = addWeeksToCurrentTimestamp(MAXIMUM_LOCK_WEEKS + 1);

            Executable createLock = () -> createLock(account, value, unlockTimeGreaterThanMaxTime);

            String expectedErrorMessage = "Create Lock: Voting Lock can be 4 years max";
            expectErrorMessage(createLock, expectedErrorMessage);
        }

        @DisplayName("existing lock")
        @Test
        void lockWithExistingLock() {
            Account account = accounts.get(1);
            createLock(account, value, unlockTime);

            Executable createSecondLock = () -> createLock(account, value, unlockTime);

            String expectedErrorMessage = "Create Lock: Withdraw old tokens first";
            expectErrorMessage(createSecondLock, expectedErrorMessage);
        }

        @DisplayName("Less than minimum amount test")
        @Test
        void lessThanMinimumAmount() {
            //Minimum amount to lock is MAX_TIME. If less than minimum amount is provided, balance is zero but the
            // transaction is not reverted.
            Account account = accounts.get(1);
            BigInteger valueLessThanMinimum = ICX.subtract(BigInteger.ONE);

            Executable increaseAmount = () -> createLock(account, valueLessThanMinimum, unlockTime);

            String expectedErrorMessage = "required minimum 1 OMM for locking";
            expectErrorMessage(increaseAmount, expectedErrorMessage);

            BigInteger balance = (BigInteger) bOmmScore.call("balanceOf", account.getAddress(), BigInteger.ZERO);
            assertEquals(BigInteger.ZERO, balance);
        }

        @DisplayName("Minimum amount test")
        @Test
        void minimumAmount() {
            Account account = accounts.get(2);
            BigInteger valueMinimum = ICX;
            createLock(account, valueMinimum, unlockTime);

            assert (((BigInteger) bOmmScore.call("balanceOf", account.getAddress(), BigInteger.ZERO)).compareTo(
                    BigInteger.ZERO) > 0);
        }

        @DisplayName("Locked balance deducted from user's account")
        @Test
        void multipleLocks() {
            for (Account account : accounts) {
                createLock(account, value, unlockTime);
            }
        }


    }

    @DisplayName("Increase Amount")
    @Nested
    class IncreaseAmountTests {

        private BigInteger value;
        private final long lockedWeeks = 5;

        @DisplayName("Create Lock of 1000 OMM tokens from account 0")
        @BeforeEach
        void setupLock() {
            Account account = accounts.get(0);
            long unlockTime = addWeeksToCurrentTimestamp(lockedWeeks);
            value = BigInteger.TEN.pow(21);

            createLock(account, value, unlockTime);
        }

        @DisplayName("with zero value")
        @Test
        void increaseAmountWithZeroValue() {
            Executable increaseAmount = () -> increaseAmount(accounts.get(0), BigInteger.ZERO);

            String expectedErrorMessage = "Token Fallback: Token value should be a positive number";
            expectErrorMessage(increaseAmount, expectedErrorMessage);
        }

        @DisplayName("for non existing lock in account 1")
        @Test
        void increaseAmountForNonExistingLock() {
            Executable increaseAmount = () -> increaseAmount(accounts.get(1), value);

            String expectedErrorMessage = "Increase amount: No existing lock found";
            expectErrorMessage(increaseAmount, expectedErrorMessage);
        }

        @DisplayName("to an expired lock")
        @Test
        void increaseAmountToExpiredLock() {
            long deltaBlock = (addWeeksToCurrentTimestamp(lockedWeeks) - sm.getBlock().getTimestamp()) / BLOCK_TIME + 1;
            sm.getBlock().increase(deltaBlock);
            // Check if the lock time has expired
            assertEquals(BigInteger.ZERO, bOmmScore.call("balanceOf", accounts.get(0).getAddress(), BigInteger.ZERO));

            Executable increaseAmount = () -> increaseAmount(accounts.get(0), value);

            String expectedErrorMessage = "Increase amount: Cannot add to expired lock.";
            expectErrorMessage(increaseAmount, expectedErrorMessage);
        }

        @DisplayName("with valid data")
        @Test
        void increaseAmountWithValidData() {
            increaseAmount(accounts.get(0), value);
        }
    }

    @DisplayName("Increase Unlock time ")
    @Nested
    class IncreaseUnlockTimeTests {

        private long unlockTime;
        private final long lockedWeeks = 5;

        @DisplayName("Create Lock of 1000 OMM tokens from account 0")
        @BeforeEach
        void setupLock() {
            unlockTime = addWeeksToCurrentTimestamp(lockedWeeks);
            BigInteger value = BigInteger.TEN.pow(21);
            createLock(accounts.get(0), value, unlockTime);
        }

        @DisplayName("of non existing lock account")
        @Test
        void increaseUnlockTimeNonExisting() {
            Executable increaseUnlockTime = () -> increaseUnlockTime(accounts.get(2), BigInteger.valueOf(unlockTime));

            String expectedErrorMessage = "Increase unlock time: Nothing is locked";
            expectErrorMessage(increaseUnlockTime, expectedErrorMessage);
        }

        @DisplayName("of expired lock")
        @Test
        void increaseUnlockTimeExpiredLock() {
            long deltaBlock = (addWeeksToCurrentTimestamp(lockedWeeks) - sm.getBlock().getTimestamp()) / BLOCK_TIME + 1;
            sm.getBlock().increase(deltaBlock);
            // Check if the lock time has expired
            assertEquals(BigInteger.ZERO, bOmmScore.call("balanceOf", accounts.get(0).getAddress(), BigInteger.ZERO));

            //Update unlock time
            unlockTime = addWeeksToCurrentTimestamp(5);
            Executable increaseUnlockTime = () -> increaseUnlockTime(accounts.get(0), BigInteger.valueOf(unlockTime));

            String expectedErrorMessage = "Increase unlock time: Lock expired";
            expectErrorMessage(increaseUnlockTime, expectedErrorMessage);
        }

        @DisplayName("with unlock time less than the current unlock time")
        @Test
        void decreaseUnlockTime() {
            Map<String, BigInteger> locked = (Map<String, BigInteger>) bOmmScore.call("getLocked", accounts.get(0)
                    .getAddress());
            final BigInteger unlockTime = locked.get("end").subtract(BigInteger.valueOf(2 * WEEK));

            Executable increaseUnlockTime = () -> increaseUnlockTime(accounts.get(0), unlockTime);

            String expectedErrorMessage = "Increase unlock time: Can only increase lock duration";
            expectErrorMessage(increaseUnlockTime, expectedErrorMessage);
        }

        @DisplayName("with unlock time more than max time")
        @Test
        void increaseUnlockTimeGreaterThanMaxTime() {
            final long unlockTime = addWeeksToCurrentTimestamp(MAXIMUM_LOCK_WEEKS + 1);

            Executable increaseUnlockTime = () -> increaseUnlockTime(accounts.get(0), BigInteger.valueOf(unlockTime));

            String expectedErrorMessage = "Increase unlock time: Voting lock can be 4 years max";
            expectErrorMessage(increaseUnlockTime, expectedErrorMessage);
        }

        @DisplayName("from contract")
        @Test
        void increaseUnlockFromContract() {
            Account account = Account.getAccount(Account.newScoreAccount(500).getAddress());
            Executable increaseUnlockTime = () -> increaseUnlockTime(account, BigInteger.valueOf(unlockTime));

            String expectedErrorMessage = "Assert Not contract: Smart contract depositors not allowed";
            expectErrorMessage(increaseUnlockTime, expectedErrorMessage);

        }

        @DisplayName("with valid data")
        @Test
        void increaseUnlockWithValidData() {
            long increasedUnlockTime = addWeeksToCurrentTimestamp(10);
            increaseUnlockTime(accounts.get(0), BigInteger.valueOf(increasedUnlockTime));
        }
    }

    @DisplayName("Withdraw tokens from the voting escrow")
    @Nested
    class WithdrawLockTests {

        private final long lockedWeeks = 5;

        @DisplayName("Create Lock of 1000 OMM tokens from account 0")
        @BeforeEach
        void setupLock() {
            long unlockTime = addWeeksToCurrentTimestamp(lockedWeeks);
            BigInteger value = BigInteger.TEN.pow(21);
            createLock(accounts.get(0), value, unlockTime);
        }

        @DisplayName("before unlock expires")
        @Test
        void unlockBeforeExpiry() {
            Executable withdraw = () -> bOmmScore.invoke(accounts.get(0), "withdraw");

            String expectedErrorMessage = "Withdraw: The lock didn't expire";
            expectErrorMessage(withdraw, expectedErrorMessage);
        }

        @DisplayName("after the expiry")
        @Test
        void unlockAfterExpiry() {
            long deltaBlock = (addWeeksToCurrentTimestamp(lockedWeeks) - sm.getBlock().getTimestamp()) / BLOCK_TIME + 1;
            sm.getBlock().increase(deltaBlock);
            // Check if the lock time has expired
            assertEquals(BigInteger.ZERO, bOmmScore.call("balanceOf", accounts.get(0).getAddress(), BigInteger.ZERO));

            bOmmScore.invoke(accounts.get(0), "withdraw");
            assertEquals(MINT_AMOUNT, tokenScore.call("balanceOf", accounts.get(0).getAddress()));
            votingBalances.put(accounts.get(0), new VotingBalance());
        }
    }

    @DisplayName("Checkpoint")
    @Test
    void checkpoint() {
        for (Account account : accounts) {
            bOmmScore.invoke(account, "checkpoint");
        }
    }

    @DisplayName("Advance Clock")
    @Test
    void advanceClock() {
        long sleepDuration = 40;
        sm.getBlock().increase(sleepDuration);
    }

    @DisplayName("Verify token balances")
    @AfterEach
    void verifyTokenBalances() {
        for (Account account : accounts) {
            assertEquals(tokenScore.call("balanceOf", account.getAddress()),
                    MINT_AMOUNT.subtract(votingBalances.get(account).value));
        }
    }

    @DisplayName("Verify individual balance against total supply")
    @AfterEach
    void verifyTotalSupply() {
        // Verify the sum of all escrow balances is equal to the escrow totalSupply
        BigInteger currentTime = BigInteger.valueOf(sm.getBlock().getTimestamp());
        BigInteger totalSupply = BigInteger.ZERO;
        for (Account account : accounts) {
            VotingBalance vote = votingBalances.getOrDefault(account, new VotingBalance());
            BigInteger balance = (BigInteger) bOmmScore.call("balanceOf", account.getAddress(), BigInteger.ZERO);
            totalSupply = totalSupply.add(balance);

            if (vote.unlockTime.compareTo(currentTime) > 0 && vote.value.divide(BigInteger.valueOf(MAX_TIME))
                    .compareTo(BigInteger.ZERO) > 0) {
                assert (balance.compareTo(BigInteger.ZERO) > 0);
            } else {
                assert vote.value.compareTo(BigInteger.ZERO) <= 0 && vote.unlockTime.compareTo(currentTime) > 0 || (
                        balance.compareTo(BigInteger.ZERO) == 0);
            }
        }

        assertEquals(bOmmScore.call("totalSupply", BigInteger.ZERO), totalSupply);
    }

    @DisplayName("Verify balanceOfAt against totalSupplyAt")
    @AfterEach
    void verifyTotalSupplyAt() {
        // Verify that total balances in account is same as total supply in previous block

        sm.getBlock().increase(16);
        BigInteger totalSupplyAtIncreasedBlock = BigInteger.ZERO;
        BigInteger lastBlock = BigInteger.valueOf(sm.getBlock().getHeight() - 4);
        BigInteger balance;
        for (Account account : accounts) {
            balance = (BigInteger) bOmmScore.call("balanceOfAt", account.getAddress(), lastBlock);
            totalSupplyAtIncreasedBlock = totalSupplyAtIncreasedBlock.add(balance);
        }

        BigInteger totalSupply = (BigInteger) bOmmScore.call("totalSupplyAt", lastBlock);
        assertEquals(totalSupply, totalSupplyAtIncreasedBlock);
    }

}
