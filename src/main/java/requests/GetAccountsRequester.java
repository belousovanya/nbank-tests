package requests;

import io.restassured.response.ValidatableResponse;
import io.restassured.specification.RequestSpecification;
import specs.ResponseSpecs;

import static io.restassured.RestAssured.given;

public class GetAccountsRequester {
    private final RequestSpecification requestSpecification;

    public GetAccountsRequester(RequestSpecification requestSpecification) {
        this.requestSpecification = requestSpecification;
    }

    public ValidatableResponse get() {
        return given()
                .spec(requestSpecification)
                .get("/customer/accounts")
                .then()
                .spec(ResponseSpecs.requestReturnsOK());
    }
}
