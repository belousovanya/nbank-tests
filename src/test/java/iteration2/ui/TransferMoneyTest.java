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
import org.openqa.selenium.Alert;
import requests.skelethon.Endpoint;
import requests.skelethon.requesters.CrudRequester;
import requests.steps.AdminSteps;
import requests.steps.UserSteps;
import specs.RequestSpecs;
import specs.ResponseSpecs;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Map;

import static com.codeborne.selenide.Selenide.*;
import static org.assertj.core.api.Assertions.assertThat;

public class TransferMoneyTest {
    private CreateUserRequest senderUser;
    private UserSteps senderSteps;
    private AccountResponse senderAccount;
    private BigDecimal senderInitialBalance;

    private UserSteps receiverSteps;
    private AccountResponse receiverAccount;
    private String receiverName;

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
        senderUser = AdminSteps.createUser();
        senderSteps = new UserSteps(RequestSpecs.authAsUser(senderUser.getUsername(), senderUser.getPassword()));
        senderAccount = senderSteps.createAccount();

        senderInitialBalance = RandomData.getAmount(AccountLimits.MIN_AMOUNT, AccountLimits.MAX_DEPOSIT);
        senderSteps.deposit(senderAccount.getId(), senderInitialBalance);

        CreateUserRequest receiverUser = AdminSteps.createUser();
        receiverSteps = new UserSteps(RequestSpecs.authAsUser(receiverUser.getUsername(), receiverUser.getPassword()));
        receiverAccount = receiverSteps.createAccount();

