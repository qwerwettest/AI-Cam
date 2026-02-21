package kvt.model;

/**
 * Модель таблицы dbo.camera_cab_journal.
 */
public record CameraCabJournal(
        int id,
        String cameraIp,
        Integer idCab,
        String loginCamera,
        String passwordCamera,
        String portCamera
) {
}
