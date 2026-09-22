package com.schedule.server.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Одна бронь пользователя — строка dbo.auditory_journal,
 * обогащённая данными аудитории.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BookingDto {

    /** id записи в auditory_journal — по нему выполняется отмена */
    private Integer id;

    @JsonProperty("auditory_id")
    private Integer auditoryId;

    /** Имя кабинета, например А-201 */
    private String name;

    /** Корпус */
    private String corpus;

    /** Этаж */
    private Integer floor;

    /** Тип помещения */
    private String category;

    /** 1 = понедельник … 7 = воскресенье */
    @JsonProperty("day_of_week")
    private Integer dayOfWeek;

    /** Название дня недели */
    @JsonProperty("day_name")
    private String dayName;

    @JsonProperty("start_time")
    private String startTime;

    @JsonProperty("end_time")
    private String endTime;

    @JsonProperty("duration_minutes")
    private Integer durationMinutes;
}
