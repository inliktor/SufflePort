package org.suffleport.zwloader.security;

import java.util.UUID;

/**
 * Пользователь, от имени которого выполняется действие.
 *
 * Передаётся в сервисы явным аргументом. Так бизнес-логику можно проверить
 * в обычном unit-тесте, не поднимая контекст безопасности Spring.
 */
public record Actor(Integer userId, String email, Integer roleId, UUID personId) {

    public static final int ROLE_EMPLOYEE = 0;
    public static final int ROLE_ADMIN = 1;
    public static final int ROLE_APPROVER = 2;
    public static final int ROLE_SECURITY = 3;

    public boolean isAdmin() {
        return roleId != null && roleId == ROLE_ADMIN;
    }

    public boolean isApprover() {
        return isAdmin() || (roleId != null && roleId == ROLE_APPROVER);
    }

    public boolean isSecurity() {
        return isAdmin() || (roleId != null && roleId == ROLE_SECURITY);
    }

    public String displayName() {
        return email != null ? email : "неизвестный пользователь";
    }
}
