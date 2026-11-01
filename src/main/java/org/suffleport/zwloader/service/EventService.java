package org.suffleport.zwloader.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.suffleport.zwloader.domain.*;
import org.suffleport.zwloader.repository.*;
import org.suffleport.zwloader.validation.ValidationRules;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.concurrent.ConcurrentHashMap;

@Service
@RequiredArgsConstructor
public class EventService {

    private static final ZoneId APP_ZONE = ZoneId.of("Europe/Saratov");
    private static final Duration CARD_BOUNCE_WINDOW = Duration.ofSeconds(5);

    private final EventRepository eventRepository;
    private final CardRepository cardRepository;
    private final PersonnelRepository personnelRepository;
    private final DeviceRepository deviceRepository;
    private final ConcurrentHashMap<String, OffsetDateTime> nfcLastSignalByCard = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Event> nfcLastEventByCard = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Object> nfcLocksByCard = new ConcurrentHashMap<>();

    @Transactional
    public Event create(String cardUid,
                        java.util.UUID personId,
                        String deviceId,
                        String faceName,
                        Direction direction,
                        Source source,
                        EventMeta meta) {
        if (direction == null) throw new IllegalArgumentException("direction is required");
        if (source == null) throw new IllegalArgumentException("source is required");

        String safeCardUid = ValidationRules.normalizeOptionalCardUid(cardUid);
        String safeDeviceId = ValidationRules.normalizeOptionalSystemId(deviceId, "ID устройства");
        String safeFaceName = ValidationRules.normalizeOptionalText(faceName, "Имя лица", 200);

        if (source == Source.NFC && safeCardUid == null && personId == null) {
            throw new IllegalArgumentException("Для NFC-события требуется cardUid или personId");
        }
        if (source == Source.FACE && safeFaceName == null && personId == null) {
            throw new IllegalArgumentException("Для FACE-события требуется faceName или personId");
        }
        if (source == Source.MANUAL && safeCardUid == null && personId == null) {
            throw new IllegalArgumentException("Для MANUAL-события требуется cardUid или personId");
        }

        Card card = null;
        if (safeCardUid != null) {
            card = cardRepository.findById(safeCardUid)
                    .orElseThrow(() -> new NoSuchElementException("Карта не найдена: " + safeCardUid));
        }
        Personnel person = null;
        if (personId != null) {
            person = personnelRepository.findById(personId)
                    .orElseThrow(() -> new NoSuchElementException("Сотрудник не найден: " + personId));
        }
        Device device = resolveDeviceForEvent(safeDeviceId, defaultDeviceKind(source));

        Event e = new Event(card, person, device, safeFaceName, direction, source, meta);
        return eventRepository.save(e);
    }

    @Transactional(readOnly = true)
    public Event getOrThrow(Long id) {
        return eventRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Событие не найдено: " + id));
    }

    @Transactional(readOnly = true)
    public Page<Event> listPage(Pageable pageable) { return eventRepository.findAll(pageable); }

    @Transactional(readOnly = true)
    public Page<Event> listPage(Source source, Direction direction, LocalDate date, Pageable pageable) {
        FilterRange range = buildFilterRange(date);
        if (range.start() == null || range.end() == null) {
            return eventRepository.findBySourceAndDirection(source, direction, pageable);
        }
        return eventRepository.findBySourceAndDirectionAndCreatedAtBetween(source, direction, range.start(), range.end(), pageable);
    }

    @Transactional(readOnly = true)
    public List<Event> listAll(Source source, Direction direction, LocalDate date) {
        FilterRange range = buildFilterRange(date);
        if (range.start() == null || range.end() == null) {
            return eventRepository.findBySourceAndDirection(source, direction, Pageable.unpaged()).getContent();
        }
        return eventRepository.findBySourceAndDirectionAndCreatedAtBetween(source, direction, range.start(), range.end(), Pageable.unpaged()).getContent();
    }

    private FilterRange buildFilterRange(LocalDate date) {
        OffsetDateTime start = null;
        OffsetDateTime end = null;
        if (date != null) {
            start = date.atStartOfDay(APP_ZONE).toOffsetDateTime();
            end = date.plusDays(1).atStartOfDay(APP_ZONE).toOffsetDateTime();
        }
        return new FilterRange(start, end);
    }

    @Transactional(readOnly = true)
    public List<Event> listAll() { return eventRepository.findAll(); }

    @Transactional(readOnly = true)
    public List<Event> listByPerson(java.util.UUID personId) { return eventRepository.findByPerson_Id(personId); }

    @Transactional(readOnly = true)
    public List<Event> listByCard(String cardUid) { return eventRepository.findByCard_Uid(cardUid); }

    @Transactional(readOnly = true)
    public List<Event> listByDeviceAndPeriod(String deviceId, OffsetDateTime start, OffsetDateTime end) {
        String safeDeviceId = ValidationRules.normalizeSystemId(deviceId, "ID устройства");
        ValidationRules.validateDateRange(start, end, "Начало периода", "Конец периода", true);
        return eventRepository.findByDevice_IdAndCreatedAtBetween(safeDeviceId, start, end);
    }

    @Transactional(readOnly = true)
    public List<Event> findBySource(Source source) { return eventRepository.findBySource(source); }

    @Transactional(readOnly = true)
    public List<Event> listCreatedAfter(OffsetDateTime date) { return eventRepository.findByCreatedAtAfter(date); }

    @Transactional(readOnly = true)
    public Page<Event> listCreatedAfter(OffsetDateTime date, Pageable pageable) {
        return eventRepository.findByCreatedAtAfter(date, pageable);
    }

