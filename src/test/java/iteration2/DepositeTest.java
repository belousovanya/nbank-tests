package iteration2;

import common.BaseTest;
import generators.RandomData;
import io.restassured.specification.RequestSpecification;
import models.CreateUserRequest;
import models.DepositeRequest;
import models.DepositeResponse;
import models.UserRole;
import org.hamcrest.Matchers;
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
                .post(null)
                .body("id", Matchers.notNullValue())
                .extract()
                .path("id");
    }

    @ParameterizedTest
    @ValueSource(doubles = {0.1, 4999.9, 5000})
    public void userCanDepositValidAmount(double amount) {
        DepositeRequest depositeRequest = DepositeRequest.builder()
                .id(accountId)
                .balance(BigDecimal.valueOf(amount))
                .build();

        DepositeResponse depositeResponse = new DepositeRequester(
                userSpec, ResponseSpecs.requestReturnsOK())
                .post(depositeRequest)
                .extract()
                .as(DepositeResponse.class);

        softly.assertThat(depositeResponse.getBalance())
                .isEqualByComparingTo(depositeRequest.getBalance());

        DepositeResponse[] accounts = new GetAccountsRequester(userSpec)
                .get()
                .extract()
                .as(DepositeResponse[].class);

        softly.assertThat(accounts[0].getBalance())
                .isEqualByComparingTo(depositeRequest.getBalance());
    }


    @ParameterizedTest
    @ValueSource(doubles = {0, -0.1, 5000.1})
    public void userCanNotDepositInvalidAmount(double amount) {
        DepositeRequest depositeRequest = DepositeRequest.builder()
                .id(accountId)
                .balance(BigDecimal.valueOf(amount))
                .build();

        new DepositeRequester(userSpec, ResponseSpecs.requestReturnsBadRequest())
                .post(depositeRequest);

        DepositeResponse[] accounts = new GetAccountsRequester(userSpec)
                .get()
                .extract()
                .as(DepositeResponse[].class);

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
                .post(null)
                .body("id", Matchers.notNullValue())
                .extract()
                .path("id");

        DepositeRequest depositeRequest = DepositeRequest.builder()
                .id(anotherAccountId)
                .balance(BigDecimal.valueOf(5000))
                .build();

        new DepositeRequester(userSpec, ResponseSpecs.requestReturnsForbidden())
                .post(depositeRequest);

        DepositeResponse[] accounts = new GetAccountsRequester(anotherUserSpec)
                .get()
                .extract()
                .as(DepositeResponse[].class);

        softly.assertThat(accounts[0].getBalance())
                .isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    public void userCannotDepositToNonexistentAccount() {
        DepositeRequest depositeRequest = DepositeRequest.builder()
                .id(99999999)
                .balance(BigDecimal.valueOf(5000))
                .build();

        new DepositeRequester(userSpec, ResponseSpecs.requestReturnsForbidden())
                .post(depositeRequest);


        DepositeResponse[] accounts = new GetAccountsRequester(userSpec)
                .get()
                .extract()
                .as(DepositeResponse[].class);

        softly.assertThat(accounts[0].getBalance())
                .isEqualByComparingTo(BigDecimal.ZERO);
    }
}