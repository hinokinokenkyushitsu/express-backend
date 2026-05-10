package com.fiveok.express.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class LoginRequest {

    @NotBlank(message = "{validation.phone.notBlank}")
    @Pattern(regexp = "^1[3-9]\\d{9}$", message = "{validation.phone.format}")
    private String phone;

    @NotBlank(message = "{validation.password.notBlank}")
    @Size(min = 6, max = 64, message = "{validation.password.size}")
    private String password;
}
