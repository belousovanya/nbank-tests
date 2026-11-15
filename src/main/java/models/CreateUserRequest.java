package models;

import generators.GeneratingRule;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class CreateUserRequest extends BaseModel {
    // API разрешает 3-15 символов; генерируем 10-15, чтобы случайные юзернеймы почти не совпадали
    @GeneratingRule(regex = "[a-zA-Z0-9]{10,15}")
    private String username;

    // большие + маленькие + цифры + спецсимволы, всего 12 символов
    @GeneratingRule(regex = "[A-Z]{3}[a-z]{4}[0-9]{3}[!@#$%]{2}")
    private String password;

    @GeneratingRule(regex = "USER")
    private String role;
}