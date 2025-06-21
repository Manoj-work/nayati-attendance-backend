package com.example.Attendance.util;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;

public class EpochUtil {

    // Convert LocalDate (midnight) to epoch seconds in the specified time zone
    public static long toEpochSeconds(LocalDate date, ZoneId zoneId) {
        return date.atStartOfDay(zoneId).toEpochSecond();
    }

    // Convert LocalDateTime to epoch seconds in the specified time zone
    public static long toEpochSeconds(LocalDateTime dateTime, ZoneId zoneId) {
        return dateTime.atZone(zoneId).toEpochSecond();
    }

    // Get current epoch seconds in UTC
    public static long currentEpochSeconds() {
        return Instant.now().getEpochSecond();
    }

    // Convert epoch seconds to LocalDateTime in a specific time zone
    public static LocalDateTime fromEpochSeconds(long epochSeconds, ZoneId zoneId) {
        return Instant.ofEpochSecond(epochSeconds).atZone(zoneId).toLocalDateTime();
    }

    // Convert epoch seconds to LocalDate in a specific time zone
    public static LocalDate fromEpochSecondsToDate(long epochSeconds, ZoneId zoneId) {
        return Instant.ofEpochSecond(epochSeconds).atZone(zoneId).toLocalDate();
    }
}
