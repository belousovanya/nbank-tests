package requests.steps;

import io.restassured.specification.RequestSpecification;
import models.AccountResponse;
import requests.skelethon.Endpoint;
import requests.skelethon.ValidatedRequests;

import java.util.List;

public class UserSteps {
    private final ValidatedRequests requests;

    public UserSteps(RequestSpecification userSpec) {
        this.requests = new ValidatedRequests(userSpec);
    }

    public AccountResponse createAccount() {
        return requests.<AccountResponse>request(Endpoint.ACCOUNTS).post();
    }

    public List<AccountResponse> getAccounts() {
        return requests.<AccountResponse>request(Endpoint.CUSTOMER_ACCOUNTS).getAll();
    }
}
