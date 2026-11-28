package com.ferry.util;

import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;

/**
 * 报表日期区间：开始日 00:00（含）到结束日次日 00:00（不含）。
 * 使用半开区间可避免 DATETIME 的毫秒/23:59:59.997 边界遗漏。
 */
public final class DateRange {
    private final LocalDate start;
    private final LocalDate end;
    private final String error;

    private DateRange(LocalDate start, LocalDate end, String error) {
        this.start = start;
        this.end = end;
        this.error = error;
    }

    public static DateRange parse(String startText, String endText) {
        LocalDate start;
        LocalDate end;
        try {
            start = parseDate(startText);
            end = parseDate(endText);
        } catch (DateTimeParseException e) {
            return new DateRange(null, null, "日期格式不正确，请使用日期选择器");
        }
        if (start != null && end != null && start.isAfter(end)) {
            return new DateRange(null, null, "开始日期不能晚于结束日期");
        }
        return new DateRange(start, end, null);
    }

    public static DateRange all() {
        return new DateRange(null, null, null);
    }

    private static LocalDate parseDate(String text) {
        if (text == null || text.trim().isEmpty()) return null;
        return LocalDate.parse(text.trim());
    }

    public Timestamp startInclusive() {
        return start == null ? null : Timestamp.valueOf(start.atStartOfDay());
    }

    public Timestamp endExclusive() {
        return end == null ? null : Timestamp.valueOf(end.plusDays(1).atStartOfDay());
    }

    public String startText() {
        return start == null ? "" : start.toString();
    }

    public String endText() {
        return end == null ? "" : end.toString();
    }

    public String error() {
        return error;
    }

    public boolean hasError() {
        return error != null;
    }
}