    @Transactional(readOnly = true)
    public List<Event> listBetween(OffsetDateTime start, OffsetDateTime end) {
        ValidationRules.validateDateRange(start, end, "Начало периода", "Конец периода", true);
        return eventRepository.findByCreatedAtBetween(start, end);
    }

    @Transactional
    public void delete(Long id) { eventRepository.delete(getOrThrow(id)); }

    @Transactional(readOnly = true)
    public Event lastByCard(String cardUid) { return eventRepository.findTop1ByCard_UidOrderByCreatedAtDesc(cardUid); }

    @Transactional(readOnly = true)
    public Event lastByFace(String faceName) { return eventRepository.findTop1ByFaceNameOrderByCreatedAtDesc(faceName); }

    @Transactional(readOnly = true)
    public Event lastByDevice(String deviceId) { return eventRepository.findTop1ByDevice_IdOrderByCreatedAtDesc(deviceId); }

    private record FilterRange(OffsetDateTime start, OffsetDateTime end) {}

    private Direction next(Direction prev) { return prev == Direction.IN ? Direction.OUT : Direction.IN; }

    @Transactional
    public Event createNfcToggleEvent(String cardUid, String deviceId) {
        String safeCardUid = ValidationRules.normalizeCardUid(cardUid);
        String safeDeviceId = ValidationRules.normalizeOptionalSystemId(deviceId, "ID устройства");
        Card card = cardRepository.findById(safeCardUid)
                .orElseThrow(() -> new IllegalArgumentException("Карта не зарегистрирована: " + safeCardUid));
        if (!card.isActive()) {
            throw new IllegalArgumentException("Карта не активна: " + safeCardUid);
        }
        if (card.getPerson() == null) {
            throw new IllegalArgumentException("Карта не привязана к сотруднику: " + safeCardUid);
        }

        Object lock = nfcLocksByCard.computeIfAbsent(safeCardUid, key -> new Object());
        synchronized (lock) {
            OffsetDateTime now = OffsetDateTime.now();
            OffsetDateTime lastSignal = nfcLastSignalByCard.get(safeCardUid);
            if (lastSignal != null && !lastSignal.isBefore(now.minus(CARD_BOUNCE_WINDOW))) {
                Event lastEvent = nfcLastEventByCard.get(safeCardUid);
                if (lastEvent != null) {
                    return lastEvent;
                }
                lastEvent = lastByCard(safeCardUid);
                if (lastEvent != null) {
                    return lastEvent;
                }
            }

            Device device = resolveDeviceForEvent(safeDeviceId, "NFC_READER");
            Direction dir = Direction.IN;
            Event last = lastByCard(safeCardUid);
            if (last != null) {
                dir = next(last.getDirection());
            }
            Personnel person = card.getPerson();
            Event e = new Event(card, person, device, null, dir, Source.NFC, null);
            Event saved = eventRepository.saveAndFlush(e);
            nfcLastSignalByCard.put(safeCardUid, now);
            nfcLastEventByCard.put(safeCardUid, saved);
            return saved;
        }
    }

    @Transactional
    public Event createFaceToggleEvent(String faceName, String deviceId) {
        String safeFaceName = ValidationRules.normalizeRequiredText(faceName, "Имя лица", 200);
        String safeDeviceId = ValidationRules.normalizeOptionalSystemId(deviceId, "ID устройства");
        Device device = resolveDeviceForEvent(safeDeviceId, "CAMERA");
        Direction dir = Direction.IN;
        Event last = lastByFace(safeFaceName);
        if (last != null) dir = next(last.getDirection());
        // попытка найти сотрудника по полному имени
        Personnel person = null;
        List<Personnel> matches = personnelRepository.findByFullNameAndArchivedAtIsNull(safeFaceName);
        if (matches.size() == 1) person = matches.get(0);
        Event e = new Event(null, person, device, safeFaceName, dir, Source.FACE, null);
        return eventRepository.save(e);
    }

    @Transactional
    public Event createSecurityAlert(String uidOrFace, String deviceId, Source source, String reason) {
        String safeUidOrFace = ValidationRules.normalizeRequiredText(uidOrFace, "Идентификатор карты или лица", 200);
        String safeDeviceId = ValidationRules.normalizeOptionalSystemId(deviceId, "ID устройства");
        String safeReason = ValidationRules.normalizeOptionalText(reason, "Причина блокировки", 200);
        if (source == null) {
            throw new IllegalArgumentException("source is required");
        }
        Device device = resolveDeviceForEvent(safeDeviceId, defaultDeviceKind(source));
        EventMeta meta = new EventMeta();
        meta.setDecision(safeReason != null ? safeReason : "DENY");
        String unresolvedIdentifier = safeUidOrFace;
        Event e = new Event(null, null, device, unresolvedIdentifier, Direction.OUT, source, meta);
        return eventRepository.save(e);
    }

    private Device resolveDeviceForEvent(String safeDeviceId, String defaultKind) {
        if (safeDeviceId == null) {
            return null;
        }
        return deviceRepository.findById(safeDeviceId).orElseGet(() -> {
            Device device = new Device();
            device.setId(safeDeviceId);
            device.setKind(defaultKind);
            device.setLocation("Автоматически добавлено из события");
            return deviceRepository.save(device);
        });
    }

    private String defaultDeviceKind(Source source) {
        if (source == Source.FACE) {
            return "CAMERA";
        }
        if (source == Source.NFC) {
            return "NFC_READER";
        }
        return "EVENT_SOURCE";
    }
}
