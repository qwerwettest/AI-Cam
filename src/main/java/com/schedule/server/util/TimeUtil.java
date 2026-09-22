package com.schedule.server.util;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Утилита для работы со временем (зона Asia/Almaty).
 */
public final class TimeUtil {

    /** Часовой пояс проекта. */
    public static final ZoneId ALMATY_ZONE = ZoneId.of("Asia/Almaty");
    private static final DateTimeFormatter HH_MM = DateTimeFormatter.ofPattern("HH:mm");

    private TimeUtil() {
    }

    /**
     * Возвращает текущий час (0..23) в зоне Asia/Almaty без округления.
     */
    public static int currentStartHour() {
        return ZonedDateTime.now(ALMATY_ZONE).getHour();
    }

    /**
     * Возвращает текущее время в формате "HH:mm" (зона Asia/Almaty).
     */
    public static String currentStartTime() {
        return ZonedDateTime.now(ALMATY_ZONE).format(HH_MM);
    }
}
