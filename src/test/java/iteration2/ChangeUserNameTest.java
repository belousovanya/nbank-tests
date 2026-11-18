package iteration2;

import common.BaseTest;
import generators.RandomData;
import generators.RandomModelGenerator;
import io.restassured.specification.RequestSpecification;
import models.CreateUserRequest;
import models.CustomerNameRequest;
import models.UpdateProfileResponse;
import org.apache.commons.lang3.RandomStringUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import requests.skelethon.Endpoint;
import requests.skelethon.ValidatedRequests;
import requests.skelethon.requesters.CrudRequester;
import requests.steps.AdminSteps;
import requests.steps.UserSteps;
import specs.RequestSpecs;
import specs.ResponseSpecs;

import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

public class ChangeUserNameTest extends BaseTest {
    private String initialName;
    private RequestSpecification userSpec;
    private UserSteps userSteps;

    @BeforeEach
    public void prepareUser() {
        CreateUserRequest user = AdminSteps.createUser();
        userSpec = RequestSpecs.authAsUser(user.getUsername(), user.getPassword());
        userSteps = new UserSteps(userSpec);

        initialName = RandomModelGenerator.generate(CustomerNameRequest.class).getName();
        userSteps.changeName(initialName);
        assertThat(userSteps.getProfile().getName()).isEqualTo(initialName);
    }

    @Test
    public void userCanChangeNameToTwoWords() {
        CustomerNameRequest updateRequest = RandomModelGenerator.generate(CustomerNameRequest.class);

        UpdateProfileResponse updateResponse = new ValidatedRequests(userSpec)
                .<UpdateProfileResponse>request(Endpoint.UPDATE_PROFILE)
                .put(updateRequest);

        softly.assertThat(updateResponse.getCustomer().getName()).isEqualTo(updateRequest.getName());
        softly.assertThat(userSteps.getProfile().getName()).isEqualTo(updateRequest.getName());
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

        new CrudRequester(userSpec, Endpoint.UPDATE_PROFILE, ResponseSpecs.requestReturnsBadRequest())
                .put(updateRequest);

        softly.assertThat(userSteps.getProfile().getName()).isEqualTo(initialName);
    }
}
