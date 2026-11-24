package iteration2.ui;

import com.codeborne.selenide.Configuration;
import com.codeborne.selenide.Selectors;
import com.codeborne.selenide.Selenide;
import common.AccountLimits;
import generators.RandomData;
import models.AccountResponse;
import models.CreateUserRequest;
import models.LoginUserRequest;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.openqa.selenium.Alert;
import requests.skelethon.Endpoint;
import requests.skelethon.requesters.CrudRequester;
import requests.steps.AdminSteps;
import requests.steps.UserSteps;
import specs.RequestSpecs;
import specs.ResponseSpecs;

import java.math.BigDecimal;
import java.util.Map;

import static com.codeborne.selenide.Selenide.*;
import static org.assertj.core.api.Assertions.assertThat;

public class DepositTest {
    private CreateUserRequest user;
    private AccountResponse account;
    private UserSteps userSteps;

    @BeforeAll
    public static void setupSelenoid() {
        Configuration.remote = "http://localhost:4444/wd/hub";
        Configuration.baseUrl = "http://192.168.31.194:3000";
        Configuration.browser = "chrome";
        Configuration.browserSize = "1920x1080";

        Configuration.browserCapabilities.setCapability("selenoid:options",
                Map.of("enableVNC", true, "enableLog", true));
    }

    @BeforeEach
    public void prepareUserAndAccount() {
        user = AdminSteps.createUser();
        userSteps = new UserSteps(RequestSpecs.authAsUser(user.getUsername(), user.getPassword()));
        account = userSteps.createAccount();
    }

    @Test
    public void userCanDepositMoneyIntoAccount() {
        // ШАГ 1: логинимся под пользователем и открываем /deposit
        String userAuthHeader = new CrudRequester(
                RequestSpecs.unauthSpec(),
                Endpoint.LOGIN,
                ResponseSpecs.requestReturnsOK())
                .post(LoginUserRequest.builder().username(user.getUsername()).password(user.getPassword()).build())
                .extract()
                .header("Authorization");

        Selenide.open("/");

        executeJavaScript("localStorage.setItem('authToken', arguments[0]);", userAuthHeader);

        Selenide.open("/deposit");

        // ШАГ 2: Выбираем созданный аккаунт и пополняем счет
        $(Selectors.byClassName("account-selector")).selectOptionByValue(String.valueOf(account.getId()));

        BigDecimal amount = RandomData.getAmount(AccountLimits.MIN_AMOUNT, AccountLimits.MAX_DEPOSIT);

        $(Selectors.byAttribute("placeholder", "Enter amount")).sendKeys(amount.toPlainString());
        $(Selectors.byText("💵 Deposit")).click();

        // ШАГ 3: проверка алерта
        Alert alert = switchTo().alert();
        String alertText = alert.getText();

        assertThat(alertText).contains("✅ Successfully deposited $" + amount.toPlainString() + " to account " + account.getAccountNumber());

        alert.accept();

        // ШАГ 4: проверка, что баланс изменился в API
        AccountResponse accountAfterDeposit = userSteps.getAccounts().get(0);
        assertThat(accountAfterDeposit.getBalance()).isEqualByComparingTo(amount);
    }

    @Test
    public void userCanDepositMaxAmount() {
        // ШАГ 1: логинимся под пользователем и открываем /deposit
        String userAuthHeader = new CrudRequester(
                RequestSpecs.unauthSpec(),
                Endpoint.LOGIN,
                ResponseSpecs.requestReturnsOK())
                .post(LoginUserRequest.builder().username(user.getUsername()).password(user.getPassword()).build())
                .extract()
                .header("Authorization");

        Selenide.open("/");

        executeJavaScript("localStorage.setItem('authToken', arguments[0]);", userAuthHeader);

        Selenide.open("/deposit");

        // ШАГ 2: выбираем счёт и пополняем на максимальную сумму
        $(Selectors.byClassName("account-selector")).selectOptionByValue(String.valueOf(account.getId()));

        BigDecimal amount = new BigDecimal("5000");

        $(Selectors.byAttribute("placeholder", "Enter amount")).sendKeys(amount.toPlainString());
        $(Selectors.byText("💵 Deposit")).click();

        // ШАГ 3: проверка алерта
        Alert alert = switchTo().alert();
        String alertText = alert.getText();

        assertThat(alertText).contains("✅ Successfully deposited $" + amount.toPlainString() + " to account " + account.getAccountNumber());

        alert.accept();

        // ШАГ 4: проверка, что баланс изменился в API
        AccountResponse accountAfterDeposit = userSteps.getAccounts().get(0);
        assertThat(accountAfterDeposit.getBalance()).isEqualByComparingTo(amount);
    }

