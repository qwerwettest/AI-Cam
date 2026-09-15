package com.schedule.server.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/**
 * DTO для одного урока.
 */
@Data
public class LessonDto {

    @JsonProperty("num")
    private String num;

    @JsonProperty("time")
    private String time;

    @JsonProperty("subject")
    private String subject;

    @JsonProperty("teacher")
    private String teacher;

    @JsonProperty("room")
    private String room;
}
