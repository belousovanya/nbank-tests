package iteration1.requests;

import io.restassured.response.ValidatableResponse;
import io.restassured.specification.RequestSpecification;
import io.restassured.specification.ResponseSpecification;
import iteration1.models.LoginUserRequest;

import static io.restassured.RestAssured.given;

public class LoginUserRequester extends Request<LoginUserRequest> {
    public LoginUserRequester(RequestSpecification requestSpecification, ResponseSpecification responseSpecification) {
        super(requestSpecification, responseSpecification);
    }

    @Override
    public ValidatableResponse post(LoginUserRequest model) {
        return given()
                .spec(requestSpecification)
                .body(model)
                .post("/api/v1/auth/login")
                .then()
                .assertThat()
                .spec(responseSpecification);
    }
}
