package iteration2;

import common.AccountLimits;
import common.BaseTest;
import generators.RandomData;
import io.restassured.specification.RequestSpecification;
import models.AccountResponse;
import models.CreateUserRequest;
import models.DepositeRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import requests.skelethon.Endpoint;
import requests.skelethon.ValidatedRequests;
import requests.skelethon.requesters.CrudRequester;
import requests.steps.AdminSteps;
import requests.steps.UserSteps;
import specs.RequestSpecs;
import specs.ResponseSpecs;

import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

public class DepositeTest extends BaseTest {
    private static final int NONEXISTENT_ACCOUNT_ID_MIN = 1_000_000;

    private RequestSpecification userSpec;
    private UserSteps userSteps;
    private int accountId;

    @BeforeEach
    public void prepareUserAndAccount() {
        CreateUserRequest user = AdminSteps.createUser();
        userSpec = RequestSpecs.authAsUser(user.getUsername(), user.getPassword());
        userSteps = new UserSteps(userSpec);
        accountId = userSteps.createAccount().getId();
    }

    @ParameterizedTest
    @ValueSource(doubles = {0.01, 4999.99, 5000})
    public void userCanDepositValidAmount(double amount) {
        DepositeRequest depositeRequest = DepositeRequest.builder()
                .id(accountId)
                .balance(BigDecimal.valueOf(amount))
                .build();

        AccountResponse depositeResponse = new ValidatedRequests(userSpec)
                .<AccountResponse>request(Endpoint.DEPOSIT)
                .post(depositeRequest);

        softly.assertThat(depositeResponse.getBalance())
                .isEqualByComparingTo(depositeRequest.getBalance());

        List<AccountResponse> accounts = userSteps.getAccounts();

        softly.assertThat(accounts.get(0).getBalance())
                .isEqualByComparingTo(depositeRequest.getBalance());
    }


    @ParameterizedTest
    @ValueSource(doubles = {0, -0.01, 5000.01})
    public void userCanNotDepositInvalidAmount(double amount) {
        DepositeRequest depositeRequest = DepositeRequest.builder()
                .id(accountId)
                .balance(BigDecimal.valueOf(amount))
                .build();

        new CrudRequester(userSpec, Endpoint.DEPOSIT, ResponseSpecs.requestReturnsBadRequest())
                .post(depositeRequest);

        List<AccountResponse> accounts = userSteps.getAccounts();

        softly.assertThat(accounts.get(0).getBalance())
                .isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    public void userCannotDepositToAnotherUsersAccount() {
        CreateUserRequest anotherUser = AdminSteps.createUser();
        RequestSpecification anotherUserSpec = RequestSpecs.authAsUser(anotherUser.getUsername(), anotherUser.getPassword());
        UserSteps anotherUserSteps = new UserSteps(anotherUserSpec);
        int anotherAccountId = anotherUserSteps.createAccount().getId();

        DepositeRequest depositeRequest = DepositeRequest.builder()
                .id(anotherAccountId)
                .balance(RandomData.getAmount(AccountLimits.MIN_AMOUNT, AccountLimits.MAX_DEPOSIT))
                .build();

        new CrudRequester(userSpec, Endpoint.DEPOSIT, ResponseSpecs.requestReturnsForbidden())
                .post(depositeRequest);

        List<AccountResponse> accounts = anotherUserSteps.getAccounts();

        softly.assertThat(accounts.get(0).getBalance())
                .isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    public void userCannotDepositToNonexistentAccount() {
        int nonexistentAccountId = ThreadLocalRandom.current().nextInt(NONEXISTENT_ACCOUNT_ID_MIN, Integer.MAX_VALUE);

        DepositeRequest depositeRequest = DepositeRequest.builder()
                .id(nonexistentAccountId)
                .balance(RandomData.getAmount(AccountLimits.MIN_AMOUNT, AccountLimits.MAX_DEPOSIT))
                .build();

        new CrudRequester(userSpec, Endpoint.DEPOSIT, ResponseSpecs.requestReturnsForbidden())
                .post(depositeRequest);

        List<AccountResponse> accounts = userSteps.getAccounts();

        softly.assertThat(accounts.get(0).getBalance())
                .isEqualByComparingTo(BigDecimal.ZERO);
    }
}