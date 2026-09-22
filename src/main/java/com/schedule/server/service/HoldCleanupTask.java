package com.schedule.server.service;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Снимает временные резервы, которые пользователь не подтвердил.
 *
 * <p>Резерв ставит C++ в момент поиска, чтобы кабинет не достался
 * одновременно двум людям. Если подтверждения не последовало, кабинет
 * должен вернуться в оборот — иначе он будет занят до конца интервала.
 */
@Component
@RequiredArgsConstructor
public class HoldCleanupTask {

    private final BookingService bookingService;

    @Value("${booking.hold-timeout-minutes:10}")
    private int holdTimeoutMinutes;

    @Scheduled(fixedDelayString = "${booking.hold-cleanup-interval-ms:60000}")
    public void purge() {
        bookingService.purgeStaleHolds(holdTimeoutMinutes);
    }
}
