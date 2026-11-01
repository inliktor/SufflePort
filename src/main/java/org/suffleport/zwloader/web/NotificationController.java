package org.suffleport.zwloader.web;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.suffleport.zwloader.domain.Notification;
import org.suffleport.zwloader.security.CurrentUserService;
import org.suffleport.zwloader.service.NotificationService;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;
    private final CurrentUserService currentUserService;

    @GetMapping
    public List<Notification> list() {
        return notificationService.listFor(currentUserService.current());
    }

    @GetMapping("/unread-count")
    public Map<String, Long> unreadCount() {
        return Map.of("unread", notificationService.unreadCount(currentUserService.current()));
    }

    @PostMapping("/{id}/read")
    public Map<String, String> markRead(@PathVariable Long id) {
        notificationService.markRead(id, currentUserService.current());
        return Map.of("status", "ok");
    }

    @PostMapping("/read-all")
    public Map<String, Integer> markAllRead() {
        return Map.of("updated", notificationService.markAllRead(currentUserService.current()));
    }
}
