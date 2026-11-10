package requests;

import io.restassured.response.ValidatableResponse;
import io.restassured.specification.RequestSpecification;
import io.restassured.specification.ResponseSpecification;
import models.CustomerNameRequest;

import static io.restassured.RestAssured.given;

public class CustomerNameRequester {
    private static final String PROFILE_PATH = "/api/v1/customer/profile";

    private final RequestSpecification requestSpecification;
    private final ResponseSpecification responseSpecification;

    public CustomerNameRequester(RequestSpecification requestSpecification,
                                 ResponseSpecification responseSpecification) {
        this.requestSpecification = requestSpecification;
        this.responseSpecification = responseSpecification;
    }

    public ValidatableResponse put(CustomerNameRequest model) {
        return given()
                .spec(requestSpecification)
                .body(model)
                .put(PROFILE_PATH)
                .then()
                .spec(responseSpecification);
    }

    public ValidatableResponse get() {
        return given()
                .spec(requestSpecification)
                .get(PROFILE_PATH)
                .then()
                .spec(responseSpecification);
    }
}
