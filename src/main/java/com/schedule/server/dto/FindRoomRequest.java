package com.schedule.server.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * Входящий JSON от Telegram-бота (FindRoomQuery.to_java_payload).
 * <pre>
 * {
 *   "location_id": "main",
 *   "start_at": "2026-02-19T10:30:00",
 *   "duration_minutes": 60,
 *   "requested_by": { "telegram_user_id": 12345 },
 *   "floor": 2,                          // optional
 *   "filters": {                          // optional
 *       "min_capacity": 20,
 *       "need_projector": true
 *   }
 * }
 * </pre>
 */
@Data
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class FindRoomRequest {

    @JsonProperty("location_id")
    private String locationId;

    @JsonProperty("start_at")
    private String startAt;

    @JsonProperty("duration_minutes")
    private int durationMinutes;

    @JsonProperty("requested_by")
    private Map<String, Object> requestedBy;

    private Integer floor;

    private Map<String, Object> filters;

    // ---- вспомогательные геттеры ----

    public long getTelegramUserId() {
        if (requestedBy == null) return 0;
        Object uid = requestedBy.get("telegram_user_id");
        if (uid instanceof Number n) return n.longValue();
        return 0;
    }

    public Integer getMinCapacity() {
        if (filters == null) return null;
        Object val = filters.get("min_capacity");
        if (val instanceof Number n) return n.intValue();
        return null;
    }

    public Boolean getNeedProjector() {
        if (filters == null) return null;
        Object val = filters.get("need_projector");
        if (val instanceof Boolean b) return b;
        return null;
    }
}
