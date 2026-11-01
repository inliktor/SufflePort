package org.suffleport.zwloader.security;

import java.util.Optional;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.suffleport.zwloader.domain.User;
import org.suffleport.zwloader.repository.UserRepository;

import lombok.RequiredArgsConstructor;

/**
 * Достаёт текущего пользователя из контекста безопасности.
 *
 * В проекте аутентификация хранится в HTTP-сессии (см. AuthController),
 * поэтому здесь достаточно прочитать Authentication и найти пользователя по email.
 */
@Component
@RequiredArgsConstructor
public class CurrentUserService {

    private final UserRepository userRepository;

    public Actor current() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null
                || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken) {
            throw new AccessDeniedException("Требуется вход в систему");
        }
        User user = userRepository.findByEmail(authentication.getName())
                .orElseThrow(() -> new AccessDeniedException("Пользователь текущей сессии не найден"));
        return toActor(user);
    }

    /**
     * Мягкий вариант: нужен в местах, где действие может выполнять и система
     * (например, аудит при фоновой операции).
     */
    public Optional<Actor> currentOrEmpty() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null
                || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken) {
            return Optional.empty();
        }
        return userRepository.findByEmail(authentication.getName()).map(CurrentUserService::toActor);
    }

    public static Actor toActor(User user) {
        Integer roleId = user.getRole() != null ? user.getRole().getRoleId() : null;
        return new Actor(user.getUserId(), user.getEmail(), roleId, user.getPersonId());
    }
}
