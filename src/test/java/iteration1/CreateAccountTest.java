package iteration1;

import io.restassured.specification.RequestSpecification;
import models.CreateAccountResponse;
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

        CreateAccountResponse createdAccount = new ValidatedCrudRequester<CreateAccountResponse>(
                userSpec,
                Endpoint.ACCOUNTS,
                ResponseSpecs.entityWasCreated())
                .post();

        List<CreateAccountResponse> userAccounts = new ValidatedCrudRequester<CreateAccountResponse>(
                userSpec,
                Endpoint.CUSTOMER_ACCOUNTS,
                ResponseSpecs.requestReturnsOK())
                .getAll();

        assertThat(userAccounts)
                .extracting(CreateAccountResponse::getId)
                .contains(createdAccount.getId());
    }
}
