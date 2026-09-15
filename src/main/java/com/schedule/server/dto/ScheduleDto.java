package com.schedule.server.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * DTO для объекта schedule.
 */
@Data
public class ScheduleDto {

    @JsonProperty("college")
    private String college;

    @JsonProperty("academic_year")
    private String academicYear;

    @JsonProperty("course")
    private int course;

    @JsonProperty("semester")
    private int semester;

    @JsonProperty("department")
    private String department;

    @JsonProperty("groups_schedule")
    private Map<String, List<DayScheduleDto>> groupsSchedule;
}
