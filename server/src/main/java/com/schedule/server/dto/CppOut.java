package com.schedule.server.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Ответ от C++ сервера.
 * { "id": <int>, "cabinet_number": "<string>" }
 */
public record CppOut(
        int id,
        @JsonProperty("cabinet_number") String cabinetNumber
) {
}
