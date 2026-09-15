package kvt.db;

import kvt.model.CameraCabJournal;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Репозиторий для таблицы dbo.camera_cab_journal (JdbcTemplate, без JPA).
 */
@Repository
public class CameraCabJournalRepository {

    private final JdbcTemplate jdbcTemplate;

    public CameraCabJournalRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * Вставляет запись в dbo.camera_cab_journal с явным id (id НЕ identity).
     */
    public int insert(CameraCabJournal c) {
        return jdbcTemplate.update(
                "INSERT INTO dbo.camera_cab_journal (id, camera_ip, id_cab, login_camera, password_camera, port_camera) " +
                        "VALUES (?, ?, ?, ?, ?, ?)",
                c.id(),
                c.cameraIp(),
                c.idCab(),
                c.loginCamera(),
                c.passwordCamera(),
                c.portCamera()
        );
    }
}
