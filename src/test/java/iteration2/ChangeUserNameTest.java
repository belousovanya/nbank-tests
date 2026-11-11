package iteration2;

import common.BaseTest;
import generators.RandomData;
import io.restassured.specification.RequestSpecification;
import models.*;
import org.apache.commons.lang3.RandomStringUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import requests.AdminCreateUserRequester;
import requests.CustomerNameRequester;
import specs.RequestSpecs;
import specs.ResponseSpecs;

import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

public class ChangeUserNameTest extends BaseTest {
    private String initialName;

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
        initialName = RandomData.getName();
        CustomerNameRequest initialNameRequest = CustomerNameRequest.builder()
                .name(initialName)
                .build();

        new CustomerNameRequester(userSpec, ResponseSpecs.requestReturnsOK())
                .put(initialNameRequest);

        assertThat(getProfile().getName()).isEqualTo(initialName);
    }

    @Test
    public void userCanChangeNameToTwoWords() {
        String newName = RandomData.getName();
        CustomerNameRequest updateRequest = CustomerNameRequest.builder()
                .name(newName)
                .build();

        UpdateProfileResponse updateResponse = new CustomerNameRequester(userSpec, ResponseSpecs.requestReturnsOK())
                .put(updateRequest)
                .extract()
                .as(UpdateProfileResponse.class);

        softly.assertThat(updateResponse.getCustomer().getName()).isEqualTo(newName);
        softly.assertThat(getProfile().getName()).isEqualTo(newName);
    }

    public static Stream<String> invalidNames() {
        return Stream.of(
                RandomData.getNamePart(),
                RandomData.getName() + " " + RandomData.getNamePart(),
                RandomData.getName() + RandomStringUtils.randomNumeric(1),
                RandomData.getNamePart() + "-" + RandomData.getNamePart(),
                RandomData.getNamePart() + "  " + RandomData.getNamePart(),
                ""
        );
    }

    @ParameterizedTest(name = "[{index}] Недопустимое имя - {0}")
    @MethodSource("invalidNames")
    public void userCannotChangeNameToInvalidValue(String invalidName) {
        CustomerNameRequest updateRequest = CustomerNameRequest.builder()
                .name(invalidName)
                .build();

        new CustomerNameRequester(userSpec, ResponseSpecs.requestReturnsBadRequest())
                .put(updateRequest);

        softly.assertThat(getProfile().getName()).isEqualTo(initialName);
    }

    private CustomerResponse getProfile() {
        return new CustomerNameRequester(userSpec, ResponseSpecs.requestReturnsOK())
                .get()
                .extract()
                .as(CustomerResponse.class);
    }
}