package iteration2;

import common.AccountLimits;
import common.BaseTest;
import generators.RandomData;
import io.restassured.specification.RequestSpecification;
import models.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import requests.*;
import specs.RequestSpecs;
import specs.ResponseSpecs;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Arrays;
import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

public class TransferMoneyTest extends BaseTest {

    private RequestSpecification senderUserSpec;
    private int senderAccountId;

    private RequestSpecification receiverUserSpec;
    private int receiverAccountId;

    @BeforeEach
    public void prepareUserAndAccounts() {
        // 1. Создаем отправителя под админом
        CreateUserRequest senderUserRequest = CreateUserRequest.builder()
                .username(RandomData.getUsername())
                .password(RandomData.getPassword())
                .role(UserRole.USER.name())
                .build();

        new AdminCreateUserRequester(RequestSpecs.adminSpec(), ResponseSpecs.entityWasCreated())
                .post(senderUserRequest);

        // 2. Авторизуемся под отправителем
        senderUserSpec = RequestSpecs.authAsUser(
                senderUserRequest.getUsername(), senderUserRequest.getPassword());

        // 3. Создаем его счёт и сохраняем ID из ответа
        senderAccountId = new CreateAccountRequester(senderUserSpec, ResponseSpecs.entityWasCreated())
                .post()
                .extract()
                .as(AccountResponse.class)
                .getId();

        // 4. Создаем получателя под админом
        CreateUserRequest receiverUserRequest = CreateUserRequest.builder()
                .username(RandomData.getUsername())
                .password(RandomData.getPassword())
                .role(UserRole.USER.name())
                .build();

        new AdminCreateUserRequester(RequestSpecs.adminSpec(), ResponseSpecs.entityWasCreated())
                .post(receiverUserRequest);

        // 5. Авторизуемся под получателем
        receiverUserSpec = RequestSpecs.authAsUser(
                receiverUserRequest.getUsername(), receiverUserRequest.getPassword());

        // 6. Создаем его счёт и сохраняем ID из ответа
        receiverAccountId = new CreateAccountRequester(receiverUserSpec, ResponseSpecs.entityWasCreated())
                .post()
                .extract()
                .as(AccountResponse.class).getId();
    }

    @Test
    public void userCanTransferValidAmountToAnotherUsersAccount() {
        BigDecimal initialBalance = RandomData.getAmount(AccountLimits.MIN_AMOUNT, AccountLimits.MAX_DEPOSIT);
        BigDecimal transferAmount = RandomData.getAmount(AccountLimits.MIN_AMOUNT, initialBalance);

        new DepositeRequester(senderUserSpec, ResponseSpecs.requestReturnsOK())
                .post(DepositeRequest.builder()
                        .id(senderAccountId)
                        .balance(initialBalance)
                        .build());

        TransferRequest transferRequest = TransferRequest.builder()
                .senderAccountId(senderAccountId)
                .receiverAccountId(receiverAccountId)
                .amount(transferAmount)
                .build();

        new TransferRequester(senderUserSpec, ResponseSpecs.requestReturnsOK())
                .post(transferRequest);

        AccountResponse[] senderAccounts = new GetAccountsRequester(senderUserSpec)
                .get()
                .extract()
                .as(AccountResponse[].class);

        AccountResponse[] receiverAccounts = new GetAccountsRequester(receiverUserSpec)
                .get()
                .extract()
                .as(AccountResponse[].class);

        // Бэкенд хранит баланс как double (например, 191.43999999999983), поэтому округляем до копеек
        softly.assertThat(senderAccounts[0].getBalance().setScale(2, RoundingMode.HALF_UP))
                .isEqualByComparingTo(initialBalance.subtract(transferAmount));
        softly.assertThat(receiverAccounts[0].getBalance().setScale(2, RoundingMode.HALF_UP))
                .isEqualByComparingTo(transferAmount);
    }

