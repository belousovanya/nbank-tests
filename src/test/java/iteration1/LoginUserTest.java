package iteration1;

import iteration1.generators.RandomData;
import iteration1.models.CreateUserRequest;
import iteration1.models.LoginUserRequest;
import iteration1.models.UserRole;
import iteration1.requests.AdminCreateUserRequester;
import iteration1.requests.LoginUserRequester;
import iteration1.specs.RequestSpecs;
import iteration1.specs.ResponseSpecs;
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
