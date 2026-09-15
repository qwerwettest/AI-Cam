package com.schedule.server.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Ответ на поиск свободного кабинета.
 * Формат совместим с тем, что ожидает Telegram-бот (formatter.py):
 * <pre>
 * {
 *   "free_rooms": [ {...}, ... ],
 *   "alternatives": [ {...}, ... ],
 *   "reason": "..."
 * }
 * </pre>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FindRoomResponse {

    /** Список свободных кабинетов (и по расписанию, и по камере) */
    @JsonProperty("free_rooms")
    private List<RoomInfo> freeRooms;

    /** Альтернативы — свободны по расписанию, но заняты по камере */
    private List<RoomInfo> alternatives;

    /** Сообщение / причина, если ничего не найдено */
    private String reason;
}
