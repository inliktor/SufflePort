package org.suffleport.zwloader.service;

import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.suffleport.zwloader.domain.Notification;
import org.suffleport.zwloader.domain.User;
import org.suffleport.zwloader.repository.NotificationRepository;
import org.suffleport.zwloader.repository.UserRepository;
import org.suffleport.zwloader.security.Actor;

import java.util.List;

/**
 * Внутренние уведомления интерфейса.
 *
 * Никакой внешней доставки здесь нет: уведомление лежит в БД и появляется
 * у пользователя при следующем запросе списка. Для курсового этого достаточно,
 * а внешние каналы можно добавить отдельным слушателем.
 */
@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;

    @Transactional
    public void create(Integer recipientUserId, String type, String title, String body, String link) {
        if (recipientUserId == null) {
            return;
        }
        Notification notification = new Notification();
        notification.setRecipientUserId(recipientUserId);
        notification.setType(type);
        notification.setTitle(title);
        notification.setBody(body);
        notification.setLink(link);
        notification.setRead(false);
        notificationRepository.save(notification);
    }

    /**
     * Рассылка всем, кто вправе согласовывать заявки.
     * Список получателей вычисляется по роли, а не хранится в настройках:
     * так не нужно отдельное администрирование ответственных.
     */
    @Transactional
    public void notifyApprovers(String type, String title, String body, String link) {
        List<User> users = userRepository.findAll();
        for (User user : users) {
            Integer roleId = user.getRole() != null ? user.getRole().getRoleId() : null;
            if (roleId != null && (roleId == Actor.ROLE_ADMIN || roleId == Actor.ROLE_APPROVER)) {
                create(user.getUserId(), type, title, body, link);
            }
        }
    }

    @Transactional(readOnly = true)
    public List<Notification> listFor(Actor actor) {
        return notificationRepository.findTop100ByRecipientUserIdOrderByCreatedAtDesc(requireUserId(actor));
    }

    @Transactional(readOnly = true)
    public long unreadCount(Actor actor) {
        return notificationRepository.countUnread(requireUserId(actor));
    }

    @Transactional
    public void markRead(Long notificationId, Actor actor) {
        Notification notification = notificationRepository.findById(notificationId)
                .orElseThrow(() -> new java.util.NoSuchElementException("Уведомление не найдено"));
        if (!notification.getRecipientUserId().equals(requireUserId(actor))) {
            // Чужое уведомление читать нельзя, но и раскрывать его существование не нужно
            throw new AccessDeniedException("Уведомление принадлежит другому пользователю");
        }
        notification.setRead(true);
        notificationRepository.save(notification);
    }

    @Transactional
    public int markAllRead(Actor actor) {
        return notificationRepository.markAllRead(requireUserId(actor));
    }

    private Integer requireUserId(Actor actor) {
        if (actor == null || actor.userId() == null) {
            throw new AccessDeniedException("Требуется вход в систему");
        }
        return actor.userId();
    }
}
