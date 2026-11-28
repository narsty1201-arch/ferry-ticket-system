package com.ferry;

import com.ferry.util.DateRange;
import org.junit.Test;

import java.sql.Timestamp;
import java.time.LocalDate;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class DateRangeTest {

    @Test
    public void parsesInclusiveCalendarDatesAsHalfOpenTimestamps() {
        DateRange range = DateRange.parse("2026-11-01", "2026-11-29");

        assertFalse(range.hasError());
        assertEquals(Timestamp.valueOf(LocalDate.of(2026, 11, 1).atStartOfDay()),
                range.startInclusive());
        assertEquals(Timestamp.valueOf(LocalDate.of(2026, 11, 30).atStartOfDay()),
                range.endExclusive());
    }

    @Test
    public void allowsOpenEndedRanges() {
        DateRange range = DateRange.parse("", "2026-11-29");

        assertFalse(range.hasError());
        assertNull(range.startInclusive());
        assertEquals(Timestamp.valueOf(LocalDate.of(2026, 11, 30).atStartOfDay()),
                range.endExclusive());
    }

    @Test
    public void rejectsReversedOrInvalidDates() {
        assertTrue(DateRange.parse("2026-11-30", "2026-11-01").hasError());
        assertTrue(DateRange.parse("2026-13-01", "").hasError());
    }
}
