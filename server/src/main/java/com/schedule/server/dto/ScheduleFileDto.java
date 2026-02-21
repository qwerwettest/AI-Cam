package com.schedule.server.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

/**
 * Корневой DTO для входящего JSON файла расписания.
 */
@Data
public class ScheduleFileDto {

    @JsonProperty("fileName")
    private String fileName;

    @JsonProperty("sheet")
    private String sheet;

    @JsonProperty("rows")
    private List<List<String>> rows;

    @JsonProperty("schedule")
    private ScheduleDto schedule;
}
