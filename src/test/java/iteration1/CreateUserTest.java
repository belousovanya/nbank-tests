package iteration1;

import io.restassured.RestAssured;
import io.restassured.filter.log.RequestLoggingFilter;
import io.restassured.filter.log.ResponseLoggingFilter;
import io.restassured.http.ContentType;
import org.apache.http.HttpStatus;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.List;
import java.util.stream.Stream;

import static io.restassured.RestAssured.given;

public class CreateUserTest {
    @BeforeAll
    public static void setupRestAssured() {
        RestAssured.filters(
                List.of(new RequestLoggingFilter(),
                        new ResponseLoggingFilter()));
    }

    @Test
    public void adminCanCreateUserWithCorrectData() {
        given()
                .contentType(ContentType.JSON)
                .accept(ContentType.JSON)
                .header("Authorization", "Basic YWRtaW46YWRtaW4=")
                .body("""
                        {
                          "username": "kate200011",
                          "password": "Kate2000#!",
                          "role": "USER"
                        }
                        """)
                .post("http://localhost:4111/api/v1/admin/users")
                .then()
                .assertThat()
                .statusCode(HttpStatus.SC_CREATED)
                .body("username", Matchers.equalTo("kate200011"))
                .body("password", Matchers.not(Matchers.equalTo("Kate2000#!")))
                .body("role", Matchers.equalTo("USER"));
    }

    public static Stream<Arguments> userInvalidData() {
        String passwordError = "Password must contain at least one digit, one lower case, one upper case, one special character, no spaces, and be at least 8 characters long";

        return Stream.of(
                // username field validation
                Arguments.of("   ", "Password33$", "USER", "username", "Username cannot be blank"),
                Arguments.of("ab", "Password33$", "USER", "username", "Username must be between 3 and 15 characters"),
                Arguments.of("abcdefghijklmnop", "Password33$", "USER", "username", "Username must be between 3 and 15 characters"),
                Arguments.of("abc$", "Password33$", "USER", "username", "Username must contain only letters, digits, dashes, underscores, and dots"),
                Arguments.of("abc%", "Password33$", "USER", "username", "Username must contain only letters, digits, dashes, underscores, and dots"),
                Arguments.of("abc^", "Password33$", "USER", "username", "Username must contain only letters, digits, dashes, underscores, and dots"),
                Arguments.of("abc&", "Password33$", "USER", "username", "Username must contain only letters, digits, dashes, underscores, and dots"),
                Arguments.of("abc*", "Password33$", "USER", "username", "Username must contain only letters, digits, dashes, underscores, and dots"),
                Arguments.of("abc(", "Password33$", "USER", "username", "Username must contain only letters, digits, dashes, underscores, and dots"),
                Arguments.of("abc)", "Password33$", "USER", "username", "Username must contain only letters, digits, dashes, underscores, and dots"),
                Arguments.of("abc@", "Password33$", "USER", "username", "Username must contain only letters, digits, dashes, underscores, and dots"),
                Arguments.of("abc#", "Password33$", "USER", "username", "Username must contain only letters, digits, dashes, underscores, and dots"),

                // password field validation
                Arguments.of("validuser", "", "USER", "password", "Password cannot be blank"),
                Arguments.of("validuser", "Aa1!abc", "USER", "password", passwordError), // 7 символов
                Arguments.of("validuser", "Password!", "USER", "password", passwordError), // без цифр
                Arguments.of("validuser", "PASSWORD33!", "USER", "password", passwordError), // без строчных букв
                Arguments.of("validuser", "password33!", "USER", "password", passwordError), // без заглавных букв
                Arguments.of("validuser", "Password33", "USER", "password", passwordError), // без спецсимвола
                Arguments.of("validuser", "Password 33!", "USER", "password", passwordError), // с пробелом

                // role field validation
                Arguments.of("validuser", "Password33$", "EDITOR", "role", "Role must be either 'ADMIN' or 'USER'")
        );

    }

    @MethodSource("userInvalidData")
    @ParameterizedTest
    public void adminCanNotCreateUserWithInvalidData(String username, String password, String role, String errorKey, String errorValue) {
        String requestBody = String.format(
                """
                        {
                          "username": "%s",
                          "password": "%s",
                          "role": "%s"
                        }
                        """, username, password, role);

        given()
                .contentType(ContentType.JSON)
                .accept(ContentType.JSON)
                .header("Authorization", "Basic YWRtaW46YWRtaW4=")
                .body(requestBody)
                .post("http://localhost:4111/api/v1/admin/users")
                .then()
                .assertThat()
                .statusCode(HttpStatus.SC_BAD_REQUEST)
                .body(errorKey, Matchers.hasItem(errorValue));
    }
}
