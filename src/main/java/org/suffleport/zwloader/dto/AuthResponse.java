package org.suffleport.zwloader.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.ZonedDateTime;
import java.util.UUID;

@Data
@NoArgsConstructor
public class AuthResponse {
    private Integer userId;
    private String token;
    private String username;
    private String email;
    private Integer roleId;
    private String roleName;

    private UUID personId;
    private ZonedDateTime createdAt;
    
    @JsonProperty("isAdmin")
    private boolean admin;
    
    // Конструктор 
    public AuthResponse(String token, String username, String email, Integer roleId, String roleName, boolean isAdmin) {
        this.token = token;
        this.username = username;
        this.email = email;
        this.roleId = roleId;
        this.roleName = roleName;
        this.admin = isAdmin;
    }

    public AuthResponse(Integer userId,
                        String token,
                        String username,
                        String email,
                        Integer roleId,
                        String roleName,
                        boolean isAdmin,
                        UUID personId,
                        ZonedDateTime createdAt) {
        this.userId = userId;
        this.token = token;
        this.username = username;
        this.email = email;
        this.roleId = roleId;
        this.roleName = roleName;
        this.admin = isAdmin;
        this.personId = personId;
        this.createdAt = createdAt;
    }
}
