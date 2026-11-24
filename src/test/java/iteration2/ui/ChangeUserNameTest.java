package iteration2.ui;

import com.codeborne.selenide.Condition;
import com.codeborne.selenide.Configuration;
import com.codeborne.selenide.Selectors;
import com.codeborne.selenide.Selenide;
import generators.RandomData;
import models.CreateUserRequest;
import models.LoginUserRequest;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.openqa.selenium.Alert;
import org.openqa.selenium.Keys;
import requests.skelethon.Endpoint;
import requests.skelethon.requesters.CrudRequester;
import requests.steps.AdminSteps;
import requests.steps.UserSteps;
import specs.RequestSpecs;
import specs.ResponseSpecs;

import java.util.Map;

import static com.codeborne.selenide.Selenide.*;
import static org.assertj.core.api.Assertions.assertThat;

public class ChangeUserNameTest {
    private CreateUserRequest user;
    private UserSteps userSteps;
    private String initialName;

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
    public void prepareUser() {
        user = AdminSteps.createUser();
        userSteps = new UserSteps(RequestSpecs.authAsUser(user.getUsername(), user.getPassword()));

        initialName = RandomData.getName();
        userSteps.changeName(initialName);
    }

    @Test
    public void userCanChangeName() {
        // ШАГ 1: логинимся под пользователем и открываем /edit-profile
        String userAuthHeader = new CrudRequester(
                RequestSpecs.unauthSpec(),
                Endpoint.LOGIN,
                ResponseSpecs.requestReturnsOK())
                .post(LoginUserRequest.builder().username(user.getUsername()).password(user.getPassword()).build())
                .extract()
                .header("Authorization");

        Selenide.open("/");

        executeJavaScript("localStorage.setItem('authToken', arguments[0]);", userAuthHeader);

        Selenide.open("/edit-profile");

        // ШАГ 2: ждём загрузки профиля, вводим новое имя и сохраняем
        String newName = RandomData.getName();

        $(Selectors.byAttribute("placeholder", "Enter new name")).shouldHave(Condition.value(initialName));
        $(Selectors.byAttribute("placeholder", "Enter new name")).setValue(newName);
        $(Selectors.byText("💾 Save Changes")).click();

        // ШАГ 3: проверка алерта
        Alert alert = switchTo().alert();
        String alertText = alert.getText();

        assertThat(alertText).contains("✅ Name updated successfully!");

        alert.accept();

        // ШАГ 4: проверка имени через API
        assertThat(userSteps.getProfile().getName()).isEqualTo(newName);

        // ШАГ 5: проверка имени на UI
        Selenide.open("/dashboard");

        $(Selectors.byClassName("welcome-text")).shouldHave(Condition.text("Welcome, " + newName + "!"));
    }

    @Test
    public void userCannotChangeNameToInvalidValue() {
        // ШАГ 1: логинимся под пользователем и открываем /edit-profile
        String userAuthHeader = new CrudRequester(
                RequestSpecs.unauthSpec(),
                Endpoint.LOGIN,
                ResponseSpecs.requestReturnsOK())
                .post(LoginUserRequest.builder().username(user.getUsername()).password(user.getPassword()).build())
                .extract()
                .header("Authorization");

        Selenide.open("/");

        executeJavaScript("localStorage.setItem('authToken', arguments[0]);", userAuthHeader);

        Selenide.open("/edit-profile");

        // ШАГ 2: ждём загрузки профиля, вводим невалидное имя (одно слово) и сохраняем
        String newName = RandomData.getNamePart();

        $(Selectors.byAttribute("placeholder", "Enter new name")).shouldHave(Condition.value(initialName));
        $(Selectors.byAttribute("placeholder", "Enter new name")).setValue(newName);
        $(Selectors.byText("💾 Save Changes")).click();

        // ШАГ 3: проверка алерта
        Alert alert = switchTo().alert();
        String alertText = alert.getText();

        assertThat(alertText).contains("Name must contain two words with letters only");

        alert.accept();

        // ШАГ 4: проверка через API, что имя не изменилось
        assertThat(userSteps.getProfile().getName()).isEqualTo(initialName);
    }

    @Test
    public void userCannotChangeNameToEmpty() {
        // ШАГ 1: логинимся под пользователем и открываем /edit-profile
        String userAuthHeader = new CrudRequester(
                RequestSpecs.unauthSpec(),
                Endpoint.LOGIN,
                ResponseSpecs.requestReturnsOK())
                .post(LoginUserRequest.builder().username(user.getUsername()).password(user.getPassword()).build())
                .extract()
                .header("Authorization");

        Selenide.open("/");

        executeJavaScript("localStorage.setItem('authToken', arguments[0]);", userAuthHeader);

        Selenide.open("/edit-profile");

        // ШАГ 2: ждём загрузки профиля и вводим пустое имя
        $(Selectors.byAttribute("placeholder", "Enter new name")).shouldHave(Condition.value(initialName));
        $(Selectors.byAttribute("placeholder", "Enter new name")).sendKeys(Keys.chord(Keys.CONTROL, "a"), Keys.BACK_SPACE);
        $(Selectors.byAttribute("placeholder", "Enter new name")).setValue("");
        $(Selectors.byText("💾 Save Changes")).click();

        // ШАГ 3: проверка алерта
        Alert alert = switchTo().alert();
        String alertText = alert.getText();

        assertThat(alertText).contains("❌ Please enter a valid name");

        alert.accept();

        // ШАГ 4: проверка через API, что имя не изменилось
        assertThat(userSteps.getProfile().getName()).isEqualTo(initialName);
    }

    @Test
    public void userCannotChangeNameToSameName() {
        // ШАГ 1: логинимся под пользователем и открываем /edit-profile
        String userAuthHeader = new CrudRequester(
                RequestSpecs.unauthSpec(),
                Endpoint.LOGIN,
                ResponseSpecs.requestReturnsOK())
                .post(LoginUserRequest.builder().username(user.getUsername()).password(user.getPassword()).build())
                .extract()
                .header("Authorization");

        Selenide.open("/");

        executeJavaScript("localStorage.setItem('authToken', arguments[0]);", userAuthHeader);

        Selenide.open("/edit-profile");

        // ШАГ 2: ждём загрузки профиля и вводим то же имя, что сейчас
        $(Selectors.byAttribute("placeholder", "Enter new name")).shouldHave(Condition.value(initialName));
        $(Selectors.byAttribute("placeholder", "Enter new name")).setValue(initialName);
        $(Selectors.byText("💾 Save Changes")).click();

        // ШАГ 3: проверка алерта
        Alert alert = switchTo().alert();
        String alertText = alert.getText();

        assertThat(alertText).contains("New name is the same as the current one");

        alert.accept();

        // ШАГ 4: проверка через API, что имя не изменилось
        assertThat(userSteps.getProfile().getName()).isEqualTo(initialName);
    }
}