        receiverName = RandomData.getName();
        receiverSteps.changeName(receiverName);
    }

    @Test
    public void userCanTransferValidAmountToAnotherUsersAccount() {
        // ШАГ 1: логинимся под отправителем и открываем /transfer
        String userAuthHeader = new CrudRequester(
                RequestSpecs.unauthSpec(),
                Endpoint.LOGIN,
                ResponseSpecs.requestReturnsOK())
                .post(LoginUserRequest.builder().username(senderUser.getUsername()).password(senderUser.getPassword()).build())
                .extract()
                .header("Authorization");

        Selenide.open("/");

        executeJavaScript("localStorage.setItem('authToken', arguments[0]);", userAuthHeader);

        Selenide.open("/transfer");

        // ШАГ 2: заполняем форму и отправляем перевод
        BigDecimal amount = RandomData.getAmount(AccountLimits.MIN_AMOUNT, senderInitialBalance);

        $(Selectors.byClassName("account-selector")).selectOptionByValue(String.valueOf(senderAccount.getId()));
        $(Selectors.byAttribute("placeholder", "Enter recipient name")).sendKeys(receiverName);
        $(Selectors.byAttribute("placeholder", "Enter recipient account number")).sendKeys(receiverAccount.getAccountNumber());
        $(Selectors.byAttribute("placeholder", "Enter amount")).sendKeys(amount.toPlainString());
        $("#confirmCheck").click();
        $(Selectors.byText("🚀 Send Transfer")).click();

        // ШАГ 3: проверка алерта
        Alert alert = switchTo().alert();
        String alertText = alert.getText();

        assertThat(alertText).contains("Successfully transferred $" + amount.toPlainString() + " to account " + receiverAccount.getAccountNumber());

        alert.accept();

        // ШАГ 4: проверка через API: у отправителя списалось, получателю пришло
        assertThat(balanceOf(senderSteps)).isEqualByComparingTo(senderInitialBalance.subtract(amount));
        assertThat(balanceOf(receiverSteps)).isEqualByComparingTo(amount);
    }

    @Test
    public void userCannotTransferMoreThanBalance() {
        // ШАГ 1: логинимся под отправителем и открываем /transfer
        String userAuthHeader = new CrudRequester(
                RequestSpecs.unauthSpec(),
                Endpoint.LOGIN,
                ResponseSpecs.requestReturnsOK())
                .post(LoginUserRequest.builder().username(senderUser.getUsername()).password(senderUser.getPassword()).build())
                .extract()
                .header("Authorization");

        Selenide.open("/");

        executeJavaScript("localStorage.setItem('authToken', arguments[0]);", userAuthHeader);

        Selenide.open("/transfer");

        // ШАГ 2: пытаемся перевести на копейку больше, чем лежит на счёте (проверку делает бэкенд)
        BigDecimal amount = senderInitialBalance.add(AccountLimits.MIN_AMOUNT);

        $(Selectors.byClassName("account-selector")).selectOptionByValue(String.valueOf(senderAccount.getId()));
        $(Selectors.byAttribute("placeholder", "Enter recipient name")).sendKeys(receiverName);
        $(Selectors.byAttribute("placeholder", "Enter recipient account number")).sendKeys(receiverAccount.getAccountNumber());
        $(Selectors.byAttribute("placeholder", "Enter amount")).sendKeys(amount.toPlainString());
        $("#confirmCheck").click();
        $(Selectors.byText("🚀 Send Transfer")).click();

        // ШАГ 3: проверка алерта: фронт показал ошибку, которую вернул бэкенд
        Alert alert = switchTo().alert();
        String alertText = alert.getText();

        assertThat(alertText).contains("❌ Error: Invalid transfer: insufficient funds or invalid accounts");

        alert.accept();

        // ШАГ 4: проверка через API, что балансы не изменились
        assertThat(balanceOf(senderSteps)).isEqualByComparingTo(senderInitialBalance);
        assertThat(balanceOf(receiverSteps)).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    public void userCannotTransferWithoutConfirmation() {
        // ШАГ 1: логинимся под отправителем и открываем /transfer
        String userAuthHeader = new CrudRequester(
                RequestSpecs.unauthSpec(),
                Endpoint.LOGIN,
                ResponseSpecs.requestReturnsOK())
                .post(LoginUserRequest.builder().username(senderUser.getUsername()).password(senderUser.getPassword()).build())
                .extract()
                .header("Authorization");

        Selenide.open("/");

        executeJavaScript("localStorage.setItem('authToken', arguments[0]);", userAuthHeader);

        Selenide.open("/transfer");

        // ШАГ 2: заполняем все поля, но НЕ ставим галочку подтверждения
        BigDecimal amount = RandomData.getAmount(AccountLimits.MIN_AMOUNT, senderInitialBalance);

        $(Selectors.byClassName("account-selector")).selectOptionByValue(String.valueOf(senderAccount.getId()));
        $(Selectors.byAttribute("placeholder", "Enter recipient name")).sendKeys(receiverName);
        $(Selectors.byAttribute("placeholder", "Enter recipient account number")).sendKeys(receiverAccount.getAccountNumber());
        $(Selectors.byAttribute("placeholder", "Enter amount")).sendKeys(amount.toPlainString());
        $(Selectors.byText("🚀 Send Transfer")).click();

        // ШАГ 3: проверка алерта
        Alert alert = switchTo().alert();
        String alertText = alert.getText();

        assertThat(alertText).contains("Please fill all fields and confirm.");

        alert.accept();

        // ШАГ 4: проверка через API, что балансы не изменились
        assertThat(balanceOf(senderSteps)).isEqualByComparingTo(senderInitialBalance);
        assertThat(balanceOf(receiverSteps)).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    public void userCannotTransferToNonexistentAccount() {
        // ШАГ 1: логинимся под отправителем и открываем /transfer
        String userAuthHeader = new CrudRequester(
                RequestSpecs.unauthSpec(),
                Endpoint.LOGIN,
                ResponseSpecs.requestReturnsOK())
                .post(LoginUserRequest.builder().username(senderUser.getUsername()).password(senderUser.getPassword()).build())
                .extract()
                .header("Authorization");

        Selenide.open("/");

        executeJavaScript("localStorage.setItem('authToken', arguments[0]);", userAuthHeader);

        Selenide.open("/transfer");

        // ШАГ 2: вводим номер счёта, которого нет в банке
        BigDecimal amount = RandomData.getAmount(AccountLimits.MIN_AMOUNT, senderInitialBalance);
        String nonexistentAccountNumber = "ACC999999999";

        $(Selectors.byClassName("account-selector")).selectOptionByValue(String.valueOf(senderAccount.getId()));
        $(Selectors.byAttribute("placeholder", "Enter recipient name")).sendKeys(receiverName);
        $(Selectors.byAttribute("placeholder", "Enter recipient account number")).sendKeys(nonexistentAccountNumber);
        $(Selectors.byAttribute("placeholder", "Enter amount")).sendKeys(amount.toPlainString());
        $("#confirmCheck").click();
        $(Selectors.byText("🚀 Send Transfer")).click();

        // ШАГ 3: проверка алерта
        Alert alert = switchTo().alert();
        String alertText = alert.getText();

        assertThat(alertText).contains("No user found with this account number.");

        alert.accept();

        // ШАГ 4: проверка через API, что балансы не изменились
        assertThat(balanceOf(senderSteps)).isEqualByComparingTo(senderInitialBalance);
        assertThat(balanceOf(receiverSteps)).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    public void userCannotTransferWhenRecipientNameDoesNotMatch() {
        // ШАГ 1: логинимся под отправителем и открываем /transfer
        String userAuthHeader = new CrudRequester(
                RequestSpecs.unauthSpec(),
                Endpoint.LOGIN,
                ResponseSpecs.requestReturnsOK())
                .post(LoginUserRequest.builder().username(senderUser.getUsername()).password(senderUser.getPassword()).build())
                .extract()
                .header("Authorization");

        Selenide.open("/");

        executeJavaScript("localStorage.setItem('authToken', arguments[0]);", userAuthHeader);

        Selenide.open("/transfer");

        // ШАГ 2: вводим чужое имя получателя
        BigDecimal amount = RandomData.getAmount(AccountLimits.MIN_AMOUNT, senderInitialBalance);
        String wrongRecipientName = RandomData.getName();

        $(Selectors.byClassName("account-selector")).selectOptionByValue(String.valueOf(senderAccount.getId()));
        $(Selectors.byAttribute("placeholder", "Enter recipient name")).sendKeys(wrongRecipientName);
        $(Selectors.byAttribute("placeholder", "Enter recipient account number")).sendKeys(receiverAccount.getAccountNumber());
        $(Selectors.byAttribute("placeholder", "Enter amount")).sendKeys(amount.toPlainString());
        $("#confirmCheck").click();
        $(Selectors.byText("🚀 Send Transfer")).click();

        // ШАГ 3: проверка алерта
        Alert alert = switchTo().alert();
        String alertText = alert.getText();

        assertThat(alertText).contains("The recipient name does not match the registered name.");

        alert.accept();

        // ШАГ 4: проверка через API, что балансы не изменились
        assertThat(balanceOf(senderSteps)).isEqualByComparingTo(senderInitialBalance);
        assertThat(balanceOf(receiverSteps)).isEqualByComparingTo(BigDecimal.ZERO);
    }


    @Test
    public void userCannotTransferWithoutSelectedAccount() {
        // ШАГ 1: логинимся под отправителем и открываем /transfer
        String userAuthHeader = new CrudRequester(
                RequestSpecs.unauthSpec(),
                Endpoint.LOGIN,
                ResponseSpecs.requestReturnsOK())
                .post(LoginUserRequest.builder().username(senderUser.getUsername()).password(senderUser.getPassword()).build())
                .extract()
                .header("Authorization");

        Selenide.open("/");

        executeJavaScript("localStorage.setItem('authToken', arguments[0]);", userAuthHeader);

        Selenide.open("/transfer");

        // ШАГ 2: заполняем всё, кроме счёта отправителя (в списке остаётся «-- Choose an account --»)
        BigDecimal amount = RandomData.getAmount(AccountLimits.MIN_AMOUNT, senderInitialBalance);

        $(Selectors.byAttribute("placeholder", "Enter recipient name")).sendKeys(receiverName);
        $(Selectors.byAttribute("placeholder", "Enter recipient account number")).sendKeys(receiverAccount.getAccountNumber());
        $(Selectors.byAttribute("placeholder", "Enter amount")).sendKeys(amount.toPlainString());
        $("#confirmCheck").click();
        $(Selectors.byText("🚀 Send Transfer")).click();

        // ШАГ 3: проверка алерта
        Alert alert = switchTo().alert();
        String alertText = alert.getText();

        assertThat(alertText).contains("Please fill all fields and confirm.");

        alert.accept();

        // ШАГ 4: проверка через API, что балансы не изменились
        assertThat(balanceOf(senderSteps)).isEqualByComparingTo(senderInitialBalance);
        assertThat(balanceOf(receiverSteps)).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    public void userCannotTransferWithEmptyRecipientAccount() {
        // ШАГ 1: логинимся под отправителем и открываем /transfer
        String userAuthHeader = new CrudRequester(
                RequestSpecs.unauthSpec(),
                Endpoint.LOGIN,
                ResponseSpecs.requestReturnsOK())
                .post(LoginUserRequest.builder().username(senderUser.getUsername()).password(senderUser.getPassword()).build())
                .extract()
                .header("Authorization");

        Selenide.open("/");

        executeJavaScript("localStorage.setItem('authToken', arguments[0]);", userAuthHeader);

        Selenide.open("/transfer");

        // ШАГ 2: заполняем всё, кроме номера счёта получателя
        BigDecimal amount = RandomData.getAmount(AccountLimits.MIN_AMOUNT, senderInitialBalance);

        $(Selectors.byClassName("account-selector")).selectOptionByValue(String.valueOf(senderAccount.getId()));
        $(Selectors.byAttribute("placeholder", "Enter recipient name")).sendKeys(receiverName);
        $(Selectors.byAttribute("placeholder", "Enter amount")).sendKeys(amount.toPlainString());
        $("#confirmCheck").click();
        $(Selectors.byText("🚀 Send Transfer")).click();

        // ШАГ 3: проверка алерта
        Alert alert = switchTo().alert();
        String alertText = alert.getText();

        assertThat(alertText).contains("Please fill all fields and confirm.");

        alert.accept();

        // ШАГ 4: проверка через API, что балансы не изменились
        assertThat(balanceOf(senderSteps)).isEqualByComparingTo(senderInitialBalance);
        assertThat(balanceOf(receiverSteps)).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    public void userCannotTransferWithEmptyAmount() {
        // ШАГ 1: логинимся под отправителем и открываем /transfer
        String userAuthHeader = new CrudRequester(
                RequestSpecs.unauthSpec(),
                Endpoint.LOGIN,
                ResponseSpecs.requestReturnsOK())
                .post(LoginUserRequest.builder().username(senderUser.getUsername()).password(senderUser.getPassword()).build())
                .extract()
                .header("Authorization");

        Selenide.open("/");

        executeJavaScript("localStorage.setItem('authToken', arguments[0]);", userAuthHeader);

        Selenide.open("/transfer");

        // ШАГ 2: заполняем всё, кроме суммы
        $(Selectors.byClassName("account-selector")).selectOptionByValue(String.valueOf(senderAccount.getId()));
        $(Selectors.byAttribute("placeholder", "Enter recipient name")).sendKeys(receiverName);
        $(Selectors.byAttribute("placeholder", "Enter recipient account number")).sendKeys(receiverAccount.getAccountNumber());
        $("#confirmCheck").click();
        $(Selectors.byText("🚀 Send Transfer")).click();

        // ШАГ 3: проверка алерта
        Alert alert = switchTo().alert();
        String alertText = alert.getText();

        assertThat(alertText).contains("Please fill all fields and confirm.");

        alert.accept();

        // ШАГ 4: проверка через API, что балансы не изменились
        assertThat(balanceOf(senderSteps)).isEqualByComparingTo(senderInitialBalance);
        assertThat(balanceOf(receiverSteps)).isEqualByComparingTo(BigDecimal.ZERO);
    }

    private BigDecimal balanceOf(UserSteps steps) {
        return steps.getAccounts().get(0).getBalance().setScale(AccountLimits.MONEY_SCALE, RoundingMode.HALF_UP);
    }
}
