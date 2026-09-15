package com.schedule.server.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.schedule.server.dto.FindRoomRequest;
import com.schedule.server.dto.FindRoomResponse;
import com.schedule.server.dto.RoomInfo;
import com.schedule.server.tcp.CppTcpClient;
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

    /**
     * Маппинг location_id (из конфига бота) → corpus (строка для C++ сервера).
     */
    private static final Map<String, String> LOCATION_TO_CORPUS = Map.of(
            "main", "Главный",
            "corp_a", "Корпус А",
            "corp_b", "Корпус Б"
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
            // Кабинет найден — формируем один элемент в free_rooms
            RoomInfo room = RoomInfo.builder()
                    .name(String.valueOf(cabinetNumber))
                    .locationName(corpus)
                    .locationId(request.getLocationId())
                    .floor(cabinetNumber / 100)       // 103 → этаж 1, 215 → этаж 2
                    .cameraFree(true)
                    .cameraStatus("свободен")
                    .scheduleFree(true)
                    .build();

            log.info("C++ found free cabinet: {}", cabinetNumber);

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
}
