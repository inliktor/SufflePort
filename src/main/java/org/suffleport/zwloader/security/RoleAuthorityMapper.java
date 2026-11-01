package org.suffleport.zwloader.security;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.suffleport.zwloader.domain.Role;

/**
 * Преобразование роли из БД в набор прав Spring Security.
 *
 * До этого в проекте роль влияла только на флаг isAdmin в ответе логина.
 * Теперь роль превращается в authority, и методы контроллеров можно закрыть
 * аннотацией @PreAuthorize.
 */
public final class RoleAuthorityMapper {

    private RoleAuthorityMapper() {
    }

    public static List<GrantedAuthority> authoritiesOf(Role role) {
        int roleId = role != null && role.getRoleId() != null
                ? role.getRoleId()
                : Actor.ROLE_EMPLOYEE;

        Set<String> names = new LinkedHashSet<>();
        // Базовая роль остаётся у всех: на ней держатся старые проверки
        names.add("ROLE_USER");

        switch (roleId) {
            case Actor.ROLE_ADMIN -> {
                names.add("ROLE_EMPLOYEE");
                names.add("ROLE_APPROVER");
                names.add("ROLE_SECURITY");
                names.add("ROLE_ADMIN");
            }
            case Actor.ROLE_APPROVER -> {
                names.add("ROLE_EMPLOYEE");
                names.add("ROLE_APPROVER");
            }
            case Actor.ROLE_SECURITY -> {
                names.add("ROLE_EMPLOYEE");
                names.add("ROLE_SECURITY");
            }
            default -> names.add("ROLE_EMPLOYEE");
        }

        List<GrantedAuthority> result = new ArrayList<>(names.size());
        for (String name : names) {
            result.add(new SimpleGrantedAuthority(name));
        }
        return result;
    }
}
