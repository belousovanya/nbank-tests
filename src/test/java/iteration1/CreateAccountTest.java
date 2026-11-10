package iteration1;

import io.restassured.specification.RequestSpecification;
import generators.RandomData;
import models.CreateUserRequest;
import models.UserRole;
import requests.AdminCreateUserRequester;
import requests.CreateAccountRequester;
import specs.RequestSpecs;
import specs.ResponseSpecs;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;

public class CreateAccountTest {
    @Test
    public void userCanCreateAccountTest() {
        CreateUserRequest userRequest = CreateUserRequest.builder()
                .username(RandomData.getUsername())
                .password(RandomData.getPassword())
                .role(UserRole.USER.name())
                .build();

        new AdminCreateUserRequester(RequestSpecs.adminSpec(), ResponseSpecs.entityWasCreated())
                .post(userRequest);

        RequestSpecification userSpec = RequestSpecs.authAsUser(
                userRequest.getUsername(), userRequest.getPassword());

        int accountId = new CreateAccountRequester(userSpec, ResponseSpecs.entityWasCreated())
                .post(null)
                .body("id", Matchers.notNullValue())
                .extract()
                .path("id");

        given()
                .spec(userSpec)
                .get("/api/v1/customer/accounts")
                .then()
                .spec(ResponseSpecs.requestReturnsOK())
                .body("id", Matchers.hasItem(accountId));
    }
}