    @Test
    public void userCannotDepositMoreThanMaxAmount() {
        // ШАГ 1: логинимся под пользователем и открываем /deposit
        String userAuthHeader = new CrudRequester(
                RequestSpecs.unauthSpec(),
                Endpoint.LOGIN,
                ResponseSpecs.requestReturnsOK())
                .post(LoginUserRequest.builder().username(user.getUsername()).password(user.getPassword()).build())
                .extract()
                .header("Authorization");

        Selenide.open("/");

        executeJavaScript("localStorage.setItem('authToken', arguments[0]);", userAuthHeader);

        Selenide.open("/deposit");

        // ШАГ 2: выбираем счёт и пытаемся пополнить больше лимита
        $(Selectors.byClassName("account-selector")).selectOptionByValue(String.valueOf(account.getId()));

        BigDecimal amount = new BigDecimal("5000.01");

        $(Selectors.byAttribute("placeholder", "Enter amount")).sendKeys(amount.toPlainString());
        $(Selectors.byText("💵 Deposit")).click();

        // ШАГ 3: проверка алерта
        Alert alert = switchTo().alert();
        String alertText = alert.getText();

        assertThat(alertText).contains("❌ Please deposit less or equal to 5000$");

        alert.accept();

        // ШАГ 4: проверка, что баланс не изменился в API
        AccountResponse accountAfterDeposit = userSteps.getAccounts().get(0);
        assertThat(accountAfterDeposit.getBalance()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @ParameterizedTest(name = "[{index}] Недопустимая сумма: ''{0}''")
    @ValueSource(strings = {"", "0", "-0.01"})
    public void userCannotDepositInvalidAmount(String amount) {
        // ШАГ 1: логинимся под пользователем и открываем /deposit
        String userAuthHeader = new CrudRequester(
                RequestSpecs.unauthSpec(),
                Endpoint.LOGIN,
                ResponseSpecs.requestReturnsOK())
                .post(LoginUserRequest.builder().username(user.getUsername()).password(user.getPassword()).build())
                .extract()
                .header("Authorization");

        Selenide.open("/");

        executeJavaScript("localStorage.setItem('authToken', arguments[0]);", userAuthHeader);

        Selenide.open("/deposit");

        // ШАГ 2: выбираем счёт и пытаемся пополнить баланс невалидными значениями
        $(Selectors.byClassName("account-selector")).selectOptionByValue(String.valueOf(account.getId()));

        $(Selectors.byAttribute("placeholder", "Enter amount")).sendKeys(amount);
        $(Selectors.byText("💵 Deposit")).click();

        // ШАГ 3: проверка алерта
        Alert alert = switchTo().alert();
        String alertText = alert.getText();

        assertThat(alertText).contains("❌ Please enter a valid amount");

        alert.accept();

        // ШАГ 4: проверка, что баланс не изменился в API
        AccountResponse accountAfterDeposit = userSteps.getAccounts().get(0);
        assertThat(accountAfterDeposit.getBalance()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    public void userCannotDepositWithoutSelectedAccount() {
        // ШАГ 1: логинимся под пользователем и открываем /deposit
        String userAuthHeader = new CrudRequester(
                RequestSpecs.unauthSpec(),
                Endpoint.LOGIN,
                ResponseSpecs.requestReturnsOK())
                .post(LoginUserRequest.builder().username(user.getUsername()).password(user.getPassword()).build())
                .extract()
                .header("Authorization");

        Selenide.open("/");

        executeJavaScript("localStorage.setItem('authToken', arguments[0]);", userAuthHeader);

        Selenide.open("/deposit");

        // ШАГ 2: Оставляем счёт пустым и пытаемся пополнить баланс
        BigDecimal amount = RandomData.getAmount(AccountLimits.MIN_AMOUNT, AccountLimits.MAX_DEPOSIT);

        $(Selectors.byAttribute("placeholder", "Enter amount")).sendKeys(amount.toPlainString());
        $(Selectors.byText("💵 Deposit")).click();

        // ШАГ 3: проверка алерта
        Alert alert = switchTo().alert();
        String alertText = alert.getText();

        assertThat(alertText).contains("❌ Please select an account");

        alert.accept();

        // ШАГ 4: проверка, что баланс не изменился в API
        AccountResponse accountAfterDeposit = userSteps.getAccounts().get(0);
        assertThat(accountAfterDeposit.getBalance()).isEqualByComparingTo(BigDecimal.ZERO);
    }
}
