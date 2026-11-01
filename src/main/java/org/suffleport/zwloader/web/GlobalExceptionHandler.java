package org.suffleport.zwloader.web;

import jakarta.validation.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.stream.Collectors;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Map<String, Object> handleValidation(MethodArgumentNotValidException ex) {
        Map<String, String> errors = ex.getBindingResult().getFieldErrors().stream()
                .collect(Collectors.toMap(
                        FieldError::getField,
                        FieldError::getDefaultMessage,
                        (first, second) -> first,
                        LinkedHashMap::new
                ));
        return Map.of(
                "message", "Ошибка валидации данных",
                "errors", errors
        );
    }

    @ExceptionHandler(ConstraintViolationException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Map<String, Object> handleConstraintViolation(ConstraintViolationException ex) {
        Map<String, String> errors = ex.getConstraintViolations().stream()
                .collect(Collectors.toMap(
                        violation -> violation.getPropertyPath().toString(),
                        violation -> violation.getMessage(),
                        (first, second) -> first,
                        LinkedHashMap::new
                ));
        return Map.of(
                "message", "Ошибка валидации данных",
                "errors", errors
        );
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Map<String, String> handleUnreadableBody(HttpMessageNotReadableException ex) {
        return Map.of("message", "Некорректный формат JSON, даты или enum-значений");
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Map<String, String> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        return Map.of("message", "Некорректное значение параметра: " + ex.getName());
    }

    @ExceptionHandler(IllegalStateException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public Map<String, String> handleConflict(IllegalStateException ex) {
        return Map.of("message", ex.getMessage() != null ? ex.getMessage() : "Conflict");
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public Map<String, String> handleDataIntegrity(DataIntegrityViolationException ex) {
        return Map.of("message", "Операция нарушает связи данных. Проверьте привязанные записи");
    }

    /**
     * Двое согласующих нажали «Одобрить» одновременно. Второй получает 409,
     * а не дубликат визита.
     */
    @ExceptionHandler(OptimisticLockingFailureException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public Map<String, String> handleOptimisticLock(OptimisticLockingFailureException ex) {
        return Map.of("message", "Запись уже изменена другим пользователем. Обновите данные и повторите действие");
    }

    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Map<String, String> handleBadRequest(IllegalArgumentException ex) {
        return Map.of("message", ex.getMessage() != null ? ex.getMessage() : "Bad request");
    }

    @ExceptionHandler(NoSuchElementException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public Map<String, String> handleNotFound(NoSuchElementException ex) {
        return Map.of("message", ex.getMessage() != null ? ex.getMessage() : "Not found");
    }

    /**
     * Отказ в правах. Возникает из @PreAuthorize и из явных проверок в сервисах.
     * Отдаём именно 403, чтобы на фронтенде было видно отличие
     * «не авторизован» (401) от «вошёл, но нельзя» (403).
     */
    @ExceptionHandler(AccessDeniedException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public Map<String, String> handleAccessDenied(AccessDeniedException ex) {
        return Map.of("message", ex.getMessage() != null ? ex.getMessage() : "Недостаточно прав");
    }

    @ExceptionHandler(AuthenticationException.class)
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    public Map<String, String> handleAuthentication(AuthenticationException ex) {
        return Map.of("message", "Требуется вход в систему");
    }
}
