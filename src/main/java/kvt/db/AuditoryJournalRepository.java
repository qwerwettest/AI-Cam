package kvt.db;

import kvt.model.AuditoryJournal;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Time;
import java.sql.Types;
import java.time.LocalTime;
import java.util.List;

/**
 * Репозиторий для таблицы dbo.auditory_journal (JdbcTemplate, без JPA).
 */
@Repository
public class AuditoryJournalRepository {

    private final JdbcTemplate jdbcTemplate;

    public AuditoryJournalRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    private static final RowMapper<AuditoryJournal> ROW_MAPPER = (ResultSet rs, int rowNum) -> new AuditoryJournal(
            rs.getInt("id"),
            rs.getInt("aud_id"),
            rs.getInt("dayOfWeek"),
            rs.getTime("startTime") != null ? rs.getTime("startTime").toLocalTime() : null,
            rs.getTime("endTime") != null ? rs.getTime("endTime").toLocalTime() : null,
            rs.getObject("duration") != null ? rs.getInt("duration") : null,
            rs.getObject("timeStatus") != null ? rs.getInt("timeStatus") : null
    );

    /**
     * Читает все записи из dbo.auditory_journal.
     */
    public List<AuditoryJournal> findAll() {
        return jdbcTemplate.query(
                "SELECT id, aud_id, dayOfWeek, startTime, endTime, duration, timeStatus FROM dbo.auditory_journal",
                ROW_MAPPER);
    }

    /**
     * Ищет журнальные записи по id аудитории.
     */
    public List<AuditoryJournal> findByAudId(int audId) {
        return jdbcTemplate.query(
                "SELECT id, aud_id, dayOfWeek, startTime, endTime, duration, timeStatus FROM dbo.auditory_journal WHERE aud_id = ?",
                ROW_MAPPER, audId);
    }

    /**
     * Возвращает максимальный id (или 0 если таблица пуста).
     */
    public int findMaxId() {
        Integer max = jdbcTemplate.queryForObject("SELECT ISNULL(MAX(id), 0) FROM dbo.auditory_journal", Integer.class);
        return max != null ? max : 0;
    }

    /**
     * Вставляет запись в dbo.auditory_journal (id генерируется автоматически — IDENTITY).
     * @return сгенерированный id новой записи
     */
    public int insert(AuditoryJournal j) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(
                    "INSERT INTO dbo.auditory_journal (aud_id, dayOfWeek, startTime, endTime, duration, timeStatus) " +
                            "VALUES (?, ?, ?, ?, ?, ?)",
                    new String[]{"id"}
            );
            ps.setInt(1, j.audId());
            ps.setInt(2, j.dayOfWeek());
            if (j.startTime() != null) {
                ps.setTime(3, Time.valueOf(j.startTime()));
            } else {
                ps.setNull(3, Types.TIME);
            }
            if (j.endTime() != null) {
                ps.setTime(4, Time.valueOf(j.endTime()));
            } else {
                ps.setNull(4, Types.TIME);
            }
            if (j.duration() != null) {
                ps.setInt(5, j.duration());
            } else {
                ps.setNull(5, Types.INTEGER);
            }
            if (j.timeStatus() != null) {
                ps.setInt(6, j.timeStatus());
            } else {
                ps.setNull(6, Types.INTEGER);
            }
            return ps;
        }, keyHolder);
        return keyHolder.getKey().intValue();
    }

    /**
     * Проверяет, занята ли аудитория в указанный день недели и интервал времени.
     * Возвращает true, если есть пересечение с существующими записями (аудитория ЗАНЯТА).
     *
     * @param audId     ID аудитории
     * @param dayOfWeek день недели (1 = пн, ..., 7 = вс)
     * @param start     время начала
     * @param end       время конца
     * @return true если аудитория занята в данный интервал
     */
    public boolean isBusy(int audId, int dayOfWeek, LocalTime start, LocalTime end) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM dbo.auditory_journal " +
                        "WHERE aud_id = ? AND dayOfWeek = ? AND timeStatus = 1 " +
                        "AND startTime < CAST(? AS time) AND endTime > CAST(? AS time)",
                Integer.class,
                audId, dayOfWeek,
                end.toString(), start.toString()
        );
        return count != null && count > 0;
    }

    /**
     * Возвращает все занятые записи для аудитории в указанный день.
     */
    public List<AuditoryJournal> findByAudIdAndDay(int audId, int dayOfWeek) {
        return jdbcTemplate.query(
                "SELECT id, aud_id, dayOfWeek, startTime, endTime, duration, timeStatus " +
                        "FROM dbo.auditory_journal WHERE aud_id = ? AND dayOfWeek = ? AND timeStatus = 1 " +
                        "ORDER BY startTime",
                ROW_MAPPER, audId, dayOfWeek);
    }
}
