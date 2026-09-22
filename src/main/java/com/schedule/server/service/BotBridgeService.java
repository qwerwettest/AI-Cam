package com.schedule.server.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.schedule.server.dto.FindRoomRequest;
import com.schedule.server.dto.FindRoomResponse;
import com.schedule.server.dto.RoomInfo;
import com.schedule.server.tcp.CppTcpClient;
import com.schedule.server.util.TimeUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;

/**
 * Сервис-мост: принимает данные от Telegram-бота,
 * формирует и пересылает запрос на поиск к C++ серверу,
 * получает готовый результат и возвращает его боту.
 *
 * <p>Java НЕ занимается поиском кабинетов самостоятельно.
 * Вся логика поиска (расписание, камеры, фильтрация) — на стороне C++ сервера.
 *
 * <p>Алгоритм:
 * <ol>
 *   <li>Парсим запрос от бота → формируем JSON для C++</li>
 *   <li>Отправляем запрос на C++ сервер по TCP</li>
 *   <li>Парсим ответ C++ → формируем FindRoomResponse для бота</li>
 * </ol>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class BotBridgeService {

    private final CppTcpClient cppTcpClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private final BookingService bookingService;

    /**
     * Маппинг location_id (из конфига бота) → corpus (строка для C++ сервера).
     */
    private static final Map<String, String> LOCATION_TO_CORPUS = Map.of(
            "corp_a", "Корпус А",
            "corp_b", "Корпус Б",
            "corp_c", "Корпус С"
    );

    /**
     * Основной метод: пересылает запрос от бота на C++ и возвращает результат.
     */
    public FindRoomResponse findRooms(FindRoomRequest request) {
        log.info("findRooms: location_id={}, start_at={}, duration={}, floor={}, user={}",
                request.getLocationId(), request.getStartAt(), request.getDurationMinutes(),
                request.getFloor(), request.getTelegramUserId());

        // --- 1. Формируем payload для C++ ---
        Map<String, Object> cppPayload = buildCppPayload(request);
        log.info("Sending search request to C++: {}", cppPayload);

        // --- 2. Отправляем на C++ и получаем ответ ---
        JsonNode cppResponse = cppTcpClient.send(cppPayload);
        log.info("Received response from C++: {}", cppResponse);

        // --- 3. Парсим ответ C++ → FindRoomResponse ---
        return parseCppResponse(cppResponse, request);
    }

    // ========================= Формирование запроса =========================

    /** Счётчик запросов для генерации id. */
    private final java.util.concurrent.atomic.AtomicInteger requestIdCounter =
            new java.util.concurrent.atomic.AtomicInteger(0);

    /**
     * Собирает JSON-payload для C++ сервера из данных бота.
     * <pre>
     * {
     *   "id": 1,
     *   "start_time": "10:30",
     *   "duration": 90,
     *   "corpus": "Главный"
     * }
     * </pre>
     */
    private Map<String, Object> buildCppPayload(FindRoomRequest request) {
        LocalDateTime startDateTime = parseStartAt(request.getStartAt());
        LocalTime startTime = startDateTime.toLocalTime();
        String corpus = resolveCorpus(request.getLocationId());

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("id", requestIdCounter.incrementAndGet());
        payload.put("start_time", startTime.format(DateTimeFormatter.ofPattern("HH:mm")));
        payload.put("duration", request.getDurationMinutes());
        payload.put("corpus", corpus);

        // Этаж: C++ использует его как предпочтение при сортировке.
        // 0 означает "этаж не важен".
        payload.put("floor", request.getFloor() != null ? request.getFloor() : 0);

        return payload;
    }

    // ========================= Парсинг ответа =========================

    /**
     * Парсит JSON-ответ от C++ сервера в FindRoomResponse.
     *
     * <p>Реальный формат ответа C++:
     * <pre>
     * {"cabinet": 103, "id": 3, "status": "answer"}
     * </pre>
     *
     * <p>cabinet — номер найденного свободного кабинета (или 0 / отсутствует, если не найден).
     * status — "answer" при успешном ответе.
     */
    private FindRoomResponse parseCppResponse(JsonNode root, FindRoomRequest request) {
        log.debug("Parsing C++ response: {}", root);

        String corpus = resolveCorpus(request.getLocationId());

        // C++ вернул номер кабинета напрямую
        int cabinetNumber = root.has("cabinet") ? root.get("cabinet").asInt(0) : 0;
        String status = root.has("status") ? root.get("status").asText("") : "";

        if (cabinetNumber > 0 && "answer".equals(status)) {
            // Номера кабинетов повторяются между корпусами (101 есть и в А, и в Б),
            // поэтому имя и корпус берём из ответа C++, а не из запроса.
            String name = text(root, "name", String.valueOf(cabinetNumber));
            String actualCorpus = text(root, "corpus", corpus);
            int floor = root.has("floor") ? root.get("floor").asInt(cabinetNumber / 100)
                                          : cabinetNumber / 100;

            Integer requestedFloor = request.getFloor();

            RoomInfo room = RoomInfo.builder()
                    .name(name)
                    .locationName(actualCorpus)
                    .locationId(request.getLocationId())
                    .floor(floor)
                    .category(text(root, "category", null))
                    .auditoryId(root.has("id") ? root.get("id").asInt() : null)
                    .bookingStart(text(root, "start_time", null))
                    .bookingEnd(text(root, "end_time", null))
                    .durationMinutes(root.has("duration") ? root.get("duration").asInt()
                                                          : request.getDurationMinutes())
                    .corpusMatched(actualCorpus.equals(corpus))
                    .floorMatched(requestedFloor == null || requestedFloor == 0
                                  || requestedFloor == floor)
                    .cameraFree(true)
                    .cameraStatus("свободен")
                    .scheduleFree(true)
                    .build();

            log.info("C++ found free cabinet: {} ({}, этаж {}), бронь {}-{}",
                    name, actualCorpus, floor, room.getBookingStart(), room.getBookingEnd());

            // C++ поставил временный резерв, но не знает, кто его заказал.
            // Закрепляем резерв за пользователем и отдаём его id: подтверждение
            // и отказ идут именно по нему.
            room.setHoldId(claimHold(root, request));

            return FindRoomResponse.builder()
                    .freeRooms(List.of(room))
                    .alternatives(Collections.emptyList())
                    .reason(null)
                    .build();
        }

        // Кабинет не найден
        String reason = "Свободных кабинетов не найдено";
        if (root.has("reason")) {
            reason = root.get("reason").asText(reason);
        }

        log.info("C++ did not find a free cabinet, status={}", status);

        return FindRoomResponse.builder()
                .freeRooms(Collections.emptyList())
                .alternatives(Collections.emptyList())
                .reason(reason)
                .build();
    }

    // ========================= Вспомогательные методы =========================

    /**
     * Парсит строку start_at (ISO 8601) в LocalDateTime.
     */
    private LocalDateTime parseStartAt(String startAt) {
        if (startAt == null || startAt.isBlank()) {
            throw new IllegalArgumentException("start_at не может быть пустым");
        }
        try {
            return LocalDateTime.parse(startAt);
        } catch (DateTimeParseException e) {
            try {
                return LocalDateTime.parse(startAt, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
            } catch (DateTimeParseException e2) {
                try {
                    return LocalDateTime.parse(startAt, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));
                } catch (DateTimeParseException e3) {
                    throw new IllegalArgumentException("Не удалось распарсить start_at: " + startAt);
                }
            }
        }
    }

    /**
     * Маппинг location_id бота → строка corpus.
     */
    private String resolveCorpus(String locationId) {
        if (locationId == null) return "Главный";
        String corpus = LOCATION_TO_CORPUS.get(locationId.toLowerCase());
        return corpus != null ? corpus : locationId;
    }

    /**
     * Закрепляет созданный C++ резерв за пользователем.
     * Ошибка не должна ломать ответ — поиск уже отработал.
     *
     * @return id резерва или null
     */
    private Integer claimHold(JsonNode root, FindRoomRequest request) {
        long userId = request.getTelegramUserId();   // 0 — пользователь не передан
        int auditoryId = root.has("id") ? root.get("id").asInt() : 0;
        String start = text(root, "start_time", null);
        String end = text(root, "end_time", null);

        if (userId == 0 || auditoryId == 0 || start == null || end == null) {
            log.warn("Пропускаю закрепление резерва: userId={}, auditoryId={}, {}-{}",
                    userId, auditoryId, start, end);
            return null;
        }

        try {
            // C++ резервирует на текущий день недели.
            int dayOfWeek = java.time.LocalDate.now(TimeUtil.ALMATY_ZONE).getDayOfWeek().getValue();
            return bookingService.claimHold(auditoryId, dayOfWeek,
                    java.time.LocalTime.parse(start), java.time.LocalTime.parse(end), userId);
        } catch (Exception e) {
            log.error("Не удалось закрепить резерв за пользователем {}: {}", userId, e.getMessage());
            return null;
        }
    }

    /** Достаёт строковое поле из ответа C++, если оно есть и непустое. */
    private static String text(JsonNode root, String field, String fallback) {
        if (root == null || !root.has(field)) {
            return fallback;
        }
        String value = root.get(field).asText("");
        return value.isBlank() ? fallback : value;
    }
}
