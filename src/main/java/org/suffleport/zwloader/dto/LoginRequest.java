package org.suffleport.zwloader.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class LoginRequest {
    @NotBlank(message = "Email обязателен")
    @Size(max = 254, message = "Email не может быть длиннее 254 символов")
    private String username; // будем использовать email — без ограничения на домен для входа

    @NotBlank(message = "Пароль обязателен")
    @Size(max = 128, message = "Пароль не может быть длиннее 128 символов")
    private String password;
}
