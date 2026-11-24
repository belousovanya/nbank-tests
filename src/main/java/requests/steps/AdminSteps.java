package requests.steps;

import generators.RandomModelGenerator;
import models.CreateUserRequest;
import models.CreateUserResponse;
import models.CustomerResponse;
import models.comparison.ModelAssertions;
import requests.skelethon.Endpoint;
import requests.skelethon.ValidatedRequests;
import specs.RequestSpecs;

public class AdminSteps {
    public static CreateUserRequest createUser() {
        CreateUserRequest userRequest =
                RandomModelGenerator.generate(CreateUserRequest.class);

        CreateUserResponse createdUser = new ValidatedRequests(RequestSpecs.adminSpec())
                .<CreateUserResponse>request(Endpoint.ADMIN_USER)
                .post(userRequest);

        CustomerResponse profile = new ValidatedRequests(RequestSpecs.authAsUser(userRequest.getUsername(), userRequest.getPassword()))
                .<CustomerResponse>request(Endpoint.GET_PROFILE)
                .get();

        ModelAssertions.assertThatModels(createdUser, profile).match();

        return userRequest;
    }
}
