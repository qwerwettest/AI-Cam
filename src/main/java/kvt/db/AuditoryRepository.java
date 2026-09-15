package kvt.db;

import kvt.model.Auditory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Types;
import java.util.List;
import java.util.Optional;

/**
 * Репозиторий для таблицы dbo.auditory (JdbcTemplate, без JPA).
 */
@Repository
public class AuditoryRepository {

    private final JdbcTemplate jdbcTemplate;
    private final NamedParameterJdbcTemplate namedJdbc;

    public AuditoryRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
        this.namedJdbc = new NamedParameterJdbcTemplate(jdbcTemplate);
    }

    private static final RowMapper<Auditory> ROW_MAPPER = (ResultSet rs, int rowNum) -> new Auditory(
            rs.getInt("id"),
            rs.getString("name"),
            rs.getObject("number") != null ? rs.getInt("number") : null,
            rs.getString("corpus"),
            rs.getString("category")
    );

    /**
     * Читает все записи из dbo.auditory.
     */
    public List<Auditory> findAll() {
        return jdbcTemplate.query("SELECT id, name, number, corpus, category FROM dbo.auditory", ROW_MAPPER);
    }

    /**
     * Ищет аудиторию по имени.
     */
    public Optional<Auditory> findByName(String name) {
        List<Auditory> list = jdbcTemplate.query(
                "SELECT id, name, number, corpus, category FROM dbo.auditory WHERE name = ?",
                ROW_MAPPER, name);
        return list.isEmpty() ? Optional.empty() : Optional.of(list.get(0));
    }

    /**
     * Возвращает максимальный id (или 0 если таблица пуста).
     */
    public int findMaxId() {
        Integer max = jdbcTemplate.queryForObject("SELECT ISNULL(MAX(id), 0) FROM dbo.auditory", Integer.class);
        return max != null ? max : 0;
    }

    /**
     * Вставляет запись в dbo.auditory (id генерируется автоматически — IDENTITY).
     * @return сгенерированный id новой записи
     */
    public int insert(Auditory a) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(
                    "INSERT INTO dbo.auditory (name, number, corpus, category) VALUES (?, ?, ?, ?)",
                    new String[]{"id"}
            );
            ps.setString(1, a.name());
            if (a.number() != null) {
                ps.setInt(2, a.number());
            } else {
                ps.setNull(2, Types.INTEGER);
            }
            ps.setString(3, a.corpus());
            ps.setString(4, a.category());
            return ps;
        }, keyHolder);
        return keyHolder.getKey().intValue();
    }

    /**
     * Ищет аудитории по корпусу (corpus).
     */
    public List<Auditory> findByCorpus(String corpus) {
        return jdbcTemplate.query(
                "SELECT id, name, number, corpus, category FROM dbo.auditory WHERE corpus = ?",
                ROW_MAPPER, corpus);
    }

    /**
     * Ищет аудитории по корпусу и этажу.
     * Этаж определяется по первой цифре номера кабинета (number / 100) или null.
     */
    public List<Auditory> findByCorpusAndFloor(String corpus, int floor) {
        return jdbcTemplate.query(
                "SELECT id, name, number, corpus, category FROM dbo.auditory " +
                        "WHERE corpus = ? AND number IS NOT NULL AND (number / 100) = ?",
                ROW_MAPPER, corpus, floor);
    }

    /**
     * Гибкий поиск аудиторий с необязательными фильтрами.
     *
     * @param corpus корпус (обязательный)
     * @param floor  этаж (опционально, null = любой)
     * @return подходящие аудитории
     */
    public List<Auditory> findFiltered(String corpus, Integer floor) {
        StringBuilder sql = new StringBuilder(
                "SELECT id, name, number, corpus, category FROM dbo.auditory WHERE corpus = :corpus");
        MapSqlParameterSource params = new MapSqlParameterSource("corpus", corpus);

        if (floor != null) {
            sql.append(" AND number IS NOT NULL AND (number / 100) = :floor");
            params.addValue("floor", floor);
        }

        sql.append(" ORDER BY number");
        return namedJdbc.query(sql.toString(), params, ROW_MAPPER);
    }
}
