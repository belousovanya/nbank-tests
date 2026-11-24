package requests.steps;

import io.restassured.specification.RequestSpecification;
import models.*;
import requests.skelethon.Endpoint;
import requests.skelethon.ValidatedRequests;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class UserSteps {
    private final ValidatedRequests requests;

    public UserSteps(RequestSpecification userSpec) {
        this.requests = new ValidatedRequests(userSpec);
    }

    public AccountResponse createAccount() {
        AccountResponse createdAccount = requests.<AccountResponse>request(Endpoint.ACCOUNTS).post();

        assertThat(getAccounts())
                .extracting(AccountResponse::getId)
                .contains(createdAccount.getId());

        return createdAccount;
    }

    public List<AccountResponse> getAccounts() {
        return requests.<AccountResponse>request(Endpoint.CUSTOMER_ACCOUNTS).getAll();
    }

    public CustomerResponse getProfile() {
        return requests.<CustomerResponse>request(Endpoint.GET_PROFILE).get();
    }

    public UpdateProfileResponse changeName(String name) {
        CustomerNameRequest request = CustomerNameRequest.builder().name(name).build();
        UpdateProfileResponse updateResponse = requests.<UpdateProfileResponse>request(Endpoint.UPDATE_PROFILE).put(request);

        assertThat(getProfile().getName()).isEqualTo(name);

        return updateResponse;
    }

    public AccountResponse deposit(int accountId, BigDecimal amount) {
        DepositRequest request = DepositRequest.builder()
                .id(accountId)
                .balance(amount)
                .build();

        AccountResponse depositResponse = requests.<AccountResponse>request(Endpoint.DEPOSIT).post(request);

        AccountResponse accountFromGet = getAccounts().stream()
                .filter(account -> account.getId() == accountId)
                .findFirst()
                .orElseThrow();
        assertThat(accountFromGet.getBalance()).isEqualByComparingTo(depositResponse.getBalance());

        return depositResponse;
    }
}