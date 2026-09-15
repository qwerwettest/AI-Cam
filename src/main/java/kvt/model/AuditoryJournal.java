package kvt.model;

import java.time.LocalTime;

/**
 * Модель таблицы dbo.auditory_journal.
 */
public record AuditoryJournal(
        int id,
        int audId,
        int dayOfWeek,
        LocalTime startTime,
        LocalTime endTime,
        Integer duration,
        Integer timeStatus
) {
}
