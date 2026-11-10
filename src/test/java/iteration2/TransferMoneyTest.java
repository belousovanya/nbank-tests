package iteration2;

import common.BaseTest;
import generators.RandomData;
import io.restassured.specification.RequestSpecification;
import models.*;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
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
                .post(null)
                .body("id", Matchers.notNullValue())
                .extract()
                .path("id");

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
                .post(null)
                .body("id", Matchers.notNullValue())
                .extract()
                .path("id");
    }

    @Test
    public void userCanTransferToAnotherUsersAccount() {
        BigDecimal initialBalance = BigDecimal.valueOf(0.01);
        BigDecimal transferAmount = BigDecimal.valueOf(0.01);

        DepositeRequest depositeRequest = DepositeRequest.builder()
                .id(senderAccountId)
                .balance(initialBalance)
                .build();

        DepositeRequester depositRequester = new DepositeRequester(
                senderUserSpec, ResponseSpecs.requestReturnsOK());

        depositRequester.post(depositeRequest);

        DepositeResponse[] senderAccounts = new GetAccountsRequester(senderUserSpec)
                .get()
                .extract()
                .as(DepositeResponse[].class);

        softly.assertThat(senderAccounts[0].getBalance())
                .isEqualByComparingTo(initialBalance);

        // Выполняем перевод
        TransferRequest transferRequest = TransferRequest.builder()
                .senderAccountId(senderAccountId)
                .receiverAccountId(receiverAccountId)
                .amount(transferAmount)
                .build();

        new TransferRequester(senderUserSpec, ResponseSpecs.requestReturnsOK())
                .post(transferRequest);

        DepositeResponse[] senderAccountsAfterTransfer = new GetAccountsRequester(senderUserSpec)
                .get()
                .extract()
                .as(DepositeResponse[].class);

        DepositeResponse[] receiverAccountsAfterTransfer = new GetAccountsRequester(receiverUserSpec)
                .get()
                .extract()
                .as(DepositeResponse[].class);


        softly.assertThat(senderAccountsAfterTransfer[0].getBalance())
                .isEqualByComparingTo(initialBalance.subtract(transferAmount));

        softly.assertThat(receiverAccountsAfterTransfer[0].getBalance())
                .isEqualByComparingTo(transferAmount);
    }

    @Test
    public void userCanTransferJustBelowLimit() {
        BigDecimal depositAmount = BigDecimal.valueOf(5000);
        BigDecimal initialBalance = BigDecimal.valueOf(10000);
        BigDecimal transferAmount = BigDecimal.valueOf(9999.99);

        DepositeRequest depositeRequest = DepositeRequest.builder()
                .id(senderAccountId)
                .balance(depositAmount)
                .build();

        DepositeRequester depositeRequester = new DepositeRequester(
                senderUserSpec, ResponseSpecs.requestReturnsOK());

        depositeRequester.post(depositeRequest);
        depositeRequester.post(depositeRequest);

        DepositeResponse[] senderAccounts = new GetAccountsRequester(senderUserSpec)
                .get()
                .extract()
                .as(DepositeResponse[].class);

        softly.assertThat(senderAccounts[0].getBalance()).isEqualByComparingTo(initialBalance);

        TransferRequest transferRequest = TransferRequest.builder()
                .senderAccountId(senderAccountId)
                .receiverAccountId(receiverAccountId)
                .amount(transferAmount)
                .build();
        new TransferRequester(senderUserSpec, ResponseSpecs.requestReturnsOK())
                .post(transferRequest);

        DepositeResponse[] senderAccountAfterTransfer = new GetAccountsRequester(senderUserSpec)
                .get()
                .extract()
                .as(DepositeResponse[].class);

        DepositeResponse[] receiverAccountAfterTransfer = new GetAccountsRequester(receiverUserSpec)
                .get()
                .extract()
                .as(DepositeResponse[].class);

        BigDecimal actualSenderBalance = senderAccountAfterTransfer[0].getBalance();
        BigDecimal expectedBalance = initialBalance.subtract(transferAmount);
        BigDecimal actualReceiverBalance = receiverAccountAfterTransfer[0].getBalance();

        softly.assertThat(actualSenderBalance.setScale(2, RoundingMode.HALF_UP))
                .isEqualByComparingTo(expectedBalance);

        softly.assertThat(actualReceiverBalance.setScale(2, RoundingMode.HALF_UP))
                .isEqualByComparingTo(transferAmount);
    }

    @Test
    public void userCanTransferMaximumAmount() {
        BigDecimal depositAmount = BigDecimal.valueOf(5000);
        BigDecimal initialBalance = BigDecimal.valueOf(10000);
        BigDecimal transferAmount = BigDecimal.valueOf(10000);

        DepositeRequest depositeRequest = DepositeRequest.builder()
                .id(senderAccountId)
                .balance(depositAmount)
                .build();

        DepositeRequester depositeRequester = new DepositeRequester(
                senderUserSpec, ResponseSpecs.requestReturnsOK());

        depositeRequester.post(depositeRequest);
        depositeRequester.post(depositeRequest);

        DepositeResponse[] senderAccounts = new GetAccountsRequester(senderUserSpec)
                .get()
                .extract()
                .as(DepositeResponse[].class);

        softly.assertThat(senderAccounts[0].getBalance()).isEqualByComparingTo(initialBalance);

        TransferRequest transferRequest = TransferRequest.builder()
                .senderAccountId(senderAccountId)
                .receiverAccountId(receiverAccountId)
                .amount(transferAmount)
                .build();
        new TransferRequester(senderUserSpec, ResponseSpecs.requestReturnsOK())
                .post(transferRequest);

        DepositeResponse[] senderAccountAfterTransfer = new GetAccountsRequester(senderUserSpec)
                .get()
                .extract()
                .as(DepositeResponse[].class);

        DepositeResponse[] receiverAccountAfterTransfer = new GetAccountsRequester(receiverUserSpec)
                .get()
                .extract()
                .as(DepositeResponse[].class);

        BigDecimal actualSenderBalance = senderAccountAfterTransfer[0].getBalance();
        BigDecimal expectedBalance = initialBalance.subtract(transferAmount);
        BigDecimal actualReceiverBalance = receiverAccountAfterTransfer[0].getBalance();

        softly.assertThat(actualSenderBalance.setScale(2, RoundingMode.HALF_UP))
                .isEqualByComparingTo(expectedBalance);

        softly.assertThat(actualReceiverBalance.setScale(2, RoundingMode.HALF_UP))
                .isEqualByComparingTo(transferAmount);
    }

    @Test
    public void userCannotTransferZeroAmount() {
        BigDecimal depositAmount = BigDecimal.valueOf(5000);
        BigDecimal transferAmount = BigDecimal.valueOf(0);

        DepositeRequest depositeRequest = DepositeRequest.builder()
                .id(senderAccountId)
                .balance(depositAmount)
                .build();

        DepositeRequester depositeRequester = new DepositeRequester(
                senderUserSpec, ResponseSpecs.requestReturnsOK());

        depositeRequester.post(depositeRequest);

        DepositeResponse[] senderAccounts = new GetAccountsRequester(senderUserSpec)
                .get()
                .extract()
                .as(DepositeResponse[].class);

        softly.assertThat(senderAccounts[0].getBalance()).isEqualByComparingTo(depositAmount);

        TransferRequest transferRequest = TransferRequest.builder()
                .senderAccountId(senderAccountId)
                .receiverAccountId(receiverAccountId)
                .amount(transferAmount)
                .build();
        new TransferRequester(senderUserSpec, ResponseSpecs.requestReturnsBadRequest())
                .post(transferRequest);

        DepositeResponse[] senderAccountAfterTransfer = new GetAccountsRequester(senderUserSpec)
                .get()
                .extract()
                .as(DepositeResponse[].class);

        DepositeResponse[] receiverAccountAfterTransfer = new GetAccountsRequester(receiverUserSpec)
                .get()
                .extract()
                .as(DepositeResponse[].class);

        BigDecimal actualSenderBalance = senderAccountAfterTransfer[0].getBalance();
        BigDecimal actualReceiverBalance = receiverAccountAfterTransfer[0].getBalance();

        softly.assertThat(actualSenderBalance)
                .isEqualByComparingTo(depositAmount);

        softly.assertThat(actualReceiverBalance)
                .isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    public void userCannotTransferNegativeAmount() {
        BigDecimal depositAmount = BigDecimal.valueOf(5000);
        BigDecimal transferAmount = BigDecimal.valueOf(-0.01);

        DepositeRequest depositeRequest = DepositeRequest.builder()
                .id(senderAccountId)
                .balance(depositAmount)
                .build();

        DepositeRequester depositeRequester = new DepositeRequester(
                senderUserSpec, ResponseSpecs.requestReturnsOK());

        depositeRequester.post(depositeRequest);

        DepositeResponse[] senderAccounts = new GetAccountsRequester(senderUserSpec)
                .get()
                .extract()
                .as(DepositeResponse[].class);

        softly.assertThat(senderAccounts[0].getBalance()).isEqualByComparingTo(depositAmount);

        TransferRequest transferRequest = TransferRequest.builder()
                .senderAccountId(senderAccountId)
                .receiverAccountId(receiverAccountId)
                .amount(transferAmount)
                .build();
        new TransferRequester(senderUserSpec, ResponseSpecs.requestReturnsBadRequest())
                .post(transferRequest);

        DepositeResponse[] senderAccountAfterTransfer = new GetAccountsRequester(senderUserSpec)
                .get()
                .extract()
                .as(DepositeResponse[].class);

        DepositeResponse[] receiverAccountAfterTransfer = new GetAccountsRequester(receiverUserSpec)
                .get()
                .extract()
                .as(DepositeResponse[].class);

        BigDecimal actualSenderBalance = senderAccountAfterTransfer[0].getBalance();
        BigDecimal actualReceiverBalance = receiverAccountAfterTransfer[0].getBalance();

        softly.assertThat(actualSenderBalance)
                .isEqualByComparingTo(depositAmount);

        softly.assertThat(actualReceiverBalance)
                .isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    public void userCannotTransferAboveLimit() {
        BigDecimal depositAmount = BigDecimal.valueOf(5000);
        BigDecimal initialBalance = BigDecimal.valueOf(15000);
        BigDecimal transferAmount = BigDecimal.valueOf(10000.01);

        DepositeRequest depositeRequest = DepositeRequest.builder()
                .id(senderAccountId)
                .balance(depositAmount)
                .build();

        DepositeRequester depositeRequester = new DepositeRequester(
                senderUserSpec, ResponseSpecs.requestReturnsOK());
        depositeRequester.post(depositeRequest);
        depositeRequester.post(depositeRequest);
        depositeRequester.post(depositeRequest);

        DepositeResponse[] senderAccounts = new GetAccountsRequester(senderUserSpec)
                .get()
                .extract()
                .as(DepositeResponse[].class);

        // Средств должно хватать, чтобы отказ был связан с лимитом перевода.
        assertThat(senderAccounts[0].getBalance())
                .isEqualByComparingTo(initialBalance);

        TransferRequest transferRequest = TransferRequest.builder()
                .senderAccountId(senderAccountId)
                .receiverAccountId(receiverAccountId)
                .amount(transferAmount)
                .build();

        new TransferRequester(senderUserSpec, ResponseSpecs.requestReturnsBadRequest())
                .post(transferRequest);

        DepositeResponse[] senderAccountsAfterTransfer = new GetAccountsRequester(senderUserSpec)
                .get()
                .extract()
                .as(DepositeResponse[].class);

        DepositeResponse[] receiverAccountsAfterTransfer = new GetAccountsRequester(receiverUserSpec)
                .get()
                .extract()
                .as(DepositeResponse[].class);

        softly.assertThat(senderAccountsAfterTransfer[0].getBalance())
                .isEqualByComparingTo(initialBalance);

        softly.assertThat(receiverAccountsAfterTransfer[0].getBalance())
                .isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    public void userCannotTransferMoreThanAvailableBalance() {
        BigDecimal transferAmount = BigDecimal.valueOf(4000);

        TransferRequest transferRequest = TransferRequest.builder()
                .senderAccountId(senderAccountId)
                .receiverAccountId(receiverAccountId)
                .amount(transferAmount)
                .build();

        new TransferRequester(senderUserSpec, ResponseSpecs.requestReturnsBadRequest())
                .post(transferRequest);

        DepositeResponse[] senderAccounts = new GetAccountsRequester(senderUserSpec)
                .get()
                .extract()
                .as(DepositeResponse[].class);

        DepositeResponse[] receiverAccounts = new GetAccountsRequester(receiverUserSpec)
                .get()
                .extract()
                .as(DepositeResponse[].class);

        softly.assertThat(senderAccounts[0].getBalance())
                .isEqualByComparingTo(BigDecimal.ZERO);

        softly.assertThat(receiverAccounts[0].getBalance())
                .isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    public void userCanTransferBetweenOwnAccounts() {
        BigDecimal initialBalance = BigDecimal.valueOf(5000);
        BigDecimal transferAmount = BigDecimal.valueOf(4000);

        int secondOwnAccountId = new CreateAccountRequester(senderUserSpec, ResponseSpecs.entityWasCreated())
                .post(null)
                .body("id", Matchers.notNullValue())
                .extract()
                .path("id");

        DepositeRequest depositeRequest = DepositeRequest.builder()
                .id(senderAccountId)
                .balance(initialBalance)
                .build();

        new DepositeRequester(senderUserSpec, ResponseSpecs.requestReturnsOK())
                .post(depositeRequest);

        TransferRequest transferRequest = TransferRequest.builder()
                .senderAccountId(senderAccountId)
                .receiverAccountId(secondOwnAccountId)
                .amount(transferAmount)
                .build();

        new TransferRequester(senderUserSpec, ResponseSpecs.requestReturnsOK())
                .post(transferRequest);

        DepositeResponse[] ownAccounts = new GetAccountsRequester(senderUserSpec)
                .get()
                .extract()
                .as(DepositeResponse[].class);

        Map<Integer, BigDecimal> balancesByAccountId = Arrays.stream(ownAccounts)
                .collect(Collectors.toMap(DepositeResponse::getId, DepositeResponse::getBalance));

        softly.assertThat(balancesByAccountId.get(senderAccountId))
                .isEqualByComparingTo(initialBalance.subtract(transferAmount));

        softly.assertThat(balancesByAccountId.get(secondOwnAccountId))
                .isEqualByComparingTo(transferAmount);
    }

    @Test
    public void userCanTransferEntireBalance() {
        BigDecimal initialBalance = BigDecimal.valueOf(5000);

        DepositeRequest depositeRequest = DepositeRequest.builder()
                .id(senderAccountId)
                .balance(initialBalance)
                .build();

        new DepositeRequester(senderUserSpec, ResponseSpecs.requestReturnsOK())
                .post(depositeRequest);

        TransferRequest transferRequest = TransferRequest.builder()
                .senderAccountId(senderAccountId)
                .receiverAccountId(receiverAccountId)
                .amount(initialBalance)
                .build();

        new TransferRequester(senderUserSpec, ResponseSpecs.requestReturnsOK())
                .post(transferRequest);

        DepositeResponse[] senderAccounts = new GetAccountsRequester(senderUserSpec)
                .get()
                .extract()
                .as(DepositeResponse[].class);

        DepositeResponse[] receiverAccounts = new GetAccountsRequester(receiverUserSpec)
                .get()
                .extract()
                .as(DepositeResponse[].class);

        softly.assertThat(senderAccounts[0].getBalance())
                .isEqualByComparingTo(BigDecimal.ZERO);

        softly.assertThat(receiverAccounts[0].getBalance())
                .isEqualByComparingTo(initialBalance);
    }
}
