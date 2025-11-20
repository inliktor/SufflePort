package org.suffleport.zwloader.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class AuthResponse {
    private String token;
    private String username;
    private String email;
    private Integer roleId;
    private String roleName;
    
    @JsonProperty("isAdmin")
    private boolean admin;
    
    // Конструктор с параметрами
    public AuthResponse(String token, String username, String email, Integer roleId, String roleName, boolean isAdmin) {
        this.token = token;
        this.username = username;
        this.email = email;
        this.roleId = roleId;
        this.roleName = roleName;
        this.admin = isAdmin;
    }
}
