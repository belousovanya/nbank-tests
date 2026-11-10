package iteration2;

import generators.RandomData;
import io.restassured.specification.RequestSpecification;
import models.CreateUserRequest;
import models.CustomerNameRequest;
import models.UserRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import requests.AdminCreateUserRequester;
import requests.CustomerNameRequester;
import specs.RequestSpecs;
import specs.ResponseSpecs;

import static org.assertj.core.api.Assertions.assertThat;

public class ChangeUserNameTest {
    private static final String INITIAL_NAME = "Jane Doe";

    private RequestSpecification userSpec;

    @BeforeEach
    public void prepareUser() {
        CreateUserRequest userRequest = CreateUserRequest.builder()
                .username(RandomData.getUsername())
                .password(RandomData.getPassword())
                .role(UserRole.USER.name())
                .build();

        new AdminCreateUserRequester(RequestSpecs.adminSpec(), ResponseSpecs.entityWasCreated())
                .post(userRequest);

        userSpec = RequestSpecs.authAsUser(
                userRequest.getUsername(), userRequest.getPassword());

        // Задаём исходное имя для проверки после отклонённого обновления
        CustomerNameRequest initialNameRequest = CustomerNameRequest.builder()
                .name(INITIAL_NAME)
                .build();

        new CustomerNameRequester(userSpec, ResponseSpecs.requestReturnsOK())
                .put(initialNameRequest);
    }

    @Test
    public void userCanChangeNameToTwoWords() {
        String newName = "Anna Smith";
        CustomerNameRequest updateRequest = CustomerNameRequest.builder()
                .name(newName)
                .build();

        new CustomerNameRequester(userSpec, ResponseSpecs.requestReturnsOK())
                .put(updateRequest);

        assertThat(getProfileName()).isEqualTo(newName);
    }

    @ParameterizedTest(name = "[{index}] Недопустимое имя - {0}")
    @ValueSource(strings = {"Anna", "Anna Maria Smith", "Anna Sm1th", "Anna-Smith", "Anna  Smith", ""})
    public void userCannotChangeNameToInvalidValue(String invalidName) {
        CustomerNameRequest updateRequest = CustomerNameRequest.builder()
                .name(invalidName)
                .build();

        new CustomerNameRequester(userSpec, ResponseSpecs.requestReturnsBadRequest())
                .put(updateRequest);

        assertThat(getProfileName()).isEqualTo(INITIAL_NAME);
    }

    private String getProfileName() {
        return new CustomerNameRequester(userSpec, ResponseSpecs.requestReturnsOK())
                .get()
                .extract()
                .path("name");
    }
}
