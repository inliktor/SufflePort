package org.suffleport.zwloader.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.suffleport.zwloader.validation.RussianEmail;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class RegisterRequest {
    @Size(max = 254, message = "Логин не может быть длиннее 254 символов")
    @RussianEmail
    private String username; // будем использовать как email

    @Size(max = 254, message = "Email не может быть длиннее 254 символов")
    @RussianEmail
    private String email;

    @Size(min = 8, max = 128, message = "Пароль должен содержать от 8 до 128 символов")
    @Pattern(regexp = "^(?=.*\\d)(?=.*[\\p{L}A-Za-z]).{8,128}$", message = "Пароль должен содержать хотя бы одну букву и одну цифру")
    private String password;
}
