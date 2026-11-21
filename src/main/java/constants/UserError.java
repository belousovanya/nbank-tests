package constants;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum UserError {
    USERNAME_BLANK("username", "Username cannot be blank"),
    USERNAME_LENGTH("username", "Username must be between 3 and 15 characters"),
    USERNAME_INVALID_CHARS("username", "Username must contain only letters, digits, dashes, underscores, and dots"),
    PASSWORD_BLANK("password", "Password cannot be blank"),
    PASSWORD_INVALID("password", "Password must contain at least one digit, one lower case, one upper case, one special character, no spaces, and be at least 8 characters long"),
    ROLE_INVALID("role", "Role must be either 'ADMIN' or 'USER'");

    private final String field;
    private final String message;
}
