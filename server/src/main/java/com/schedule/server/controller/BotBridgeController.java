package com.schedule.server.controller;

import com.schedule.server.dto.FindRoomRequest;
import com.schedule.server.dto.FindRoomResponse;
import com.schedule.server.service.BotBridgeService;
import com.schedule.server.tcp.CppTcpClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Bridge-контроллер: принимает запрос от Telegram-бота,
 * пересылает на C++ сервер, возвращает результат.
 */
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@Slf4j
public class BotBridgeController {

    private final CppTcpClient cppTcpClient;
    private final BotBridgeService botBridgeService;

    /**
     * POST /api/bridge
     * Принимает полный запрос от Telegram-бота (FindRoomQuery.to_java_payload):
     * <pre>
     * {
     *   "location_id": "main",
     *   "start_at": "2026-02-19T10:30:00",
     *   "duration_minutes": 60,
     *   "requested_by": {"telegram_user_id": 12345},
     *   "floor": 2,
     *   "filters": {"min_capacity": 20, "need_projector": true}
     * }
     * </pre>
     * Response: FindRoomResponse с free_rooms, alternatives, reason
     */
    @PostMapping("/bridge")
    public ResponseEntity<?> bridge(@RequestBody FindRoomRequest request) {
        try {
            log.info("Bridge request: location_id={}, start_at={}, duration={}, floor={}, user={}",
                    request.getLocationId(), request.getStartAt(),
                    request.getDurationMinutes(), request.getFloor(),
                    request.getTelegramUserId());

            FindRoomResponse response = botBridgeService.findRooms(request);

            log.info("Bridge response: {} free rooms, {} alternatives",
                    response.getFreeRooms() != null ? response.getFreeRooms().size() : 0,
                    response.getAlternatives() != null ? response.getAlternatives().size() : 0);

            return ResponseEntity.ok(response);

        } catch (IllegalArgumentException e) {
            log.warn("Bridge bad request: {}", e.getMessage());

            Map<String, String> errorBody = new LinkedHashMap<>();
            errorBody.put("status", "error");
            errorBody.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(errorBody);

        } catch (Exception e) {
            log.error("Bridge error: {}", e.getMessage(), e);

            Map<String, String> errorBody = new LinkedHashMap<>();
            errorBody.put("status", "error");
            errorBody.put("message", e.getMessage());

            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorBody);
        }
    }

    /**
     * GET /api/bridge
     * Health-check / простой запрос для проверки работоспособности.
     * Используется Telegram-ботом в /status.
     */
    @GetMapping("/bridge")
    public ResponseEntity<Map<String, Object>> bridgeHealth() {
        log.info("Health check: GET /api/bridge");
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", "ok");
        body.put("service", "schedule-server");
        body.put("cppServer", cppTcpClient != null ? "configured" : "not configured");
        body.put("pendingCppRequests", cppTcpClient.getPendingCount());
        return ResponseEntity.ok(body);
    }

}
