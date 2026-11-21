package requests.skelethon;

import io.restassured.specification.RequestSpecification;
import models.BaseModel;
import requests.skelethon.requesters.ValidatedCrudRequester;
import specs.ResponseSpecs;

public class ValidatedRequests {
    private final RequestSpecification requestSpecification;

    public ValidatedRequests(RequestSpecification requestSpecification) {
        this.requestSpecification = requestSpecification;
    }

    public <T extends BaseModel> ValidatedCrudRequester<T> request(Endpoint endpoint) {
        return new ValidatedCrudRequester<>(
                requestSpecification,
                endpoint,
                ResponseSpecs.requestReturns(endpoint.getSuccessStatus()));
    }
}