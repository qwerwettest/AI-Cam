package com.schedule.server.service;

import com.schedule.server.dto.BookingDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;

import java.sql.Time;
import java.time.LocalTime;
import java.util.List;

/**
 * Брони пользователей: просмотр и отмена.
 *
 * <p>Саму бронь создаёт C++ внутри {@code CabinetFindRequest} — он про
 * пользователей ничего не знает, поэтому владельца проставляет Java сразу
 * после успешного ответа.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class BookingService {

    private final JdbcTemplate jdbcTemplate;

    private static final String[] DAY_NAMES = {
            "", "понедельник", "вторник", "среда", "четверг", "пятница", "суббота", "воскресенье"
    };

    private static final String SELECT_BOOKINGS = """
            SELECT j.id, j.aud_id, j.dayOfWeek, j.startTime, j.endTime, j.duration,
                   a.name, a.number, a.corpus, a.category
            FROM dbo.auditory_journal j
            JOIN dbo.auditory a ON a.id = j.aud_id
            WHERE j.telegram_user_id = ?
            ORDER BY j.dayOfWeek, j.startTime
            """;

    private final RowMapper<BookingDto> rowMapper = (rs, rowNum) -> {
        int dayOfWeek = rs.getInt("dayOfWeek");
        Time start = rs.getTime("startTime");
        Time end = rs.getTime("endTime");
        int number = rs.getInt("number");

        return BookingDto.builder()
                .id(rs.getInt("id"))
                .auditoryId(rs.getInt("aud_id"))
                .name(rs.getString("name"))
                .corpus(rs.getString("corpus"))
                .floor(number / 100)
                .category(rs.getString("category"))
                .dayOfWeek(dayOfWeek)
                .dayName(dayName(dayOfWeek))
                .startTime(format(start))
                .endTime(format(end))
                .durationMinutes(rs.getObject("duration") != null ? rs.getInt("duration") : null)
                .build();
    };

    /**
     * Привязывает только что созданную C++ бронь к пользователю.
     *
     * <p>C++ возвращает аудиторию и интервал, но не id строки журнала, поэтому
     * ищем её по совокупности полей. Берём последнюю подходящую запись без
     * владельца: занятия по расписанию так не затрагиваются.
     *
     * @return true, если владелец проставлен
     */
    public boolean assignOwner(int auditoryId, int dayOfWeek, LocalTime start, LocalTime end, long telegramUserId) {
        int updated = jdbcTemplate.update("""
                UPDATE dbo.auditory_journal
                SET telegram_user_id = ?
                WHERE id = (
                    SELECT TOP (1) id FROM dbo.auditory_journal
                    WHERE aud_id = ? AND dayOfWeek = ?
                      AND startTime = CAST(? AS time) AND endTime = CAST(? AS time)
                      AND telegram_user_id IS NULL
                      AND timeStatus IN (1, 2)
                    ORDER BY id DESC
                )
                """, telegramUserId, auditoryId, dayOfWeek, start.toString(), end.toString());

        if (updated == 0) {
            log.warn("Не удалось привязать бронь к пользователю {}: aud_id={}, {}-{}",
                    telegramUserId, auditoryId, start, end);
        }
        return updated > 0;
    }

    /** Все брони пользователя. */
    public List<BookingDto> findByUser(long telegramUserId) {
        return jdbcTemplate.query(SELECT_BOOKINGS, rowMapper, telegramUserId);
    }

    /**
     * Отменяет бронь. Удаление строки сразу освобождает кабинет: алгоритм
     * поиска в C++ считает занятыми только существующие записи журнала.
     *
     * <p>Владелец проверяется в самом запросе — чужую бронь отменить нельзя.
     *
     * @return true, если бронь принадлежала пользователю и была удалена
     */
    public boolean cancel(int bookingId, long telegramUserId) {
        int deleted = jdbcTemplate.update(
                "DELETE FROM dbo.auditory_journal WHERE id = ? AND telegram_user_id = ?",
                bookingId, telegramUserId);

        if (deleted > 0) {
            log.info("Бронь {} отменена пользователем {}", bookingId, telegramUserId);
        } else {
            log.warn("Отмена брони {} пользователем {} отклонена: не найдена или чужая",
                    bookingId, telegramUserId);
        }
        return deleted > 0;
    }

    private static String dayName(int dayOfWeek) {
        return dayOfWeek >= 1 && dayOfWeek <= 7 ? DAY_NAMES[dayOfWeek] : "";
    }

    private static String format(Time time) {
        return time != null ? time.toLocalTime().toString().substring(0, 5) : null;
    }
}
