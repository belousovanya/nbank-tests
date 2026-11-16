package iteration2;

import common.AccountLimits;
import common.BaseTest;
import generators.RandomData;
import io.restassured.specification.RequestSpecification;
import models.CreateAccountResponse;
import models.CreateUserRequest;
import models.CustomerResponse;
import models.DepositeRequest;
import models.UserRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import requests.AdminCreateUserRequester;
import requests.CreateAccountRequester;
import requests.CustomerNameRequester;
import requests.DepositeRequester;
import requests.GetAccountsRequester;
import specs.RequestSpecs;
import specs.ResponseSpecs;

import java.math.BigDecimal;
import java.util.concurrent.ThreadLocalRandom;

import static org.assertj.core.api.Assertions.assertThat;

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

        CustomerResponse createdUser = new AdminCreateUserRequester(RequestSpecs.adminSpec(), ResponseSpecs.entityWasCreated())
                .post(userRequest)
                .extract()
                .as(CustomerResponse.class);

        // 2. Авторизуемся созданным пользователем
        userSpec = RequestSpecs.authAsUser(
                userRequest.getUsername(), userRequest.getPassword());

        // Проверяем через GET, что пользователь действительно создан
        CustomerResponse userProfile = new CustomerNameRequester(userSpec, ResponseSpecs.requestReturnsOK())
                .get()
                .extract()
                .as(CustomerResponse.class);
        assertThat(userProfile.getId()).isEqualTo(createdUser.getId());
        assertThat(userProfile.getUsername()).isEqualTo(userRequest.getUsername());
        assertThat(userProfile.getRole()).isEqualTo(userRequest.getRole());

        // 3. Создаем его счёт и сохраняем ID из ответа
        accountId = new CreateAccountRequester(userSpec, ResponseSpecs.entityWasCreated())
                .post()
                .extract()
                .as(CreateAccountResponse.class).getId();
    }

    @ParameterizedTest
    @ValueSource(doubles = {0.01, 4999.99, 5000})
    public void userCanDepositValidAmount(double amount) {
        DepositeRequest depositeRequest = DepositeRequest.builder()
                .id(accountId)
                .balance(BigDecimal.valueOf(amount))
                .build();

        CreateAccountResponse depositeResponse = new DepositeRequester(
                userSpec, ResponseSpecs.requestReturnsOK())
                .post(depositeRequest)
                .extract()
                .as(CreateAccountResponse.class);

        softly.assertThat(depositeResponse.getBalance())
                .isEqualByComparingTo(depositeRequest.getBalance());

        CreateAccountResponse[] accounts = new GetAccountsRequester(userSpec)
                .get()
                .extract()
                .as(CreateAccountResponse[].class);

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

        CreateAccountResponse[] accounts = new GetAccountsRequester(userSpec)
                .get()
                .extract()
                .as(CreateAccountResponse[].class);

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

        CustomerResponse createdAnotherUser = new AdminCreateUserRequester(RequestSpecs.adminSpec(), ResponseSpecs.entityWasCreated())
                .post(anotherUserRequest)
                .extract()
                .as(CustomerResponse.class);

        // 2. Авторизуемся созданным пользователем
        RequestSpecification anotherUserSpec = RequestSpecs.authAsUser(
                anotherUserRequest.getUsername(), anotherUserRequest.getPassword());

        // Проверяем через GET, что пользователь действительно создан
        CustomerResponse anotherUserProfile = new CustomerNameRequester(anotherUserSpec, ResponseSpecs.requestReturnsOK())
                .get()
                .extract()
                .as(CustomerResponse.class);
        assertThat(anotherUserProfile.getId()).isEqualTo(createdAnotherUser.getId());
        assertThat(anotherUserProfile.getUsername()).isEqualTo(anotherUserRequest.getUsername());
        assertThat(anotherUserProfile.getRole()).isEqualTo(anotherUserRequest.getRole());

        // 3. Создаем его счёт и сохраняем ID из ответа
        int anotherAccountId = new CreateAccountRequester(anotherUserSpec, ResponseSpecs.entityWasCreated())
                .post()
                .extract()
                .as(CreateAccountResponse.class).getId();

        DepositeRequest depositeRequest = DepositeRequest.builder()
                .id(anotherAccountId)
                .balance(RandomData.getAmount(AccountLimits.MIN_AMOUNT, AccountLimits.MAX_DEPOSIT))
                .build();

        new DepositeRequester(userSpec, ResponseSpecs.requestReturnsForbidden())
                .post(depositeRequest);

        CreateAccountResponse[] accounts = new GetAccountsRequester(anotherUserSpec)
                .get()
                .extract()
                .as(CreateAccountResponse[].class);

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


        CreateAccountResponse[] accounts = new GetAccountsRequester(userSpec)
                .get()
                .extract()
                .as(CreateAccountResponse[].class);

        softly.assertThat(accounts[0].getBalance())
                .isEqualByComparingTo(BigDecimal.ZERO);
    }
}