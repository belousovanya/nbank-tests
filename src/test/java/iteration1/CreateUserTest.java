package iteration1;

import common.BaseTest;
import generators.RandomModelGenerator;
import models.CreateUserRequest;
import models.CreateUserResponse;
import models.comparison.ModelAssertions;
import requests.skelethon.Endpoint;
import requests.skelethon.requesters.CrudRequester;
import requests.skelethon.ValidatedRequests;
import specs.RequestSpecs;
import specs.ResponseSpecs;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

public class CreateUserTest extends BaseTest {
    @Test
    public void adminCanCreateUserWithCorrectData() {
        CreateUserRequest createUserRequest = RandomModelGenerator.generate(CreateUserRequest.class);

        CreateUserResponse createdUser = new ValidatedRequests(RequestSpecs.adminSpec())
                .<CreateUserResponse>request(Endpoint.ADMIN_USER)
                .post(createUserRequest);

        // username и role сравниваются по правилам из model-comparison.properties
        ModelAssertions.assertThatModels(createUserRequest, createdUser).match();
        // пароль возвращается захэшированным, поэтому он НЕ должен совпадать с исходным
        softly.assertThat(createdUser.getPassword()).isNotEqualTo(createUserRequest.getPassword());
    }

    public static Stream<Arguments> userInvalidData() {
        String passwordError = "Password must contain at least one digit, one lower case, one upper case, one special character, no spaces, and be at least 8 characters long";

        return Stream.of(
                // username
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

                // password
                Arguments.of("validuser", "", "USER", "password", "Password cannot be blank"),
                Arguments.of("validuser", "Aa1!abc", "USER", "password", passwordError), // 7 символов
                Arguments.of("validuser", "Password!", "USER", "password", passwordError), // без цифр
                Arguments.of("validuser", "PASSWORD33!", "USER", "password", passwordError), // без строчных букв
                Arguments.of("validuser", "password33!", "USER", "password", passwordError), // без заглавных букв
                Arguments.of("validuser", "Password33", "USER", "password", passwordError), // без спецсимвола
                Arguments.of("validuser", "Password 33!", "USER", "password", passwordError), // с пробелом

                // role
                Arguments.of("validuser", "Password33$", "EDITOR", "role", "Role must be either 'ADMIN' or 'USER'")
        );

    }

    @MethodSource("userInvalidData")
    @ParameterizedTest
    public void adminCanNotCreateUserWithInvalidData(String username, String password, String role, String errorKey, String errorValue) {
        CreateUserRequest createUserRequest = CreateUserRequest.builder()
                .username(username)
                .password(password)
                .role(role)
                .build();

        new CrudRequester(
                RequestSpecs.adminSpec(),
                Endpoint.ADMIN_USER,
                ResponseSpecs.requestReturnsBadRequest(errorKey, errorValue))
                .post(createUserRequest);
    }
}
