package org.suffleport.zwloader.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.suffleport.zwloader.domain.Notification;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    List<Notification> findTop100ByRecipientUserIdOrderByCreatedAtDesc(Integer recipientUserId);

    // Явный JPQL вместо вывода имени из названия метода: меньше шансов ошибиться в разборе
    @Query("select count(n) from Notification n where n.recipientUserId = :userId and n.read = false")
    long countUnread(@Param("userId") Integer userId);

    @Modifying(clearAutomatically = true)
    @Query("update Notification n set n.read = true where n.recipientUserId = :userId and n.read = false")
    int markAllRead(@Param("userId") Integer userId);
}
