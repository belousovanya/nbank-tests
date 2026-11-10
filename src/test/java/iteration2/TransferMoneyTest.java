package iteration2;

import common.BaseTest;
import generators.RandomData;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;
import models.*;
import org.apache.http.HttpStatus;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import requests.*;
import specs.RequestSpecs;
import specs.ResponseSpecs;

import java.math.BigDecimal;
import java.math.RoundingMode;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertAll;

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
        BigDecimal initialBalance = BigDecimal.valueOf(0.1);
        BigDecimal transferAmount = BigDecimal.valueOf(0.1);

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
        BigDecimal transferAmount = BigDecimal.valueOf(9999.9);

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
        BigDecimal transferAmount = BigDecimal.valueOf(-0.1);

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
        // Готовим достаточный баланс, чтобы проверить именно лимит перевода
        // Пополняем счёт отправителя несколькими депозитами по 5000
        given()
                .contentType(ContentType.JSON)
                .header("Authorization", senderUserSpec)
                .body("""
                        {
                          "id": %d,
                          "balance": 5000
                        }
                        """.formatted(senderAccountId))
                .post("http://localhost:4111/api/v1/accounts/deposit")
                .then()
                .statusCode(HttpStatus.SC_OK);

        given()
                .contentType(ContentType.JSON)
                .header("Authorization", senderUserSpec)
                .body("""
                        {
                          "id": %d,
                          "balance": 5000
                        }
                        """.formatted(senderAccountId))
                .post("http://localhost:4111/api/v1/accounts/deposit")
                .then()
                .statusCode(HttpStatus.SC_OK);

        given()
                .contentType(ContentType.JSON)
                .header("Authorization", senderUserSpec)
                .body("""
                        {
                          "id": %d,
                          "balance": 5000
                        }
                        """.formatted(senderAccountId))
                .post("http://localhost:4111/api/v1/accounts/deposit")
                .then()
                .statusCode(HttpStatus.SC_OK);

        // Пытаемся выполнить перевод
        Response transferResponse = given()
                .contentType(ContentType.JSON)
                .header("Authorization", senderUserSpec)
                .body("""
                        {
                          "senderAccountId": %d,
                          "receiverAccountId": %d,
                          "amount": 10000.1
                        }
                        """.formatted(senderAccountId, receiverAccountId))
                .post("http://localhost:4111/api/v1/accounts/transfer");

        // Запрашиваем балансы после попытки перевода
        Response senderAccounts = given()
                .header("Authorization", senderUserSpec)
                .accept(ContentType.JSON)
                .get("http://localhost:4111/api/v1/customer/accounts");

        Response receiverAccounts = given()
                .header("Authorization", receiverUserSpec)
                .accept(ContentType.JSON)
                .get("http://localhost:4111/api/v1/customer/accounts");

        assertAll(
                () -> transferResponse.then().statusCode(HttpStatus.SC_BAD_REQUEST),
                () -> senderAccounts.then()
                        .statusCode(HttpStatus.SC_OK)
                        .body("[0].balance", Matchers.equalTo(15000.0f)),
                () -> receiverAccounts.then()
                        .statusCode(HttpStatus.SC_OK)
                        .body("[0].balance", Matchers.equalTo(0.0f))
        );
    }

    @Test
    public void userCannotTransferMoreThanAvailableBalance() {
        // Пытаемся выполнить перевод
        Response transferResponse = given()
                .contentType(ContentType.JSON)
                .header("Authorization", senderUserSpec)
                .body("""
                        {
                          "senderAccountId": %d,
                          "receiverAccountId": %d,
                          "amount": 4000
                        }
                        """.formatted(senderAccountId, receiverAccountId))
                .post("http://localhost:4111/api/v1/accounts/transfer");

        // Запрашиваем балансы после попытки перевода
        Response senderAccounts = given()
                .header("Authorization", senderUserSpec)
                .accept(ContentType.JSON)
                .get("http://localhost:4111/api/v1/customer/accounts");

        Response receiverAccounts = given()
                .header("Authorization", receiverUserSpec)
                .accept(ContentType.JSON)
                .get("http://localhost:4111/api/v1/customer/accounts");

        assertAll(
                () -> transferResponse.then().statusCode(HttpStatus.SC_BAD_REQUEST),
                () -> senderAccounts.then()
                        .statusCode(HttpStatus.SC_OK)
                        .body("[0].balance", Matchers.equalTo(0.0f)),
                () -> receiverAccounts.then()
                        .statusCode(HttpStatus.SC_OK)
                        .body("[0].balance", Matchers.equalTo(0.0f))
        );
    }

    @Test
    public void userCanTransferBetweenOwnAccounts() {
        // Создаём второй счёт отправителя
        int secondOwnAccountId = given()
                .contentType(ContentType.JSON)
                .header("Authorization", senderUserSpec)
                .post("http://localhost:4111/api/v1/accounts")
                .then()
                .statusCode(HttpStatus.SC_CREATED)
                .body("id", Matchers.notNullValue())
                .extract()
                .path("id");

        // Пополняем счёт отправителя
        given()
                .contentType(ContentType.JSON)
                .header("Authorization", senderUserSpec)
                .body("""
                        {
                          "id": %d,
                          "balance": 5000
                        }
                        """.formatted(senderAccountId))
                .post("http://localhost:4111/api/v1/accounts/deposit")
                .then()
                .statusCode(HttpStatus.SC_OK);

        // Выполняем перевод
        given()
                .contentType(ContentType.JSON)
                .header("Authorization", senderUserSpec)
                .body("""
                        {
                          "senderAccountId": %d,
                          "receiverAccountId": %d,
                          "amount": 4000
                        }
                        """.formatted(senderAccountId, secondOwnAccountId))
                .post("http://localhost:4111/api/v1/accounts/transfer")
                .then()
                .statusCode(HttpStatus.SC_OK);

        // Проверяем баланс обоих счетов
        given()
                .header("Authorization", senderUserSpec)
                .accept(ContentType.JSON)
                .get("http://localhost:4111/api/v1/customer/accounts")
                .then()
                .statusCode(HttpStatus.SC_OK)
                .body("find { it.id == %d }.balance".formatted(senderAccountId), Matchers.equalTo(1000.0f));

        given()
                .header("Authorization", senderUserSpec)
                .accept(ContentType.JSON)
                .get("http://localhost:4111/api/v1/customer/accounts")
                .then()
                .statusCode(HttpStatus.SC_OK)
                .body("find { it.id == %d }.balance".formatted(secondOwnAccountId), Matchers.equalTo(4000f));
    }

    @Test
    public void userCanTransferEntireBalance() {
        // Пополняем счёт отправителя
        given()
                .contentType(ContentType.JSON)
                .header("Authorization", senderUserSpec)
                .body("""
                        {
                          "id": %d,
                          "balance": 5000
                        }
                        """.formatted(senderAccountId))
                .post("http://localhost:4111/api/v1/accounts/deposit")
                .then()
                .statusCode(HttpStatus.SC_OK);

        // Выполняем перевод
        given()
                .contentType(ContentType.JSON)
                .header("Authorization", senderUserSpec)
                .body("""
                        {
                          "senderAccountId": %d,
                          "receiverAccountId": %d,
                          "amount": 5000
                        }
                        """.formatted(senderAccountId, receiverAccountId))
                .post("http://localhost:4111/api/v1/accounts/transfer")
                .then()
                .statusCode(HttpStatus.SC_OK);

        // Проверяем баланс обоих счетов
        given()
                .header("Authorization", senderUserSpec)
                .accept(ContentType.JSON)
                .get("http://localhost:4111/api/v1/customer/accounts")
                .then()
                .statusCode(HttpStatus.SC_OK)
                .body("[0].balance", Matchers.equalTo(0.0f));

        given()
                .header("Authorization", receiverUserSpec)
                .accept(ContentType.JSON)
                .get("http://localhost:4111/api/v1/customer/accounts")
                .then()
                .statusCode(HttpStatus.SC_OK)
                .body("[0].balance", Matchers.equalTo(5000f));
    }
}
