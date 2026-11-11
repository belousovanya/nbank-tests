package iteration2;

import io.restassured.RestAssured;
import io.restassured.filter.log.RequestLoggingFilter;
import io.restassured.filter.log.ResponseLoggingFilter;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import org.apache.http.HttpStatus;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class TransferMoneyTest {

    private String senderUserAuthHeader;
    private int senderAccountId;

    private String receiverUserAuthHeader;
    private int receiverAccountId;

    @BeforeAll
    public static void setupRestAssured() {
        RestAssured.filters(
                List.of(new RequestLoggingFilter(),
                        new ResponseLoggingFilter()));
    }

    @BeforeEach
    public void prepareUserAndAccounts() {
        // Предусловие
        String username = "user"
                + UUID.randomUUID().toString().substring(0, 10);
        String receiverUsername = "user"
                + UUID.randomUUID().toString().substring(0, 10);
        String password = "verysTRongPassword33$";

        // 1. Создаем нового пользователя под админом
        given()
                .contentType(ContentType.JSON)
                .header("Authorization", "Basic YWRtaW46YWRtaW4=")
                .body("""
                        {
                        "username": "%s",
                        "password": "%s",
                        "role": "USER"
                        }
                        """.formatted(username, password))
                .post("http://localhost:4111/api/v1/admin/users")
                .then()
                .statusCode(HttpStatus.SC_CREATED);

        // 2. Авторизуемся созданным пользователем (юзер-отправитель)
        senderUserAuthHeader = given()
                .contentType(ContentType.JSON)
                .body("""
                        {
                        "username": "%s",
                        "password": "%s"
                        }
                        """.formatted(username, password))
                .post("http://localhost:4111/api/v1/auth/login")
                .then()
                .statusCode(HttpStatus.SC_OK)
                .header("Authorization", Matchers.notNullValue())
                .extract()
                .header("Authorization");

        // Проверяем, что созданный пользователь доступен через API
        given()
                .header("Authorization", senderUserAuthHeader)
                .accept(ContentType.JSON)
                .get("http://localhost:4111/api/v1/customer/profile")
                .then()
                .statusCode(HttpStatus.SC_OK)
                .body("username", Matchers.equalTo(username))
                .body("role", Matchers.equalTo("USER"));

        // 3. Создаем его счёт и сохраняем ID из ответа (юзер-отправитель)
        senderAccountId = given()
                .contentType(ContentType.JSON)
                .header("Authorization", senderUserAuthHeader)
                .post("http://localhost:4111/api/v1/accounts")
                .then()
                .statusCode(HttpStatus.SC_CREATED)
                .body("id", Matchers.notNullValue())
                .extract()
                .path("id");

        // Проверяем, что счёт создан с нулевым балансом
        given()
                .header("Authorization", senderUserAuthHeader)
                .accept(ContentType.JSON)
                .get("http://localhost:4111/api/v1/customer/accounts")
                .then()
                .statusCode(HttpStatus.SC_OK)
                .body("id", Matchers.hasItem(senderAccountId))
                .body("find { it.id == %d }.balance".formatted(senderAccountId), Matchers.equalTo(0.0f));

        // 4. Создаем нового получателя под админом
        given()
                .contentType(ContentType.JSON)
                .header("Authorization", "Basic YWRtaW46YWRtaW4=")
                .body("""
                        {
                        "username": "%s",
                        "password": "%s",
                        "role": "USER"
                        }
                        """.formatted(receiverUsername, password))
                .post("http://localhost:4111/api/v1/admin/users")
                .then()
                .statusCode(HttpStatus.SC_CREATED);

        // 5. Авторизуемся созданным пользователем (юзер-получатель)
        receiverUserAuthHeader = given()
                .contentType(ContentType.JSON)
                .body("""
                        {
                        "username": "%s",
                        "password": "%s"
                        }
                        """.formatted(receiverUsername, password))
                .post("http://localhost:4111/api/v1/auth/login")
                .then()
                .statusCode(HttpStatus.SC_OK)
                .header("Authorization", Matchers.notNullValue())
                .extract()
                .header("Authorization");

        // Проверяем, что созданный пользователь доступен через API
        given()
                .header("Authorization", receiverUserAuthHeader)
                .accept(ContentType.JSON)
                .get("http://localhost:4111/api/v1/customer/profile")
                .then()
                .statusCode(HttpStatus.SC_OK)
                .body("username", Matchers.equalTo(receiverUsername))
                .body("role", Matchers.equalTo("USER"));

        // 6. Создаем его счёт и сохраняем ID из ответа (юзер-получатель)
        receiverAccountId = given()
                .contentType(ContentType.JSON)
                .header("Authorization", receiverUserAuthHeader)
                .post("http://localhost:4111/api/v1/accounts")
                .then()
                .statusCode(HttpStatus.SC_CREATED)
                .body("id", Matchers.notNullValue())
                .extract()
                .path("id");

        // Проверяем, что счёт создан с нулевым балансом
        given()
                .header("Authorization", receiverUserAuthHeader)
                .accept(ContentType.JSON)
                .get("http://localhost:4111/api/v1/customer/accounts")
                .then()
                .statusCode(HttpStatus.SC_OK)
                .body("id", Matchers.hasItem(receiverAccountId))
                .body("find { it.id == %d }.balance".formatted(receiverAccountId), Matchers.equalTo(0.0f));
    }

    @Test
    public void userCanTransferToAnotherUsersAccount() {
        // Пополняем счёт отправителя несколькими депозитами по 5000
        given()
                .contentType(ContentType.JSON)
                .header("Authorization", senderUserAuthHeader)
                .body("""
                        {
                          "id": %d,
                          "balance": 5000
                        }
                        """.formatted(senderAccountId))
                .post("http://localhost:4111/api/v1/accounts/deposit")
                .then()
                .statusCode(HttpStatus.SC_OK);

        // Проверяем сохранённый баланс после пополнения
        given()
                .header("Authorization", senderUserAuthHeader)
                .accept(ContentType.JSON)
                .get("http://localhost:4111/api/v1/customer/accounts")
                .then()
                .statusCode(HttpStatus.SC_OK)
                .body("id", Matchers.hasItem(senderAccountId))
                .body("find { it.id == %d }.balance".formatted(senderAccountId), Matchers.equalTo(5000.0f));

        given()
                .contentType(ContentType.JSON)
                .header("Authorization", senderUserAuthHeader)
                .body("""
                        {
                          "id": %d,
                          "balance": 5000
                        }
                        """.formatted(senderAccountId))
                .post("http://localhost:4111/api/v1/accounts/deposit")
                .then()
                .statusCode(HttpStatus.SC_OK);

        // Проверяем сохранённый баланс после пополнения
        given()
                .header("Authorization", senderUserAuthHeader)
                .accept(ContentType.JSON)
                .get("http://localhost:4111/api/v1/customer/accounts")
                .then()
                .statusCode(HttpStatus.SC_OK)
                .body("id", Matchers.hasItem(senderAccountId))
                .body("find { it.id == %d }.balance".formatted(senderAccountId), Matchers.equalTo(10000.0f));

        // Выполняем перевод
        given()
                .contentType(ContentType.JSON)
                .header("Authorization", senderUserAuthHeader)
                .body("""
                        {
                          "senderAccountId": %d,
                          "receiverAccountId": %d,
                          "amount": 0.01
                        }
                        """.formatted(senderAccountId, receiverAccountId))
                .post("http://localhost:4111/api/v1/accounts/transfer")
                .then()
                .statusCode(HttpStatus.SC_OK);

        // Проверяем баланс обоих счетов
        given()
                .header("Authorization", senderUserAuthHeader)
                .accept(ContentType.JSON)
                .get("http://localhost:4111/api/v1/customer/accounts")
                .then()
                .statusCode(HttpStatus.SC_OK)
                .body("[0].balance", Matchers.equalTo(9999.99f));

        given()
                .header("Authorization", receiverUserAuthHeader)
                .accept(ContentType.JSON)
                .get("http://localhost:4111/api/v1/customer/accounts")
                .then()
                .statusCode(HttpStatus.SC_OK)
                .body("[0].balance", Matchers.equalTo(0.01f));
    }

    @Test
    public void userCanTransferJustBelowLimit() {
        // Пополняем счёт отправителя несколькими депозитами по 5000
        given()
                .contentType(ContentType.JSON)
                .header("Authorization", senderUserAuthHeader)
                .body("""
                        {
                          "id": %d,
                          "balance": 5000
                        }
                        """.formatted(senderAccountId))
                .post("http://localhost:4111/api/v1/accounts/deposit")
                .then()
                .statusCode(HttpStatus.SC_OK);

        // Проверяем сохранённый баланс после пополнения
        given()
                .header("Authorization", senderUserAuthHeader)
                .accept(ContentType.JSON)
                .get("http://localhost:4111/api/v1/customer/accounts")
                .then()
                .statusCode(HttpStatus.SC_OK)
                .body("id", Matchers.hasItem(senderAccountId))
                .body("find { it.id == %d }.balance".formatted(senderAccountId), Matchers.equalTo(5000.0f));

        given()
                .contentType(ContentType.JSON)
                .header("Authorization", senderUserAuthHeader)
                .body("""
                        {
                          "id": %d,
                          "balance": 5000
                        }
                        """.formatted(senderAccountId))
                .post("http://localhost:4111/api/v1/accounts/deposit")
                .then()
                .statusCode(HttpStatus.SC_OK);

        // Проверяем сохранённый баланс после пополнения
        given()
                .header("Authorization", senderUserAuthHeader)
                .accept(ContentType.JSON)
                .get("http://localhost:4111/api/v1/customer/accounts")
                .then()
                .statusCode(HttpStatus.SC_OK)
                .body("id", Matchers.hasItem(senderAccountId))
                .body("find { it.id == %d }.balance".formatted(senderAccountId), Matchers.equalTo(10000.0f));

        // Выполняем перевод
        given()
                .contentType(ContentType.JSON)
                .header("Authorization", senderUserAuthHeader)
                .body("""
                        {
                          "senderAccountId": %d,
                          "receiverAccountId": %d,
                          "amount": 9999.99
                        }
                        """.formatted(senderAccountId, receiverAccountId))
                .post("http://localhost:4111/api/v1/accounts/transfer")
                .then()
                .statusCode(HttpStatus.SC_OK);

        // Проверяем баланс обоих счетов
        double senderBalance = given()
                .header("Authorization", senderUserAuthHeader)
                .accept(ContentType.JSON)
                .get("http://localhost:4111/api/v1/customer/accounts")
                .then()
                .statusCode(HttpStatus.SC_OK)
                .extract()
                .jsonPath()
                .getDouble("[0].balance");
        assertEquals(new BigDecimal("0.01"),
                BigDecimal.valueOf(senderBalance).setScale(2, RoundingMode.HALF_UP));

        given()
                .header("Authorization", receiverUserAuthHeader)
                .accept(ContentType.JSON)
                .get("http://localhost:4111/api/v1/customer/accounts")
                .then()
                .statusCode(HttpStatus.SC_OK)
                .body("[0].balance", Matchers.equalTo(9999.99f));
    }

    @Test
    public void userCanTransferMaximumAmount() {
        // Пополняем счёт отправителя несколькими депозитами по 5000
        given()
                .contentType(ContentType.JSON)
                .header("Authorization", senderUserAuthHeader)
                .body("""
                        {
                          "id": %d,
                          "balance": 5000
                        }
                        """.formatted(senderAccountId))
                .post("http://localhost:4111/api/v1/accounts/deposit")
                .then()
                .statusCode(HttpStatus.SC_OK);

        // Проверяем сохранённый баланс после пополнения
        given()
                .header("Authorization", senderUserAuthHeader)
                .accept(ContentType.JSON)
                .get("http://localhost:4111/api/v1/customer/accounts")
                .then()
                .statusCode(HttpStatus.SC_OK)
                .body("id", Matchers.hasItem(senderAccountId))
                .body("find { it.id == %d }.balance".formatted(senderAccountId), Matchers.equalTo(5000.0f));

        given()
                .contentType(ContentType.JSON)
                .header("Authorization", senderUserAuthHeader)
                .body("""
                        {
                          "id": %d,
                          "balance": 5000
                        }
                        """.formatted(senderAccountId))
                .post("http://localhost:4111/api/v1/accounts/deposit")
                .then()
                .statusCode(HttpStatus.SC_OK);

        // Проверяем сохранённый баланс после пополнения
        given()
                .header("Authorization", senderUserAuthHeader)
                .accept(ContentType.JSON)
                .get("http://localhost:4111/api/v1/customer/accounts")
                .then()
                .statusCode(HttpStatus.SC_OK)
                .body("id", Matchers.hasItem(senderAccountId))
                .body("find { it.id == %d }.balance".formatted(senderAccountId), Matchers.equalTo(10000.0f));

        // Выполняем перевод
        given()
                .contentType(ContentType.JSON)
                .header("Authorization", senderUserAuthHeader)
                .body("""
                        {
                          "senderAccountId": %d,
                          "receiverAccountId": %d,
                          "amount": 10000
                        }
                        """.formatted(senderAccountId, receiverAccountId))
                .post("http://localhost:4111/api/v1/accounts/transfer")
                .then()
                .statusCode(HttpStatus.SC_OK);

        // Проверяем баланс обоих счетов
        given()
                .header("Authorization", senderUserAuthHeader)
                .accept(ContentType.JSON)
                .get("http://localhost:4111/api/v1/customer/accounts")
                .then()
                .statusCode(HttpStatus.SC_OK)
                .body("[0].balance", Matchers.equalTo(0.0f));

        given()
                .header("Authorization", receiverUserAuthHeader)
                .accept(ContentType.JSON)
                .get("http://localhost:4111/api/v1/customer/accounts")
                .then()
                .statusCode(HttpStatus.SC_OK)
                .body("[0].balance", Matchers.equalTo(10000f));
    }

    @Test
    public void userCannotTransferZeroAmount() {
        // Пополняем счёт отправителя несколькими депозитами по 5000
        given()
                .contentType(ContentType.JSON)
                .header("Authorization", senderUserAuthHeader)
                .body("""
                        {
                          "id": %d,
                          "balance": 5000
                        }
                        """.formatted(senderAccountId))
                .post("http://localhost:4111/api/v1/accounts/deposit")
                .then()
                .statusCode(HttpStatus.SC_OK);

        // Проверяем сохранённый баланс после пополнения
        given()
                .header("Authorization", senderUserAuthHeader)
                .accept(ContentType.JSON)
                .get("http://localhost:4111/api/v1/customer/accounts")
                .then()
                .statusCode(HttpStatus.SC_OK)
                .body("id", Matchers.hasItem(senderAccountId))
                .body("find { it.id == %d }.balance".formatted(senderAccountId), Matchers.equalTo(5000.0f));

        given()
                .contentType(ContentType.JSON)
                .header("Authorization", senderUserAuthHeader)
                .body("""
                        {
                          "id": %d,
                          "balance": 5000
                        }
                        """.formatted(senderAccountId))
                .post("http://localhost:4111/api/v1/accounts/deposit")
                .then()
                .statusCode(HttpStatus.SC_OK);

        // Проверяем сохранённый баланс после пополнения
        given()
                .header("Authorization", senderUserAuthHeader)
                .accept(ContentType.JSON)
                .get("http://localhost:4111/api/v1/customer/accounts")
                .then()
                .statusCode(HttpStatus.SC_OK)
                .body("id", Matchers.hasItem(senderAccountId))
                .body("find { it.id == %d }.balance".formatted(senderAccountId), Matchers.equalTo(10000.0f));

        given()
                .contentType(ContentType.JSON)
                .header("Authorization", senderUserAuthHeader)
                .body("""
                        {
                          "id": %d,
                          "balance": 5000
                        }
                        """.formatted(senderAccountId))
                .post("http://localhost:4111/api/v1/accounts/deposit")
                .then()
                .statusCode(HttpStatus.SC_OK);

        // Проверяем сохранённый баланс после пополнения
        given()
                .header("Authorization", senderUserAuthHeader)
                .accept(ContentType.JSON)
                .get("http://localhost:4111/api/v1/customer/accounts")
                .then()
                .statusCode(HttpStatus.SC_OK)
                .body("id", Matchers.hasItem(senderAccountId))
                .body("find { it.id == %d }.balance".formatted(senderAccountId), Matchers.equalTo(15000.0f));

        // Пытаемся выполнить перевод
        Response transferResponse = given()
                .contentType(ContentType.JSON)
                .header("Authorization", senderUserAuthHeader)
                .body("""
                        {
                          "senderAccountId": %d,
                          "receiverAccountId": %d,
                          "amount": 0
                        }
                        """.formatted(senderAccountId, receiverAccountId))
                .post("http://localhost:4111/api/v1/accounts/transfer");

        // Запрашиваем балансы после попытки перевода
        Response senderAccounts = given()
                .header("Authorization", senderUserAuthHeader)
                .accept(ContentType.JSON)
                .get("http://localhost:4111/api/v1/customer/accounts");

        Response receiverAccounts = given()
                .header("Authorization", receiverUserAuthHeader)
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
    public void userCannotTransferNegativeAmount() {
        // Пополняем счёт отправителя несколькими депозитами по 5000
        given()
                .contentType(ContentType.JSON)
                .header("Authorization", senderUserAuthHeader)
                .body("""
                        {
                          "id": %d,
                          "balance": 5000
                        }
                        """.formatted(senderAccountId))
                .post("http://localhost:4111/api/v1/accounts/deposit")
                .then()
                .statusCode(HttpStatus.SC_OK);

        // Проверяем сохранённый баланс после пополнения
        given()
                .header("Authorization", senderUserAuthHeader)
                .accept(ContentType.JSON)
                .get("http://localhost:4111/api/v1/customer/accounts")
                .then()
                .statusCode(HttpStatus.SC_OK)
                .body("id", Matchers.hasItem(senderAccountId))
                .body("find { it.id == %d }.balance".formatted(senderAccountId), Matchers.equalTo(5000.0f));

        given()
                .contentType(ContentType.JSON)
                .header("Authorization", senderUserAuthHeader)
                .body("""
                        {
                          "id": %d,
                          "balance": 5000
                        }
                        """.formatted(senderAccountId))
                .post("http://localhost:4111/api/v1/accounts/deposit")
                .then()
                .statusCode(HttpStatus.SC_OK);

        // Проверяем сохранённый баланс после пополнения
        given()
                .header("Authorization", senderUserAuthHeader)
                .accept(ContentType.JSON)
                .get("http://localhost:4111/api/v1/customer/accounts")
                .then()
                .statusCode(HttpStatus.SC_OK)
                .body("id", Matchers.hasItem(senderAccountId))
                .body("find { it.id == %d }.balance".formatted(senderAccountId), Matchers.equalTo(10000.0f));

        given()
                .contentType(ContentType.JSON)
                .header("Authorization", senderUserAuthHeader)
                .body("""
                        {
                          "id": %d,
                          "balance": 5000
                        }
                        """.formatted(senderAccountId))
                .post("http://localhost:4111/api/v1/accounts/deposit")
                .then()
                .statusCode(HttpStatus.SC_OK);

        // Проверяем сохранённый баланс после пополнения
        given()
                .header("Authorization", senderUserAuthHeader)
                .accept(ContentType.JSON)
                .get("http://localhost:4111/api/v1/customer/accounts")
                .then()
                .statusCode(HttpStatus.SC_OK)
                .body("id", Matchers.hasItem(senderAccountId))
                .body("find { it.id == %d }.balance".formatted(senderAccountId), Matchers.equalTo(15000.0f));

        // Пытаемся выполнить перевод
        Response transferResponse = given()
                .contentType(ContentType.JSON)
                .header("Authorization", senderUserAuthHeader)
                .body("""
                        {
                          "senderAccountId": %d,
                          "receiverAccountId": %d,
                          "amount": -0.01
                        }
                        """.formatted(senderAccountId, receiverAccountId))
                .post("http://localhost:4111/api/v1/accounts/transfer");

        // Запрашиваем балансы после попытки перевода
        Response senderAccounts = given()
                .header("Authorization", senderUserAuthHeader)
                .accept(ContentType.JSON)
                .get("http://localhost:4111/api/v1/customer/accounts");

        Response receiverAccounts = given()
                .header("Authorization", receiverUserAuthHeader)
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
    public void userCannotTransferAboveLimit() {
        // Готовим достаточный баланс, чтобы проверить именно лимит перевода
        // Пополняем счёт отправителя несколькими депозитами по 5000
        given()
                .contentType(ContentType.JSON)
                .header("Authorization", senderUserAuthHeader)
                .body("""
                        {
                          "id": %d,
                          "balance": 5000
                        }
                        """.formatted(senderAccountId))
                .post("http://localhost:4111/api/v1/accounts/deposit")
                .then()
                .statusCode(HttpStatus.SC_OK);

        // Проверяем сохранённый баланс после пополнения
        given()
                .header("Authorization", senderUserAuthHeader)
                .accept(ContentType.JSON)
                .get("http://localhost:4111/api/v1/customer/accounts")
                .then()
                .statusCode(HttpStatus.SC_OK)
                .body("id", Matchers.hasItem(senderAccountId))
                .body("find { it.id == %d }.balance".formatted(senderAccountId), Matchers.equalTo(5000.0f));

        given()
                .contentType(ContentType.JSON)
                .header("Authorization", senderUserAuthHeader)
                .body("""
                        {
                          "id": %d,
                          "balance": 5000
                        }
                        """.formatted(senderAccountId))
                .post("http://localhost:4111/api/v1/accounts/deposit")
                .then()
                .statusCode(HttpStatus.SC_OK);

        // Проверяем сохранённый баланс после пополнения
        given()
                .header("Authorization", senderUserAuthHeader)
                .accept(ContentType.JSON)
                .get("http://localhost:4111/api/v1/customer/accounts")
                .then()
                .statusCode(HttpStatus.SC_OK)
                .body("id", Matchers.hasItem(senderAccountId))
                .body("find { it.id == %d }.balance".formatted(senderAccountId), Matchers.equalTo(10000.0f));

        given()
                .contentType(ContentType.JSON)
                .header("Authorization", senderUserAuthHeader)
                .body("""
                        {
                          "id": %d,
                          "balance": 5000
                        }
                        """.formatted(senderAccountId))
                .post("http://localhost:4111/api/v1/accounts/deposit")
                .then()
                .statusCode(HttpStatus.SC_OK);

        // Проверяем сохранённый баланс после пополнения
        given()
                .header("Authorization", senderUserAuthHeader)
                .accept(ContentType.JSON)
                .get("http://localhost:4111/api/v1/customer/accounts")
                .then()
                .statusCode(HttpStatus.SC_OK)
                .body("id", Matchers.hasItem(senderAccountId))
                .body("find { it.id == %d }.balance".formatted(senderAccountId), Matchers.equalTo(15000.0f));

        // Пытаемся выполнить перевод
        Response transferResponse = given()
                .contentType(ContentType.JSON)
                .header("Authorization", senderUserAuthHeader)
                .body("""
                        {
                          "senderAccountId": %d,
                          "receiverAccountId": %d,
                          "amount": 10000.01
                        }
                        """.formatted(senderAccountId, receiverAccountId))
                .post("http://localhost:4111/api/v1/accounts/transfer");

        // Запрашиваем балансы после попытки перевода
        Response senderAccounts = given()
                .header("Authorization", senderUserAuthHeader)
                .accept(ContentType.JSON)
                .get("http://localhost:4111/api/v1/customer/accounts");

        Response receiverAccounts = given()
                .header("Authorization", receiverUserAuthHeader)
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
                .header("Authorization", senderUserAuthHeader)
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
                .header("Authorization", senderUserAuthHeader)
                .accept(ContentType.JSON)
                .get("http://localhost:4111/api/v1/customer/accounts");

        Response receiverAccounts = given()
                .header("Authorization", receiverUserAuthHeader)
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
                .header("Authorization", senderUserAuthHeader)
                .post("http://localhost:4111/api/v1/accounts")
                .then()
                .statusCode(HttpStatus.SC_CREATED)
                .body("id", Matchers.notNullValue())
                .extract()
                .path("id");

        // Проверяем, что счёт создан с нулевым балансом
        given()
                .header("Authorization", senderUserAuthHeader)
                .accept(ContentType.JSON)
                .get("http://localhost:4111/api/v1/customer/accounts")
                .then()
                .statusCode(HttpStatus.SC_OK)
                .body("id", Matchers.hasItem(secondOwnAccountId))
                .body("find { it.id == %d }.balance".formatted(secondOwnAccountId), Matchers.equalTo(0.0f));

        // Пополняем счёт отправителя
        given()
                .contentType(ContentType.JSON)
                .header("Authorization", senderUserAuthHeader)
                .body("""
                        {
                          "id": %d,
                          "balance": 5000
                        }
                        """.formatted(senderAccountId))
                .post("http://localhost:4111/api/v1/accounts/deposit")
                .then()
                .statusCode(HttpStatus.SC_OK);

        // Проверяем сохранённый баланс после пополнения
        given()
                .header("Authorization", senderUserAuthHeader)
                .accept(ContentType.JSON)
                .get("http://localhost:4111/api/v1/customer/accounts")
                .then()
                .statusCode(HttpStatus.SC_OK)
                .body("id", Matchers.hasItem(senderAccountId))
                .body("find { it.id == %d }.balance".formatted(senderAccountId), Matchers.equalTo(5000.0f));

        // Выполняем перевод
        given()
                .contentType(ContentType.JSON)
                .header("Authorization", senderUserAuthHeader)
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
                .header("Authorization", senderUserAuthHeader)
                .accept(ContentType.JSON)
                .get("http://localhost:4111/api/v1/customer/accounts")
                .then()
                .statusCode(HttpStatus.SC_OK)
                .body("find { it.id == %d }.balance".formatted(senderAccountId), Matchers.equalTo(1000.0f));

        given()
                .header("Authorization", senderUserAuthHeader)
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
                .header("Authorization", senderUserAuthHeader)
                .body("""
                        {
                          "id": %d,
                          "balance": 5000
                        }
                        """.formatted(senderAccountId))
                .post("http://localhost:4111/api/v1/accounts/deposit")
                .then()
                .statusCode(HttpStatus.SC_OK);

        // Проверяем сохранённый баланс после пополнения
        given()
                .header("Authorization", senderUserAuthHeader)
                .accept(ContentType.JSON)
                .get("http://localhost:4111/api/v1/customer/accounts")
                .then()
                .statusCode(HttpStatus.SC_OK)
                .body("id", Matchers.hasItem(senderAccountId))
                .body("find { it.id == %d }.balance".formatted(senderAccountId), Matchers.equalTo(5000.0f));

        // Выполняем перевод
        given()
                .contentType(ContentType.JSON)
                .header("Authorization", senderUserAuthHeader)
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
                .header("Authorization", senderUserAuthHeader)
                .accept(ContentType.JSON)
                .get("http://localhost:4111/api/v1/customer/accounts")
                .then()
                .statusCode(HttpStatus.SC_OK)
                .body("[0].balance", Matchers.equalTo(0.0f));

        given()
                .header("Authorization", receiverUserAuthHeader)
                .accept(ContentType.JSON)
                .get("http://localhost:4111/api/v1/customer/accounts")
                .then()
                .statusCode(HttpStatus.SC_OK)
                .body("[0].balance", Matchers.equalTo(5000f));
    }
}
