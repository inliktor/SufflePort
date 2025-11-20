package org.suffleport.zwloader.web;

import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;
import org.suffleport.zwloader.domain.Role;
import org.suffleport.zwloader.domain.User;
import org.suffleport.zwloader.dto.AuthResponse;
import org.suffleport.zwloader.dto.LoginRequest;
import org.suffleport.zwloader.dto.RegisterRequest;
import org.suffleport.zwloader.repository.RoleRepository;
import org.suffleport.zwloader.repository.UserRepository;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@CrossOrigin(origins = "http://192.168.88.247:7080", allowCredentials = "true")
public class AuthController {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;

    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody RegisterRequest request) {
        try {
            // Проверяем существование пользователя
            if (userRepository.existsByEmail(request.getEmail())) {
                return ResponseEntity.status(HttpStatus.CONFLICT)
                        .body("Пользователь с таким email уже существует");
            }

            // По умолчанию новые пользователи получают роль USER (role_id = 0)
            Role userRole = roleRepository.findByRoleId(0)
                    .orElseThrow(() -> new RuntimeException("Роль USER не найдена"));

            // Создаём пользователя
            User user = new User();
            user.setEmail(request.getEmail());
            user.setPassword(passwordEncoder.encode(request.getPassword()));
            user.setRole(userRole);

            userRepository.save(user);

            // Возвращаем успех
            return ResponseEntity.ok()
                    .body(Map.of(
                            "message", "Регистрация успешна",
                            "email", user.getEmail()
                    ));

        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Ошибка регистрации: " + e.getMessage());
        }
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody LoginRequest request, HttpSession session) {
        try {
            // Ищем пользователя по email
            User user = userRepository.findByEmail(request.getUsername())
                    .orElseThrow(() -> new RuntimeException("Неверные учетные данные"));

            // Проверяем пароль
            if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body("Неверные учетные данные");
            }

            // Проверяем является ли пользователь администратором
            Integer userRoleId = user.getRole() != null ? user.getRole().getRoleId() : 0;
            boolean isAdminUser = userRoleId == 1;
            
            System.out.println("=== LOGIN DEBUG ===");
            System.out.println("User email: " + user.getEmail());
            System.out.println("User roleId: " + userRoleId);
            System.out.println("isAdminUser: " + isAdminUser);
            
            // Сохраняем пользователя в сессию
            session.setAttribute("userId", user.getUserId());
            session.setAttribute("email", user.getEmail());
            session.setAttribute("roleId", userRoleId);
            session.setAttribute("isAdmin", isAdminUser);

            // Формируем ответ
            AuthResponse response = new AuthResponse(
                    session.getId(), // используем ID сессии как токен
                    user.getEmail().split("@")[0], // username из email
                    user.getEmail(),
                    userRoleId,
                    user.getRole().getName(),
                    isAdminUser
            );
            
            System.out.println("Response isAdmin: " + response.isAdmin());
            System.out.println("Response roleId: " + response.getRoleId());

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
                    session.getId(),
                    user.getEmail().split("@")[0],
                    user.getEmail(),
                    roleId,
                    user.getRole().getName(),
                    isAdmin
            );

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body("Ошибка авторизации: " + e.getMessage());
        }
    }

    @PostMapping("/logout")
    public ResponseEntity<?> logout(HttpSession session) {
        session.invalidate();
        return ResponseEntity.ok(Map.of("message", "Выход выполнен"));
    }
}
