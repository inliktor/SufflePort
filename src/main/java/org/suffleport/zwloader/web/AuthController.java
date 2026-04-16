package org.suffleport.zwloader.web;

import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.suffleport.zwloader.domain.Role;
import org.suffleport.zwloader.domain.User;
import org.suffleport.zwloader.dto.AuthResponse;
import org.suffleport.zwloader.dto.LoginRequest;
import org.suffleport.zwloader.dto.RegisterRequest;
import org.suffleport.zwloader.repository.RoleRepository;
import org.suffleport.zwloader.repository.UserRepository;
import org.suffleport.zwloader.validation.ValidationRules;

import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@CrossOrigin(origins = "http://192.168.88.247:7080", allowCredentials = "true")
public class AuthController {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;

    @PostMapping("/register")
    public ResponseEntity<Map<String, Object>> register(@Valid @RequestBody RegisterRequest request) {
        String rawEmail = request.getEmail() != null ? request.getEmail() : request.getUsername();
        String email = ValidationRules.normalizeEmail(rawEmail);
        ValidationRules.validatePassword(request.getPassword());

        if (userRepository.existsByEmail(email)) {
            throw new IllegalStateException("Пользователь с таким email уже существует");
        }

        Role userRole = roleRepository.findByRoleId(0)
                .orElseThrow(() -> new IllegalStateException("Роль USER не найдена"));

        User user = new User();
        user.setEmail(email);
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setRole(userRole);
        userRepository.save(user);

        return ResponseEntity.ok(Map.of(
                "message", "Регистрация успешна",
                "email", user.getEmail()
        ));
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@Valid @RequestBody LoginRequest request, HttpSession session) {
        try {
            String email = ValidationRules.normalizeEmail(request.getUsername());
           
            User user = userRepository.findByEmail(email)
                    .orElseThrow(() -> new RuntimeException("Неверные учетные данные"));

            
            if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body("Неверные учетные данные");
            }

            
            Integer userRoleId = user.getRole() != null ? user.getRole().getRoleId() : 0;
            boolean isAdminUser = userRoleId == 1;

            
            session.setAttribute("userId", user.getUserId());
            session.setAttribute("email", user.getEmail());
            session.setAttribute("roleId", userRoleId);
            session.setAttribute("isAdmin", isAdminUser);


            var authorities = List.of(new SimpleGrantedAuthority(
                    isAdminUser ? "ROLE_ADMIN" : "ROLE_USER"));
            var springAuth = new UsernamePasswordAuthenticationToken(
                    user.getEmail(), null, authorities);
            SecurityContext springContext = SecurityContextHolder.createEmptyContext();
            springContext.setAuthentication(springAuth);
            SecurityContextHolder.setContext(springContext);
            
            session.setAttribute(
                    HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY,
                    springContext);

            
                AuthResponse response = new AuthResponse(
                    user.getUserId(),
                    session.getId(), 
                    user.getEmail().split("@")[0], 
                    user.getEmail(),
                    userRoleId,
                    user.getRole().getName(),
                    isAdminUser,
                    user.getPersonId(),
                    user.getCreatedAt()
                );

            return ResponseEntity.ok(response);

        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Ошибка входа: " + e.getMessage());
        }
    }

    @GetMapping("/me")
    public ResponseEntity<?> getCurrentUser(HttpSession session) {
        try {
            Integer userId = (Integer) session.getAttribute("userId");
            String email = (String) session.getAttribute("email");
            Integer roleId = (Integer) session.getAttribute("roleId");
            Boolean isAdmin = (Boolean) session.getAttribute("isAdmin");

            if (userId == null || email == null) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body("Не авторизован");
            }

            User user = userRepository.findByEmail(email)
                    .orElseThrow(() -> new RuntimeException("Пользователь не найден"));

            AuthResponse response = new AuthResponse(
                    user.getUserId(),
                    session.getId(),
                    user.getEmail().split("@")[0],
                    user.getEmail(),
                    roleId,
                    user.getRole().getName(),
                    isAdmin != null && isAdmin,
                    user.getPersonId(),
                    user.getCreatedAt()
            );

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body("Ошибка авторизации: " + e.getMessage());
        }
    }

    @PostMapping("/logout")
    public ResponseEntity<?> logout(HttpSession session) {
        SecurityContextHolder.clearContext();
        session.invalidate();
        return ResponseEntity.ok(Map.of("message", "Выход выполнен"));
    }
}
