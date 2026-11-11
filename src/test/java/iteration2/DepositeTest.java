package iteration2;

import common.AccountLimits;
import common.BaseTest;
import generators.RandomData;
import io.restassured.specification.RequestSpecification;
import models.AccountResponse;
import models.CreateUserRequest;
import models.DepositeRequest;
import models.UserRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import requests.AdminCreateUserRequester;
import requests.CreateAccountRequester;
import requests.DepositeRequester;
import requests.GetAccountsRequester;
import specs.RequestSpecs;
import specs.ResponseSpecs;

import java.math.BigDecimal;
import java.util.concurrent.ThreadLocalRandom;

public class DepositeTest extends BaseTest {

    private RequestSpecification userSpec;
    private int accountId;

    @BeforeEach
    public void prepareUserAndAccount() {
        // 1. Создаем нового пользователя под админом
        CreateUserRequest userRequest = CreateUserRequest.builder()
                .username(RandomData.getUsername())
                .password(RandomData.getPassword())
                .role(UserRole.USER.name())
                .build();

        new AdminCreateUserRequester(RequestSpecs.adminSpec(), ResponseSpecs.entityWasCreated())
                .post(userRequest);

        // 2. Авторизуемся созданным пользователем
        userSpec = RequestSpecs.authAsUser(
                userRequest.getUsername(), userRequest.getPassword());

        // 3. Создаем его счёт и сохраняем ID из ответа
        accountId = new CreateAccountRequester(userSpec, ResponseSpecs.entityWasCreated())
                .post()
                .extract()
                .as(AccountResponse.class).getId();
    }

    @ParameterizedTest
    @ValueSource(doubles = {0.01, 4999.99, 5000})
    public void userCanDepositValidAmount(double amount) {
        DepositeRequest depositeRequest = DepositeRequest.builder()
                .id(accountId)
                .balance(BigDecimal.valueOf(amount))
                .build();

        AccountResponse depositeResponse = new DepositeRequester(
                userSpec, ResponseSpecs.requestReturnsOK())
                .post(depositeRequest)
                .extract()
                .as(AccountResponse.class);

        softly.assertThat(depositeResponse.getBalance())
                .isEqualByComparingTo(depositeRequest.getBalance());

        AccountResponse[] accounts = new GetAccountsRequester(userSpec)
                .get()
                .extract()
                .as(AccountResponse[].class);

        softly.assertThat(accounts[0].getBalance())
                .isEqualByComparingTo(depositeRequest.getBalance());
    }


    @ParameterizedTest
    @ValueSource(doubles = {0, -0.01, 5000.01})
    public void userCanNotDepositInvalidAmount(double amount) {
        DepositeRequest depositeRequest = DepositeRequest.builder()
                .id(accountId)
                .balance(BigDecimal.valueOf(amount))
                .build();

        new DepositeRequester(userSpec, ResponseSpecs.requestReturnsBadRequest())
                .post(depositeRequest);

        AccountResponse[] accounts = new GetAccountsRequester(userSpec)
                .get()
                .extract()
                .as(AccountResponse[].class);

        softly.assertThat(accounts[0].getBalance())
                .isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    public void userCannotDepositToAnotherUsersAccount() {
        CreateUserRequest anotherUserRequest = CreateUserRequest.builder()
                .username(RandomData.getUsername())
                .password(RandomData.getPassword())
                .role(UserRole.USER.name())
                .build();

        new AdminCreateUserRequester(RequestSpecs.adminSpec(), ResponseSpecs.entityWasCreated())
                .post(anotherUserRequest);

        // 2. Авторизуемся созданным пользователем
        RequestSpecification anotherUserSpec = RequestSpecs.authAsUser(
                anotherUserRequest.getUsername(), anotherUserRequest.getPassword());

        // 3. Создаем его счёт и сохраняем ID из ответа
        int anotherAccountId = new CreateAccountRequester(anotherUserSpec, ResponseSpecs.entityWasCreated())
                .post()
                .extract()
                .as(AccountResponse.class).getId();

        DepositeRequest depositeRequest = DepositeRequest.builder()
                .id(anotherAccountId)
                .balance(RandomData.getAmount(AccountLimits.MIN_AMOUNT, AccountLimits.MAX_DEPOSIT))
                .build();

        new DepositeRequester(userSpec, ResponseSpecs.requestReturnsForbidden())
                .post(depositeRequest);

        AccountResponse[] accounts = new GetAccountsRequester(anotherUserSpec)
                .get()
                .extract()
                .as(AccountResponse[].class);

        softly.assertThat(accounts[0].getBalance())
                .isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    public void userCannotDepositToNonexistentAccount() {
        // Счетов с такими большими id в системе нет
        int nonexistentAccountId = ThreadLocalRandom.current().nextInt(1_000_000, Integer.MAX_VALUE);

        DepositeRequest depositeRequest = DepositeRequest.builder()
                .id(nonexistentAccountId)
                .balance(RandomData.getAmount(AccountLimits.MIN_AMOUNT, AccountLimits.MAX_DEPOSIT))
                .build();

        new DepositeRequester(userSpec, ResponseSpecs.requestReturnsForbidden())
                .post(depositeRequest);


        AccountResponse[] accounts = new GetAccountsRequester(userSpec)
                .get()
                .extract()
                .as(AccountResponse[].class);

        softly.assertThat(accounts[0].getBalance())
                .isEqualByComparingTo(BigDecimal.ZERO);
    }
}