package com.schedule.server.controller;

import com.schedule.server.dto.BookingDto;
import com.schedule.server.service.BookingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Просмотр и отмена собственных броней.
 */
@RestController
@RequestMapping("/api/bookings")
@RequiredArgsConstructor
@Slf4j
public class BookingController {

    private final BookingService bookingService;

    /** Брони пользователя. */
    @GetMapping
    public ResponseEntity<Map<String, Object>> list(@RequestParam("telegram_user_id") long telegramUserId) {
        List<BookingDto> bookings = bookingService.findByUser(telegramUserId);
        log.info("Bookings for user {}: {}", telegramUserId, bookings.size());
        return ResponseEntity.ok(Map.of("bookings", bookings));
    }

    /**
     * Подтверждение резерва: кабинет становится бронью.
     *
     * <p>До подтверждения кабинет удерживается, но снимается по таймауту,
     * если пользователь так и не решился.
     */
    @PostMapping("/{id}/confirm")
    public ResponseEntity<Map<String, Object>> confirm(
            @PathVariable("id") int id,
            @RequestParam("telegram_user_id") long telegramUserId) {

        if (bookingService.confirm(id, telegramUserId)) {
            return ResponseEntity.ok(Map.of("status", "ok", "confirmed", id));
        }
        return ResponseEntity.status(404).body(Map.of(
                "status", "error",
                "message", "Резерв не найден, принадлежит другому пользователю или уже истёк"));
    }

    /**
     * Отмена брони или отказ от резерва. Пользователь передаётся явно и
     * проверяется: тронуть чужую запись нельзя.
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, Object>> cancel(
            @PathVariable("id") int id,
            @RequestParam("telegram_user_id") long telegramUserId) {

        boolean cancelled = bookingService.cancel(id, telegramUserId);

        if (cancelled) {
            return ResponseEntity.ok(Map.of("status", "ok", "cancelled", id));
        }
        return ResponseEntity.status(404).body(Map.of(
                "status", "error",
                "message", "Бронь не найдена или принадлежит другому пользователю"));
    }
}