    @ParameterizedTest(name = "[{index}] Граничная сумма перевода - {0}")
    @ValueSource(strings = {"0.01", "9999.99", "10000.00"})
    public void userCanTransferBoundaryAmount(BigDecimal transferAmount) {
        // За один депозит можно положить не больше MAX_DEPOSIT, поэтому пополняем частями
        DepositeRequester depositeRequester = new DepositeRequester(senderUserSpec, ResponseSpecs.requestReturnsOK());
        BigDecimal remaining = transferAmount;
        while (remaining.signum() > 0) {
            BigDecimal chunk = remaining.min(AccountLimits.MAX_DEPOSIT);
            depositeRequester.post(DepositeRequest.builder()
                    .id(senderAccountId)
                    .balance(chunk)
                    .build());
            remaining = remaining.subtract(chunk);
        }

        AccountResponse[] senderAccountsBeforeTransfer = new GetAccountsRequester(senderUserSpec)
                .get()
                .extract()
                .as(AccountResponse[].class);

        // Проверка предусловия: на счёте ровно сумма перевода
        assertThat(senderAccountsBeforeTransfer[0].getBalance().setScale(2, RoundingMode.HALF_UP))
                .isEqualByComparingTo(transferAmount);

        TransferRequest transferRequest = TransferRequest.builder()
                .senderAccountId(senderAccountId)
                .receiverAccountId(receiverAccountId)
                .amount(transferAmount)
                .build();

        new TransferRequester(senderUserSpec, ResponseSpecs.requestReturnsOK())
                .post(transferRequest);

        AccountResponse[] senderAccounts = new GetAccountsRequester(senderUserSpec)
                .get()
                .extract()
                .as(AccountResponse[].class);

        AccountResponse[] receiverAccounts = new GetAccountsRequester(receiverUserSpec)
                .get()
                .extract()
                .as(AccountResponse[].class);

        softly.assertThat(senderAccounts[0].getBalance().setScale(2, RoundingMode.HALF_UP))
                .isEqualByComparingTo(BigDecimal.ZERO);
        softly.assertThat(receiverAccounts[0].getBalance().setScale(2, RoundingMode.HALF_UP))
                .isEqualByComparingTo(transferAmount);
    }

