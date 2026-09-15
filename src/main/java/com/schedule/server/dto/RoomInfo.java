package com.schedule.server.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Информация об одном кабинете в ответе на поиск.
 * Формат совместим с тем, что ожидает Telegram-бот (formatter.py).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RoomInfo {

    /** Название/номер кабинета */
    private String name;

    /** Название локации (корпус) */
    @JsonProperty("location_name")
    private String locationName;

    /** Идентификатор локации */
    @JsonProperty("location_id")
    private String locationId;

    /** Этаж */
    private Integer floor;

    /** Вместимость */
    private Integer capacity;

    /** Свободен по расписанию */
    @JsonProperty("schedule_free")
    private Boolean scheduleFree;

    /** Свободен по камере */
    @JsonProperty("camera_free")
    private Boolean cameraFree;

    /** Статус камеры (текст) */
    @JsonProperty("camera_status")
    private String cameraStatus;

    /** ID аудитории в БД */
    @JsonProperty("auditory_id")
    private Integer auditoryId;
}
