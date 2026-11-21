package iteration1;

import common.BaseTest;
import constants.UserError;
import generators.RandomModelGenerator;
import models.CreateUserRequest;
import models.CreateUserResponse;
import models.CustomerResponse;
import models.UserRole;
import models.comparison.ModelAssertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import requests.skelethon.Endpoint;
import requests.skelethon.ValidatedRequests;
import requests.skelethon.requesters.CrudRequester;
import requests.steps.UserSteps;
import specs.RequestSpecs;
import specs.ResponseSpecs;

import java.util.List;
import java.util.stream.Stream;

public class CreateUserTest extends BaseTest {
    private static final String VALID_USERNAME = "validuser";
    private static final String VALID_PASSWORD = "Password33$";
    private static final String VALID_ROLE = UserRole.USER.name();
    private static final String INVALID_ROLE = "EDITOR";

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

        CustomerResponse profile = new UserSteps(
                RequestSpecs.authAsUser(createUserRequest.getUsername(), createUserRequest.getPassword()))
                .getProfile();

        ModelAssertions.assertThatModels(createdUser, profile).match();
    }

    public static Stream<Arguments> userInvalidData() {
        return Stream.of(
                // username
                Arguments.of("   ", VALID_PASSWORD, VALID_ROLE, UserError.USERNAME_BLANK),
                Arguments.of("ab", VALID_PASSWORD, VALID_ROLE, UserError.USERNAME_LENGTH),
                Arguments.of("abcdefghijklmnop", VALID_PASSWORD, VALID_ROLE, UserError.USERNAME_LENGTH),
                Arguments.of("abc$", VALID_PASSWORD, VALID_ROLE, UserError.USERNAME_INVALID_CHARS),
                Arguments.of("abc%", VALID_PASSWORD, VALID_ROLE, UserError.USERNAME_INVALID_CHARS),
                Arguments.of("abc^", VALID_PASSWORD, VALID_ROLE, UserError.USERNAME_INVALID_CHARS),
                Arguments.of("abc&", VALID_PASSWORD, VALID_ROLE, UserError.USERNAME_INVALID_CHARS),
                Arguments.of("abc*", VALID_PASSWORD, VALID_ROLE, UserError.USERNAME_INVALID_CHARS),
                Arguments.of("abc(", VALID_PASSWORD, VALID_ROLE, UserError.USERNAME_INVALID_CHARS),
                Arguments.of("abc)", VALID_PASSWORD, VALID_ROLE, UserError.USERNAME_INVALID_CHARS),
                Arguments.of("abc@", VALID_PASSWORD, VALID_ROLE, UserError.USERNAME_INVALID_CHARS),
                Arguments.of("abc#", VALID_PASSWORD, VALID_ROLE, UserError.USERNAME_INVALID_CHARS),

                // password
                Arguments.of(VALID_USERNAME, "", VALID_ROLE, UserError.PASSWORD_BLANK),
                Arguments.of(VALID_USERNAME, "Aa1!abc", VALID_ROLE, UserError.PASSWORD_INVALID),      // 7 символов
                Arguments.of(VALID_USERNAME, "Password!", VALID_ROLE, UserError.PASSWORD_INVALID),    // без цифр
                Arguments.of(VALID_USERNAME, "PASSWORD33!", VALID_ROLE, UserError.PASSWORD_INVALID),  // без строчных букв
                Arguments.of(VALID_USERNAME, "password33!", VALID_ROLE, UserError.PASSWORD_INVALID),  // без заглавных букв
                Arguments.of(VALID_USERNAME, "Password33", VALID_ROLE, UserError.PASSWORD_INVALID),   // без спецсимвола
                Arguments.of(VALID_USERNAME, "Password 33!", VALID_ROLE, UserError.PASSWORD_INVALID), // с пробелом

                // role
                Arguments.of(VALID_USERNAME, VALID_PASSWORD, INVALID_ROLE, UserError.ROLE_INVALID)
        );
    }

    @MethodSource("userInvalidData")
    @ParameterizedTest
    public void adminCanNotCreateUserWithInvalidData(String username, String password, String role, UserError error) {
        CreateUserRequest createUserRequest = CreateUserRequest.builder()
                .username(username)
                .password(password)
                .role(role)
                .build();

        new CrudRequester(
                RequestSpecs.adminSpec(),
                Endpoint.ADMIN_USER,
                ResponseSpecs.requestReturnsBadRequest(error.getField(), error.getMessage()))
                .post(createUserRequest);

        List<CustomerResponse> users = new ValidatedRequests(RequestSpecs.adminSpec())
                .<CustomerResponse>request(Endpoint.ADMIN_ALL_USERS)
                .getAll();
        softly.assertThat(users)
                .extracting(CustomerResponse::getUsername)
                .doesNotContain(username);
    }
}
