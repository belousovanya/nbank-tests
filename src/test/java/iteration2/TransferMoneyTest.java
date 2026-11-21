package iteration2;

import common.AccountLimits;
import common.BaseTest;
import generators.RandomData;
import io.restassured.specification.RequestSpecification;
import models.AccountResponse;
import models.CreateUserRequest;
import models.TransferRequest;
import models.TransferResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import requests.skelethon.Endpoint;
import requests.skelethon.ValidatedRequests;
import requests.skelethon.requesters.CrudRequester;
import requests.steps.AdminSteps;
import requests.steps.UserSteps;
import specs.RequestSpecs;
import specs.ResponseSpecs;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

public class TransferMoneyTest extends BaseTest {
    private RequestSpecification senderUserSpec;
    private UserSteps senderSteps;
    private int senderAccountId;

    private UserSteps receiverSteps;
    private int receiverAccountId;

    @BeforeEach
    public void prepareUserAndAccounts() {
        CreateUserRequest sender = AdminSteps.createUser();
        senderUserSpec = RequestSpecs.authAsUser(sender.getUsername(), sender.getPassword());
        senderSteps = new UserSteps(senderUserSpec);
        senderAccountId = senderSteps.createAccount().getId();

        CreateUserRequest receiver = AdminSteps.createUser();
        receiverSteps = new UserSteps(RequestSpecs.authAsUser(receiver.getUsername(), receiver.getPassword()));
        receiverAccountId = receiverSteps.createAccount().getId();
    }

    @Test
    public void userCanTransferValidAmountToAnotherUsersAccount() {
        BigDecimal initialBalance = RandomData.getAmount(AccountLimits.MIN_AMOUNT, AccountLimits.MAX_DEPOSIT);
        BigDecimal transferAmount = RandomData.getAmount(AccountLimits.MIN_AMOUNT, initialBalance);

        senderSteps.deposit(senderAccountId, initialBalance);

        new ValidatedRequests(senderUserSpec)
                .<TransferResponse>request(Endpoint.TRANSFER)
                .post(transferRequest(receiverAccountId, transferAmount));

        softly.assertThat(balanceOf(senderSteps)).isEqualByComparingTo(initialBalance.subtract(transferAmount));
        softly.assertThat(balanceOf(receiverSteps)).isEqualByComparingTo(transferAmount);
    }

    @ParameterizedTest(name = "[{index}] Граничная сумма перевода - {0}")
    @ValueSource(strings = {"0.01", "9999.99", "10000.00"})
    public void userCanTransferBoundaryAmount(BigDecimal transferAmount) {
        depositInChunks(transferAmount);

        assertThat(balanceOf(senderSteps)).isEqualByComparingTo(transferAmount);

        new ValidatedRequests(senderUserSpec)
                .<TransferResponse>request(Endpoint.TRANSFER)
                .post(transferRequest(receiverAccountId, transferAmount));

        softly.assertThat(balanceOf(senderSteps)).isEqualByComparingTo(BigDecimal.ZERO);
        softly.assertThat(balanceOf(receiverSteps)).isEqualByComparingTo(transferAmount);
    }

    @ParameterizedTest(name = "[{index}] Недопустимая сумма перевода - {0}")
    @ValueSource(strings = {"0", "-0.01", "10000.01"})
    public void userCannotTransferInvalidAmount(BigDecimal transferAmount) {
        BigDecimal initialBalance = transferAmount.max(
                RandomData.getAmount(AccountLimits.MIN_AMOUNT, AccountLimits.MAX_DEPOSIT));
        depositInChunks(initialBalance);

        assertThat(balanceOf(senderSteps)).isEqualByComparingTo(initialBalance);

        new CrudRequester(senderUserSpec, Endpoint.TRANSFER, ResponseSpecs.requestReturnsBadRequest())
                .post(transferRequest(receiverAccountId, transferAmount));

        softly.assertThat(balanceOf(senderSteps)).isEqualByComparingTo(initialBalance);
        softly.assertThat(balanceOf(receiverSteps)).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    public void userCannotTransferMoreThanAvailableBalance() {
        BigDecimal initialBalance = RandomData.getAmount(AccountLimits.MIN_AMOUNT, AccountLimits.MAX_DEPOSIT);

        BigDecimal transferAmount = initialBalance.add(AccountLimits.MIN_AMOUNT);

        senderSteps.deposit(senderAccountId, initialBalance);

        new CrudRequester(senderUserSpec, Endpoint.TRANSFER, ResponseSpecs.requestReturnsBadRequest())
                .post(transferRequest(receiverAccountId, transferAmount));

        softly.assertThat(balanceOf(senderSteps)).isEqualByComparingTo(initialBalance);
        softly.assertThat(balanceOf(receiverSteps)).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    public void userCanTransferBetweenOwnAccounts() {
        int secondOwnAccountId = senderSteps.createAccount().getId();

        BigDecimal initialBalance = RandomData.getAmount(AccountLimits.MIN_AMOUNT, AccountLimits.MAX_DEPOSIT);
        BigDecimal transferAmount = RandomData.getAmount(AccountLimits.MIN_AMOUNT, initialBalance);

        senderSteps.deposit(senderAccountId, initialBalance);

        new ValidatedRequests(senderUserSpec)
                .<TransferResponse>request(Endpoint.TRANSFER)
                .post(transferRequest(secondOwnAccountId, transferAmount));

        Map<Integer, BigDecimal> balancesByAccountId = senderSteps.getAccounts().stream()
                .collect(Collectors.toMap(AccountResponse::getId, AccountResponse::getBalance));

        softly.assertThat(balancesByAccountId.get(senderAccountId).setScale(AccountLimits.MONEY_SCALE, RoundingMode.HALF_UP))
                .isEqualByComparingTo(initialBalance.subtract(transferAmount));
        softly.assertThat(balancesByAccountId.get(secondOwnAccountId).setScale(AccountLimits.MONEY_SCALE, RoundingMode.HALF_UP))
                .isEqualByComparingTo(transferAmount);
    }

    @Test
    public void userCanTransferEntireBalance() {
        BigDecimal initialBalance = RandomData.getAmount(AccountLimits.MIN_AMOUNT, AccountLimits.MAX_DEPOSIT);

        senderSteps.deposit(senderAccountId, initialBalance);

        new ValidatedRequests(senderUserSpec)
                .<TransferResponse>request(Endpoint.TRANSFER)
                .post(transferRequest(receiverAccountId, initialBalance));

        softly.assertThat(balanceOf(senderSteps)).isEqualByComparingTo(BigDecimal.ZERO);
        softly.assertThat(balanceOf(receiverSteps)).isEqualByComparingTo(initialBalance);
    }

    private TransferRequest transferRequest(int receiverId, BigDecimal amount) {
        return TransferRequest.builder()
                .senderAccountId(senderAccountId)
                .receiverAccountId(receiverId)
                .amount(amount)
                .build();
    }

    // За один депозит можно положить не больше MAX_DEPOSIT, поэтому пополняем частями
    private void depositInChunks(BigDecimal total) {
        BigDecimal remaining = total;
        while (remaining.signum() > 0) {
            BigDecimal chunk = remaining.min(AccountLimits.MAX_DEPOSIT);
            senderSteps.deposit(senderAccountId, chunk);
            remaining = remaining.subtract(chunk);
        }
    }

    private BigDecimal balanceOf(UserSteps steps) {
        return steps.getAccounts().get(0).getBalance().setScale(AccountLimits.MONEY_SCALE, RoundingMode.HALF_UP);
    }
}
