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

import java.util.List;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertAll;

public class ChangeUserNameTest {

    private String userAuthHeader;

    @BeforeAll
    public static void setupRestAssured() {
        RestAssured.filters(
                List.of(new RequestLoggingFilter(),
                        new ResponseLoggingFilter()));
    }

    @BeforeEach
    public void prepareUser() {
        String username = "user"
                + UUID.randomUUID().toString().substring(0, 10);
        String password = "verysTRongPassword33$";

        // 1. Создаём нового пользователя под админом
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

        // Задаём исходное имя, чтобы проверять, сохранилось ли оно при отказе
        given()
                .contentType(ContentType.JSON)
                .header("Authorization", userAuthHeader)
                .body("""
                        {
                          "name": "Jane Doe"
                        }
                        """)
                .put("http://localhost:4111/api/v1/customer/profile")
                .then()
                .statusCode(HttpStatus.SC_OK);
    }

    @Test
    public void userCanChangeNameToTwoWords() {
        // Меняем имя на два слова
        given()
                .contentType(ContentType.JSON)
                .header("Authorization", userAuthHeader)
                .body("""
                        {
                          "name": "Anna Smith"
                        }
                        """)
                .put("http://localhost:4111/api/v1/customer/profile")
                .then()
                .statusCode(HttpStatus.SC_OK);

        // Проверяем имя в сохранённом профиле
        given()
                .header("Authorization", userAuthHeader)
                .accept(ContentType.JSON)
                .get("http://localhost:4111/api/v1/customer/profile")
                .then()
                .statusCode(HttpStatus.SC_OK)
                .body("name", Matchers.equalTo("Anna Smith"));
    }

    @Test
    public void userCannotChangeNameToOneWord() {
        // Пытаемся установить некорректное имя
        Response updateResponse = given()
                .contentType(ContentType.JSON)
                .header("Authorization", userAuthHeader)
                .body("""
                        {
                          "name": "Anna"
                        }
                        """)
                .put("http://localhost:4111/api/v1/customer/profile");

        // Проверяем, что запрос отклонён и исходное имя сохранилось
        Response profileResponse = given()
                .header("Authorization", userAuthHeader)
                .accept(ContentType.JSON)
                .get("http://localhost:4111/api/v1/customer/profile");

        assertAll(
                () -> updateResponse.then().statusCode(HttpStatus.SC_BAD_REQUEST),
                () -> profileResponse.then()
                        .statusCode(HttpStatus.SC_OK)
                        .body("name", Matchers.equalTo("Jane Doe"))
        );
    }

    @Test
    public void userCannotChangeNameToThreeWords() {
        // Пытаемся установить некорректное имя
        Response updateResponse = given()
                .contentType(ContentType.JSON)
                .header("Authorization", userAuthHeader)
                .body("""
                        {
                          "name": "Anna Maria Smith"
                        }
                        """)
                .put("http://localhost:4111/api/v1/customer/profile");

        // Проверяем, что запрос отклонён и исходное имя сохранилось
        Response profileResponse = given()
                .header("Authorization", userAuthHeader)
                .accept(ContentType.JSON)
                .get("http://localhost:4111/api/v1/customer/profile");

        assertAll(
                () -> updateResponse.then().statusCode(HttpStatus.SC_BAD_REQUEST),
                () -> profileResponse.then()
                        .statusCode(HttpStatus.SC_OK)
                        .body("name", Matchers.equalTo("Jane Doe"))
        );
    }

    @Test
    public void userCannotChangeNameToOneContainingDigits() {
        // Пытаемся установить некорректное имя
        Response updateResponse = given()
                .contentType(ContentType.JSON)
                .header("Authorization", userAuthHeader)
                .body("""
                        {
                          "name": "Anna Sm1th"
                        }
                        """)
                .put("http://localhost:4111/api/v1/customer/profile");

        // Проверяем, что запрос отклонён и исходное имя сохранилось
        Response profileResponse = given()
                .header("Authorization", userAuthHeader)
                .accept(ContentType.JSON)
                .get("http://localhost:4111/api/v1/customer/profile");

        assertAll(
                () -> updateResponse.then().statusCode(HttpStatus.SC_BAD_REQUEST),
                () -> profileResponse.then()
                        .statusCode(HttpStatus.SC_OK)
                        .body("name", Matchers.equalTo("Jane Doe"))
        );
    }

    @Test
    public void userCannotChangeNameToOneContainingSpecialCharacters() {
        // Пытаемся установить некорректное имя
        Response updateResponse = given()
                .contentType(ContentType.JSON)
                .header("Authorization", userAuthHeader)
                .body("""
                        {
                          "name": "Anna-Smith"
                        }
                        """)
                .put("http://localhost:4111/api/v1/customer/profile");

        // Проверяем, что запрос отклонён и исходное имя сохранилось
        Response profileResponse = given()
                .header("Authorization", userAuthHeader)
                .accept(ContentType.JSON)
                .get("http://localhost:4111/api/v1/customer/profile");

        assertAll(
                () -> updateResponse.then().statusCode(HttpStatus.SC_BAD_REQUEST),
                () -> profileResponse.then()
                        .statusCode(HttpStatus.SC_OK)
                        .body("name", Matchers.equalTo("Jane Doe"))
        );
    }

    @Test
    public void userCannotChangeNameToWordsSeparatedByTwoSpaces() {
        // Пытаемся установить некорректное имя
        Response updateResponse = given()
                .contentType(ContentType.JSON)
                .header("Authorization", userAuthHeader)
                .body("""
                        {
                          "name": "Anna  Smith"
                        }
                        """)
                .put("http://localhost:4111/api/v1/customer/profile");

        // Проверяем, что запрос отклонён и исходное имя сохранилось
        Response profileResponse = given()
                .header("Authorization", userAuthHeader)
                .accept(ContentType.JSON)
                .get("http://localhost:4111/api/v1/customer/profile");

        assertAll(
                () -> updateResponse.then().statusCode(HttpStatus.SC_BAD_REQUEST),
                () -> profileResponse.then()
                        .statusCode(HttpStatus.SC_OK)
                        .body("name", Matchers.equalTo("Jane Doe"))
        );
    }

    @Test
    public void userCannotChangeNameToEmptyString() {
        // Пытаемся установить некорректное имя
        Response updateResponse = given()
                .contentType(ContentType.JSON)
                .header("Authorization", userAuthHeader)
                .body("""
                        {
                          "name": ""
                        }
                        """)
                .put("http://localhost:4111/api/v1/customer/profile");

        // Проверяем, что запрос отклонён и исходное имя сохранилось
        Response profileResponse = given()
                .header("Authorization", userAuthHeader)
                .accept(ContentType.JSON)
                .get("http://localhost:4111/api/v1/customer/profile");

        assertAll(
                () -> updateResponse.then().statusCode(HttpStatus.SC_BAD_REQUEST),
                () -> profileResponse.then()
                        .statusCode(HttpStatus.SC_OK)
                        .body("name", Matchers.equalTo("Jane Doe"))
        );
    }
}