    @ParameterizedTest(name = "[{index}] Недопустимая сумма перевода - {0}")
    @ValueSource(strings = {"0", "-0.01", "10000.01"})
    public void userCannotTransferInvalidAmount(BigDecimal transferAmount) {
        // Денег на счёте должно хватать, чтобы отказ был именно из-за суммы перевода
        BigDecimal initialBalance = transferAmount.max(
                RandomData.getAmount(AccountLimits.MIN_AMOUNT, AccountLimits.MAX_DEPOSIT));

        // За один депозит можно положить не больше MAX_DEPOSIT, поэтому пополняем частями
        DepositeRequester depositeRequester = new DepositeRequester(senderUserSpec, ResponseSpecs.requestReturnsOK());
        BigDecimal remaining = initialBalance;
        while (remaining.signum() > 0) {
            BigDecimal chunk = remaining.min(AccountLimits.MAX_DEPOSIT);
            depositeRequester.post(DepositeRequest.builder()
                    .id(senderAccountId)
                    .balance(chunk)
                    .build());
            remaining = remaining.subtract(chunk);
        }

        AccountResponse[] senderAccountsBeforeTransfer = new GetAccountsRequester(senderUserSpec)
                .get()
                .extract()
                .as(AccountResponse[].class);

        // Проверка предусловия: деньги на счёт действительно положены
        assertThat(senderAccountsBeforeTransfer[0].getBalance().setScale(2, RoundingMode.HALF_UP))
                .isEqualByComparingTo(initialBalance);

        TransferRequest transferRequest = TransferRequest.builder()
                .senderAccountId(senderAccountId)
                .receiverAccountId(receiverAccountId)
                .amount(transferAmount)
                .build();

        new TransferRequester(senderUserSpec, ResponseSpecs.requestReturnsBadRequest())
                .post(transferRequest);

        AccountResponse[] senderAccounts = new GetAccountsRequester(senderUserSpec)
                .get()
                .extract()
                .as(AccountResponse[].class);

        AccountResponse[] receiverAccounts = new GetAccountsRequester(receiverUserSpec)
                .get()
                .extract()
                .as(AccountResponse[].class);

        softly.assertThat(senderAccounts[0].getBalance().setScale(2, RoundingMode.HALF_UP))
                .isEqualByComparingTo(initialBalance);
        softly.assertThat(receiverAccounts[0].getBalance().setScale(2, RoundingMode.HALF_UP))
                .isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    public void userCannotTransferMoreThanAvailableBalance() {
        BigDecimal initialBalance = RandomData.getAmount(AccountLimits.MIN_AMOUNT, AccountLimits.MAX_DEPOSIT);
        // Граница: на одну копейку больше, чем есть на счёте
        BigDecimal transferAmount = initialBalance.add(AccountLimits.MIN_AMOUNT);

        new DepositeRequester(senderUserSpec, ResponseSpecs.requestReturnsOK())
                .post(DepositeRequest.builder()
                        .id(senderAccountId)
                        .balance(initialBalance)
                        .build());

        TransferRequest transferRequest = TransferRequest.builder()
                .senderAccountId(senderAccountId)
                .receiverAccountId(receiverAccountId)
                .amount(transferAmount)
                .build();

        new TransferRequester(senderUserSpec, ResponseSpecs.requestReturnsBadRequest())
                .post(transferRequest);

        AccountResponse[] senderAccounts = new GetAccountsRequester(senderUserSpec)
                .get()
                .extract()
                .as(AccountResponse[].class);

        AccountResponse[] receiverAccounts = new GetAccountsRequester(receiverUserSpec)
                .get()
                .extract()
                .as(AccountResponse[].class);

        softly.assertThat(senderAccounts[0].getBalance().setScale(2, RoundingMode.HALF_UP))
                .isEqualByComparingTo(initialBalance);
        softly.assertThat(receiverAccounts[0].getBalance().setScale(2, RoundingMode.HALF_UP))
                .isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    public void userCanTransferBetweenOwnAccounts() {
        int secondOwnAccountId = new CreateAccountRequester(senderUserSpec, ResponseSpecs.entityWasCreated())
                .post()
                .extract()
                .as(AccountResponse.class)
                .getId();

        BigDecimal initialBalance = RandomData.getAmount(AccountLimits.MIN_AMOUNT, AccountLimits.MAX_DEPOSIT);
        BigDecimal transferAmount = RandomData.getAmount(AccountLimits.MIN_AMOUNT, initialBalance);

        new DepositeRequester(senderUserSpec, ResponseSpecs.requestReturnsOK())
                .post(DepositeRequest.builder()
                        .id(senderAccountId)
                        .balance(initialBalance)
                        .build());

        TransferRequest transferRequest = TransferRequest.builder()
                .senderAccountId(senderAccountId)
                .receiverAccountId(secondOwnAccountId)
                .amount(transferAmount)
                .build();

        new TransferRequester(senderUserSpec, ResponseSpecs.requestReturnsOK())
                .post(transferRequest);

        AccountResponse[] ownAccounts = new GetAccountsRequester(senderUserSpec)
                .get()
                .extract()
                .as(AccountResponse[].class);

        Map<Integer, BigDecimal> balancesByAccountId = Arrays.stream(ownAccounts)
                .collect(Collectors.toMap(AccountResponse::getId, AccountResponse::getBalance));

        softly.assertThat(balancesByAccountId.get(senderAccountId).setScale(2, RoundingMode.HALF_UP))
                .isEqualByComparingTo(initialBalance.subtract(transferAmount));
        softly.assertThat(balancesByAccountId.get(secondOwnAccountId).setScale(2, RoundingMode.HALF_UP))
                .isEqualByComparingTo(transferAmount);
    }

    @Test
    public void userCanTransferEntireBalance() {
        BigDecimal initialBalance = RandomData.getAmount(AccountLimits.MIN_AMOUNT, AccountLimits.MAX_DEPOSIT);

        new DepositeRequester(senderUserSpec, ResponseSpecs.requestReturnsOK())
                .post(DepositeRequest.builder()
                        .id(senderAccountId)
                        .balance(initialBalance)
                        .build());

        TransferRequest transferRequest = TransferRequest.builder()
                .senderAccountId(senderAccountId)
                .receiverAccountId(receiverAccountId)
                .amount(initialBalance)
                .build();

        new TransferRequester(senderUserSpec, ResponseSpecs.requestReturnsOK())
                .post(transferRequest);

        AccountResponse[] senderAccounts = new GetAccountsRequester(senderUserSpec)
                .get()
                .extract()
                .as(AccountResponse[].class);

        AccountResponse[] receiverAccounts = new GetAccountsRequester(receiverUserSpec)
                .get()
                .extract()
                .as(AccountResponse[].class);

        softly.assertThat(senderAccounts[0].getBalance().setScale(2, RoundingMode.HALF_UP))
                .isEqualByComparingTo(BigDecimal.ZERO);
        softly.assertThat(receiverAccounts[0].getBalance().setScale(2, RoundingMode.HALF_UP))
                .isEqualByComparingTo(initialBalance);
    }
}
