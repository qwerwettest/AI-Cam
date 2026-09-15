package com.schedule.server.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * Сервис для получения данных из таблицы auditory_journal через JdbcTemplate.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ScheduleLookupService {

    private final JdbcTemplate jdbcTemplate;

    /**
     * Возвращает duration из первой записи dbo.auditory_journal.
     * TODO: заменить на полноценную связь id→duration, когда появится маппинг.
     *
     * @return duration в минутах
     * @throws IllegalStateException если таблица пуста или duration == null
     */
    public int getDurationFallback() {
        log.debug("Fetching fallback duration from dbo.auditory_journal");

        Integer duration = jdbcTemplate.query(
                "SELECT TOP 1 duration FROM dbo.auditory_journal",
                rs -> rs.next() ? rs.getObject("duration", Integer.class) : null
        );

        if (duration == null) {
            throw new IllegalStateException(
                    "Не удалось получить duration: таблица auditory_journal пуста или duration = NULL");
        }

        log.debug("Fallback duration = {}", duration);
        return duration;
    }
}
