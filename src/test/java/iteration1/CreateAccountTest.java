package iteration1;

import io.restassured.specification.RequestSpecification;
import models.AccountResponse;
import models.CreateUserRequest;
import org.junit.jupiter.api.Test;
import requests.skelethon.Endpoint;
import requests.skelethon.requesters.ValidatedCrudRequester;
import requests.steps.AdminSteps;
import specs.RequestSpecs;
import specs.ResponseSpecs;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class CreateAccountTest {
    @Test
    public void userCanCreateAccountTest() {
        CreateUserRequest user = AdminSteps.createUser();
        RequestSpecification userSpec = RequestSpecs.authAsUser(user.getUsername(), user.getPassword());

        AccountResponse createdAccount = new ValidatedCrudRequester<AccountResponse>(
                userSpec,
                Endpoint.ACCOUNTS,
                ResponseSpecs.entityWasCreated())
                .post();

        List<AccountResponse> userAccounts = new ValidatedCrudRequester<AccountResponse>(
                userSpec,
                Endpoint.CUSTOMER_ACCOUNTS,
                ResponseSpecs.requestReturnsOK())
                .getAll();

        assertThat(userAccounts)
                .extracting(AccountResponse::getId)
                .contains(createdAccount.getId());
    }
}
