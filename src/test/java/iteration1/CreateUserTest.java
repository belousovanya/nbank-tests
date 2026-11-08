package iteration1;

import io.restassured.builder.ResponseSpecBuilder;
import io.restassured.specification.ResponseSpecification;
import iteration1.generators.RandomData;
import iteration1.models.CreateUserRequest;
import iteration1.models.CreateUserResponse;
import iteration1.models.UserRole;
import iteration1.requests.AdminCreateUserRequester;
import iteration1.specs.RequestSpecs;
import iteration1.specs.ResponseSpecs;
import org.apache.http.HttpStatus;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

public class CreateUserTest extends BaseTest {
    @Test
    public void adminCanCreateUserWithCorrectData() {
        CreateUserRequest userRequest = CreateUserRequest.builder()
                .username(RandomData.getUsername())
                .password(RandomData.getPassword())
                .role(UserRole.USER.name())
                .build();

        CreateUserResponse createdUser = new AdminCreateUserRequester(
                RequestSpecs.adminSpec(), ResponseSpecs.entityWasCreated())
                .post(userRequest)
                .extract()
                .as(CreateUserResponse.class);

        softly.assertThat(createdUser.getUsername()).isEqualTo(userRequest.getUsername());
        softly.assertThat(createdUser.getPassword()).isNotEqualTo(userRequest.getPassword());
        softly.assertThat(createdUser.getRole()).isEqualTo(userRequest.getRole());
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
        CreateUserRequest userRequest = CreateUserRequest.builder()
                .username(username)
                .password(password)
                .role(role)
                .build();

        ResponseSpecification validationError = new ResponseSpecBuilder()
                .expectStatusCode(HttpStatus.SC_BAD_REQUEST)
                .expectBody(errorKey, Matchers.hasItem(errorValue))
                .build();

        new AdminCreateUserRequester(RequestSpecs.adminSpec(), validationError)
                .post(userRequest);
    }
}
