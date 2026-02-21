package com.schedule.server.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

/**
 * DTO для расписания одного дня.
 */
@Data
public class DayScheduleDto {

    @JsonProperty("day")
    private String day;

    @JsonProperty("lessons")
    private List<LessonDto> lessons;
}
