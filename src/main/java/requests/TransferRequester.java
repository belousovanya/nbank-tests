package requests;

import io.restassured.response.ValidatableResponse;
import io.restassured.specification.RequestSpecification;
import io.restassured.specification.ResponseSpecification;
import models.TransferRequest;
import specs.ResponseSpecs;

import static io.restassured.RestAssured.given;

public class TransferRequester extends Request<TransferRequest> {
    public TransferRequester(RequestSpecification requestSpecification, ResponseSpecification responseSpecification) {
        super(requestSpecification, responseSpecification);
    }

    @Override
    public ValidatableResponse post(TransferRequest model) {
        return given()
                .spec(requestSpecification)
                .body(model)
                .post("/api/v1/accounts/transfer")
                .then()
                .assertThat()
                .spec(responseSpecification);
    }
}