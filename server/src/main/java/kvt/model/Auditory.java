package kvt.model;

/**
 * Модель таблицы dbo.auditory.
 */
public record Auditory(
        int id,
        String name,
        Integer number,
        String corpus,
        String category
) {
}
