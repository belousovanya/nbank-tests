package requests.skelethon;

import lombok.AllArgsConstructor;
import lombok.Getter;
import models.*;
import org.apache.http.HttpStatus;

@Getter
@AllArgsConstructor
public enum Endpoint {
    ADMIN_USER(
            "/admin/users",
            CreateUserRequest.class,
            CreateUserResponse.class,
            HttpStatus.SC_CREATED
    ),

    ADMIN_ALL_USERS(
            "/admin/users",
            BaseModel.class,
            CustomerResponse.class,
            HttpStatus.SC_OK
    ),

    LOGIN(
            "/auth/login",
            LoginUserRequest.class,
            LoginUserResponse.class,
            HttpStatus.SC_OK
    ),

    ACCOUNTS(
            "/accounts",
            BaseModel.class,
            AccountResponse.class,
            HttpStatus.SC_CREATED
    ),

    CUSTOMER_ACCOUNTS(
            "/customer/accounts",
            BaseModel.class,
            AccountResponse.class,
            HttpStatus.SC_OK
    ),

    DEPOSIT(
            "/accounts/deposit",
            DepositeRequest.class,
            AccountResponse.class,
            HttpStatus.SC_OK
    ),

    TRANSFER(
            "/accounts/transfer",
            TransferRequest.class,
            TransferResponse.class,
            HttpStatus.SC_OK
    ),

    UPDATE_PROFILE(
            "/customer/profile",
            CustomerNameRequest.class,
            UpdateProfileResponse.class,
            HttpStatus.SC_OK
    ),

    GET_PROFILE(
            "/customer/profile",
            BaseModel.class,
            CustomerResponse.class,
            HttpStatus.SC_OK
    );

    private final String url;
    private final Class<? extends BaseModel> requestModel;
    private final Class<? extends BaseModel> responseModel;
    private final int successStatus;
}