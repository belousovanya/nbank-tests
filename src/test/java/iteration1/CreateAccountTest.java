package iteration1;

import io.restassured.specification.RequestSpecification;
import generators.RandomData;
import models.AccountResponse;
import models.CreateUserRequest;
import models.UserRole;
import requests.AdminCreateUserRequester;
import requests.CreateAccountRequester;
import requests.GetAccountsRequester;
import specs.RequestSpecs;
import specs.ResponseSpecs;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class CreateAccountTest {
    @Test
    public void userCanCreateAccountTest() {
        CreateUserRequest userRequest = CreateUserRequest.builder()
                .username(RandomData.getUsername())
                .password(RandomData.getPassword())
                .role(UserRole.USER.name())
                .build();

        new AdminCreateUserRequester(RequestSpecs.adminSpec(), ResponseSpecs.entityWasCreated())
                .post(userRequest);

        RequestSpecification userSpec = RequestSpecs.authAsUser(
                userRequest.getUsername(), userRequest.getPassword());

        int accountId = new CreateAccountRequester(userSpec, ResponseSpecs.entityWasCreated())
                .post()
                .extract()
                .as(AccountResponse.class)
                .getId();

        AccountResponse[] accounts = new GetAccountsRequester(userSpec)
                .get()
                .extract()
                .as(AccountResponse[].class);

        assertThat(accounts)
                .extracting(AccountResponse::getId)
                .contains(accountId);
    }
}
