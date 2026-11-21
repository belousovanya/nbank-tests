package iteration1;

import io.restassured.specification.RequestSpecification;
import models.AccountResponse;
import models.CreateUserRequest;
import org.junit.jupiter.api.Test;
import requests.skelethon.Endpoint;
import requests.skelethon.ValidatedRequests;
import requests.steps.AdminSteps;
import requests.steps.UserSteps;
import specs.RequestSpecs;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class CreateAccountTest {
    @Test
    public void userCanCreateAccountTest() {
        CreateUserRequest user = AdminSteps.createUser();
        RequestSpecification userSpec = RequestSpecs.authAsUser(user.getUsername(), user.getPassword());

        AccountResponse createdAccount = new ValidatedRequests(userSpec)
                .<AccountResponse>request(Endpoint.ACCOUNTS)
                .post();

        List<AccountResponse> userAccounts = new UserSteps(userSpec).getAccounts();

        assertThat(userAccounts)
                .extracting(AccountResponse::getId)
                .contains(createdAccount.getId());
    }
}
