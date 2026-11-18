package iteration1;

import common.BaseTest;
import models.CreateUserRequest;
import models.LoginUserRequest;
import models.LoginUserResponse;
import models.UserRole;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import requests.skelethon.Endpoint;
import requests.skelethon.requesters.CrudRequester;
import requests.skelethon.ValidatedRequests;
import requests.steps.AdminSteps;
import specs.RequestSpecs;
import specs.ResponseSpecs;

public class LoginUserTest extends BaseTest {
    @Test
    public void adminCanGenerateAuthTokenTest() {
        LoginUserRequest adminLogin = LoginUserRequest.builder()
                .username("admin")
                .password("admin")
                .build();

        LoginUserResponse response = new ValidatedRequests(RequestSpecs.unauthSpec())
                .<LoginUserResponse>request(Endpoint.LOGIN)
                .post(adminLogin);

        softly.assertThat(response.getUsername()).isEqualTo("admin");
        softly.assertThat(response.getRole()).isEqualTo(UserRole.ADMIN.name());
    }

    @Test
    public void userCanGenerateAuthTokenTest() {
        CreateUserRequest user = AdminSteps.createUser();

        LoginUserRequest loginRequest = LoginUserRequest.builder()
                .username(user.getUsername())
                .password(user.getPassword())
                .build();

        new CrudRequester(
                RequestSpecs.unauthSpec(),
                Endpoint.LOGIN,
                ResponseSpecs.requestReturnsOK())
                .post(loginRequest)
                .header("Authorization", Matchers.notNullValue());
    }
}
