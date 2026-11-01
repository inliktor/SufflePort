package org.suffleport.zwloader.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.suffleport.zwloader.domain.AuditLog;
import org.suffleport.zwloader.repository.AuditLogRepository;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Журнал аудита действий пользователей.
 *
 * Пишем «до» и «после» в виде JSON. Сознательно не делаем восстановление
 * старых значений: это отдельная задача с массой крайних случаев, а для
 * курсовой важнее достоверная история.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AuditService {

    private final AuditLogRepository auditLogRepository;
    private final ObjectMapper objectMapper;

    /**
     * Запись аудита в отдельной транзакции.
     *
     * Если писать в общей транзакции, то любая ошибка вставки помечает её
     * rollback-only, и внешний вызов падает на коммите — то есть аудит всё-таки
     * ломает основную операцию. REQUIRES_NEW отвязывает запись от бизнес-транзакции.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(String entityType,
                       String entityId,
                       String action,
                       Object before,
                       Object after,
                       String actorEmail,
                       String reason) {
        try {
            AuditLog entry = new AuditLog();
            entry.setEntityType(entityType);
            entry.setEntityId(entityId);
            entry.setAction(action);
            entry.setActorEmail(actorEmail);
            entry.setBeforeJson(toJson(before));
            entry.setAfterJson(toJson(after));
            entry.setReason(reason);
            auditLogRepository.save(entry);
        } catch (RuntimeException ex) {
            // Аудит не должен ломать основную операцию: пишем в лог и идём дальше
            log.warn("Не удалось записать аудит: {} {} {}", entityType, entityId, action, ex);
        }
    }

    @Transactional(readOnly = true)
    public Page<AuditLog> search(String entityType,
                                 String entityId,
                                 String actor,
                                 OffsetDateTime from,
                                 OffsetDateTime to,
                                 Pageable pageable) {
        // Фильтры собираются только для заполненных полей. Раньше здесь был JPQL
        // с (:param is null or ...) — PostgreSQL получал null без типа и падал
        // на lower(bytea).
        Specification<AuditLog> specification = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (hasText(entityType)) {
                predicates.add(cb.equal(root.get("entityType"), entityType.trim()));
            }
            if (hasText(entityId)) {
                predicates.add(cb.equal(root.get("entityId"), entityId.trim()));
            }
            if (hasText(actor)) {
                String pattern = "%" + actor.trim().toLowerCase(Locale.ROOT) + "%";
                predicates.add(cb.like(cb.lower(root.get("actorEmail")), pattern));
            }
            if (from != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("createdAt"), from));
            }
            if (to != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("createdAt"), to));
            }

            return cb.and(predicates.toArray(Predicate[]::new));
        };

        Pageable sorted = pageable.getSort().isSorted()
                ? pageable
                : org.springframework.data.domain.PageRequest.of(
                        pageable.getPageNumber(),
                        pageable.getPageSize(),
                        Sort.by(Sort.Direction.DESC, "createdAt"));

        return auditLogRepository.findAll(specification, sorted);
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private String toJson(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception ex) {
            return String.valueOf(value);
        }
    }
}
