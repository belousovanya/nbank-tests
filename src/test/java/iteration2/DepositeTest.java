package iteration2;

import io.restassured.RestAssured;
import io.restassured.filter.log.RequestLoggingFilter;
import io.restassured.filter.log.ResponseLoggingFilter;
import io.restassured.http.ContentType;
import org.apache.http.HttpStatus;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;
import java.util.UUID;

import static io.restassured.RestAssured.given;

public class DepositeTest {

    private String userAuthHeader;
    private int accountId;

    @BeforeAll
    public static void setupRestAssured() {
        RestAssured.filters(
                List.of(new RequestLoggingFilter(),
                        new ResponseLoggingFilter()));
    }

    @BeforeEach
    public void prepareUserAndAccount() {
        // Предусловие
        String username = "user"
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

        // 2. Авторизуемся созданным пользователем
        userAuthHeader = given()
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

        // 3. Создаем его счёт и сохраняем ID из ответа
        accountId = given()
                .contentType(ContentType.JSON)
                .header("Authorization", userAuthHeader)
                .post("http://localhost:4111/api/v1/accounts")
                .then()
                .statusCode(HttpStatus.SC_CREATED)
                .body("id", Matchers.notNullValue())
                .extract()
                .path("id");
    }

    @ParameterizedTest
    @ValueSource(doubles = {0.1, 4999.9, 5000})
    public void userCanDepositValidAmount(double amount) {
        // 1. Пополняем счёт
        given()
                .contentType(ContentType.JSON)
                .header("Authorization", userAuthHeader)
                .body("""
                        {
                          "id": %d,
                          "balance": %s
                        }
                        """.formatted(accountId, amount))
                .post("http://localhost:4111/api/v1/accounts/deposit")
                .then()
                .statusCode(HttpStatus.SC_OK)
                .body("balance", Matchers.equalTo((float) amount));

        // 2. Проверяем баланс
        given()
                .header("Authorization", userAuthHeader)
                .accept(ContentType.JSON)
                .get("http://localhost:4111/api/v1/customer/accounts")
                .then()
                .statusCode(HttpStatus.SC_OK)
                .body("[0].balance", Matchers.equalTo((float) amount));
    }


    @ParameterizedTest
    @ValueSource(doubles = {0, -0.1, 5000.1})
    public void userCanNotDepositInvalidAmount(double amount) {
        // 1. Пополняем счёт
        given()
                .contentType(ContentType.JSON)
                .header("Authorization", userAuthHeader)
                .body("""
                        {
                          "id": %d,
                          "balance": %s
                        }
                        """.formatted(accountId, amount))
                .post("http://localhost:4111/api/v1/accounts/deposit")
                .then()
                .statusCode(HttpStatus.SC_BAD_REQUEST);

        // 2. Проверяем баланс (должен остаться 0)
        given()
                .header("Authorization", userAuthHeader)
                .accept(ContentType.JSON)
                .get("http://localhost:4111/api/v1/customer/accounts")
                .then()
                .statusCode(HttpStatus.SC_OK)
                .body("[0].balance", Matchers.equalTo(0.0f));
    }

    @Test
    public void userCannotDepositToAnotherUsersAccount() {
        // Создаём второго пользователя
        String anotherUsername = "user"
                + UUID.randomUUID().toString().substring(0, 10);
        String anotherPassword = "verysTRongPassword33$";

        // Создаем нового пользователя под админом
        given()
                .contentType(ContentType.JSON)
                .header("Authorization", "Basic YWRtaW46YWRtaW4=")
                .body("""
                        {
                        "username": "%s",
                        "password": "%s",
                        "role": "USER"
                        }
                        """.formatted(anotherUsername, anotherPassword))
                .post("http://localhost:4111/api/v1/admin/users")
                .then()
                .statusCode(HttpStatus.SC_CREATED);

        // Авторизуемся
        String anotherUserAuthHeader = given()
                .contentType(ContentType.JSON)
                .body("""
                        {
                        "username": "%s",
                        "password": "%s"
                        }
                        """.formatted(anotherUsername, anotherPassword))
                .post("http://localhost:4111/api/v1/auth/login")
                .then()
                .statusCode(HttpStatus.SC_OK)
                .header("Authorization", Matchers.notNullValue())
                .extract()
                .header("Authorization");

        // Cоздаём счёт другого пользователя
        int anotherAccountId = given()
                .contentType(ContentType.JSON)
                .header("Authorization", anotherUserAuthHeader)
                .post("http://localhost:4111/api/v1/accounts")
                .then()
                .statusCode(HttpStatus.SC_CREATED)
                .body("id", Matchers.notNullValue())
                .extract()
                .path("id");

        // Основной пользователь пытается пополнить чужой счёт
        // 1. Пополняем счёт
        given()
                .contentType(ContentType.JSON)
                .header("Authorization", userAuthHeader)
                .body("""
                        {
                          "id": %d,
                          "balance": %s
                        }
                        """.formatted(anotherAccountId, 5000))
                .post("http://localhost:4111/api/v1/accounts/deposit")
                .then()
                .statusCode(HttpStatus.SC_FORBIDDEN);

        // 2. Проверяем баланс
        given()
                .header("Authorization", anotherUserAuthHeader)
                .accept(ContentType.JSON)
                .get("http://localhost:4111/api/v1/customer/accounts")
                .then()
                .statusCode(HttpStatus.SC_OK)
                .body("[0].balance", Matchers.equalTo(0.0f));
    }

    @Test
    public void userCannotDepositToNonexistentAccount() {
        // 1. Пополняем счёт
        given()
                .contentType(ContentType.JSON)
                .header("Authorization", userAuthHeader)
                .body("""
                        {
                          "id": %d,
                          "balance": %s
                        }
                        """.formatted(999999999, 5000))
                .post("http://localhost:4111/api/v1/accounts/deposit")
                .then()
                .statusCode(HttpStatus.SC_FORBIDDEN);

        // 2. Проверяем баланс
        given()
                .header("Authorization", userAuthHeader)
                .accept(ContentType.JSON)
                .get("http://localhost:4111/api/v1/customer/accounts")
                .then()
                .statusCode(HttpStatus.SC_OK)
                .body("[0].balance", Matchers.equalTo(0.0f));
    }
}