package org.suffleport.zwloader.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .cors(cors -> {})
                .authorizeHttpRequests(auth -> auth
                    .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        // Public: static frontend assets, API docs and health check
                        .requestMatchers(
                                "/swagger-ui/**",
                                "/v3/api-docs/**",
                            "/actuator/health",
                                "/index.html",
                                "/login.html",
                                "/styles.css",
                                "/js/**"
                        ).permitAll()
                        // Public: auth endpoints and hardware-device integration
                    .requestMatchers("/api/auth/**", "/api/ha/**", "/api/events/log", "/api/security/**", "/rpc/**", "/api/public/**").permitAll()
                        // Everything else requires an authenticated session
                        .anyRequest().authenticated()
                )
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED));
        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        // Allow LAN/local origins on any port and embedded WebView null origin.
        config.setAllowedOriginPatterns(List.of(
                "https://192.168.*:*",
                "http://192.168.*:*",
                "https://10.*:*",
                "http://10.*:*",
                "https://172.*:*",
                "http://172.*:*",
                "https://localhost:*",
                "http://localhost:*",
                "https://127.0.0.1:*",
                "http://127.0.0.1:*",
                "null"
        ));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS", "HEAD"));
        config.setAllowedHeaders(List.of("*"));
        config.setExposedHeaders(List.of("Set-Cookie", "Authorization", "Content-Disposition"));
        config.setAllowCredentials(true);
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}
