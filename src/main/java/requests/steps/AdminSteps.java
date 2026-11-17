package requests.steps;

import generators.RandomModelGenerator;
import models.CreateUserRequest;
import models.CreateUserResponse;
import requests.skelethon.Endpoint;
import requests.skelethon.ValidatedRequests;
import specs.RequestSpecs;

public class AdminSteps {
    public static CreateUserRequest createUser() {
        CreateUserRequest userRequest =
                RandomModelGenerator.generate(CreateUserRequest.class);

        new ValidatedRequests(RequestSpecs.adminSpec())
                .<CreateUserResponse>request(Endpoint.ADMIN_USER)
                .post(userRequest);

        return userRequest;
    }
}
