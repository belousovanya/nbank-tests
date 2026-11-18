package requests.steps;

import io.restassured.specification.RequestSpecification;
import models.*;
import requests.skelethon.Endpoint;
import requests.skelethon.ValidatedRequests;

import java.math.BigDecimal;
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

    public CustomerResponse getProfile() {
        return requests.<CustomerResponse>request(Endpoint.GET_PROFILE).get();
    }

    public UpdateProfileResponse changeName(String name) {
        CustomerNameRequest request = CustomerNameRequest.builder().name(name).build();
        return requests.<UpdateProfileResponse>request(Endpoint.UPDATE_PROFILE).put(request);
    }

    public AccountResponse deposit(int accountId, BigDecimal amount) {
        DepositeRequest request = DepositeRequest.builder()
                .id(accountId)
                .balance(amount)
                .build();

        return requests.<AccountResponse>request(Endpoint.DEPOSIT).post(request);
    }
}
