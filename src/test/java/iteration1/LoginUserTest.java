package iteration1;

import generators.RandomData;
import models.CreateUserRequest;
import models.LoginUserRequest;
import models.UserRole;
import requests.AdminCreateUserRequester;
import requests.LoginUserRequester;
import specs.RequestSpecs;
import specs.ResponseSpecs;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;

public class LoginUserTest {
    @Test
    public void adminCanGenerateAuthTokenTest() {
        LoginUserRequest userRequest = LoginUserRequest.builder()
                .username("admin")
                .password("admin")
                .build();

        new LoginUserRequester(RequestSpecs.unauthSpec(),
                ResponseSpecs.requestReturnsOK())
                .post(userRequest)
                .header("Authorization", "Basic YWRtaW46YWRtaW4=");
    }

    @Test
    public void userCanGenerateAuthTokenTest() {
        CreateUserRequest userRequest = CreateUserRequest.builder()
                .username(RandomData.getUsername())
                .password(RandomData.getPassword())
                .role(UserRole.USER.name())
                .build();

        new AdminCreateUserRequester(RequestSpecs.adminSpec(), ResponseSpecs.entityWasCreated())
                .post(userRequest);

        LoginUserRequest loginRequest = LoginUserRequest.builder()
                .username(userRequest.getUsername())
                .password(userRequest.getPassword())
                .build();

        new LoginUserRequester(RequestSpecs.unauthSpec(), ResponseSpecs.requestReturnsOK())
                .post(loginRequest)
                .header("Authorization", Matchers.notNullValue());
    }
}
