package iteration1.api;

import common.BaseTest;
import constants.AuthConstants;
import io.restassured.response.ValidatableResponse;
import models.*;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import requests.skelethon.Endpoint;
import requests.skelethon.ValidatedRequests;
import requests.skelethon.requesters.CrudRequester;
import requests.steps.AdminSteps;
import requests.steps.UserSteps;
import specs.RequestSpecs;
import specs.ResponseSpecs;

import java.util.List;

public class LoginUserTest extends BaseTest {
    @Test
    public void adminCanGenerateAuthTokenTest() {
        LoginUserRequest adminLogin = LoginUserRequest.builder()
                .username(AuthConstants.ADMIN_USERNAME)
                .password(AuthConstants.ADMIN_PASSWORD)
                .build();

        ValidatableResponse loginResponse = new CrudRequester(
                RequestSpecs.unauthSpec(),
                Endpoint.LOGIN,
                ResponseSpecs.requestReturnsOK())
                .post(adminLogin);

        LoginUserResponse response = loginResponse.extract().as(LoginUserResponse.class);
        String authHeader = loginResponse.extract().header(AuthConstants.HEADER_AUTHORIZATION);

        softly.assertThat(response.getUsername()).isEqualTo(AuthConstants.ADMIN_USERNAME);
        softly.assertThat(response.getRole()).isEqualTo(UserRole.ADMIN.name());

        List<CustomerResponse> users = new ValidatedRequests(RequestSpecs.withAuthHeader(authHeader))
                .<CustomerResponse>request(Endpoint.ADMIN_ALL_USERS)
                .getAll();
        softly.assertThat(users).isNotEmpty();
    }

    @Test
    public void userCanGenerateAuthTokenTest() {
        CreateUserRequest user = AdminSteps.createUser();

        LoginUserRequest loginRequest = LoginUserRequest.builder()
                .username(user.getUsername())
                .password(user.getPassword())
                .build();

        String authHeader = new CrudRequester(
                RequestSpecs.unauthSpec(),
                Endpoint.LOGIN,
                ResponseSpecs.requestReturnsOK())
                .post(loginRequest)
                .header(AuthConstants.HEADER_AUTHORIZATION, Matchers.notNullValue())
                .extract()
                .header(AuthConstants.HEADER_AUTHORIZATION);

        CustomerResponse profile = new UserSteps(RequestSpecs.withAuthHeader(authHeader)).getProfile();
        softly.assertThat(profile.getUsername()).isEqualTo(user.getUsername());
    }
}