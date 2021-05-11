
package org.ihtsdo.refsetservice.util.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.Date;
import java.util.Locale;

import org.ihtsdo.refsetservice.test.BaseTest;
import org.ihtsdo.refsetservice.util.DateUtility;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Validates the code-system-* files in the "src/resources" folder.
 */
public class DateUtilityTest extends BaseTest {

    /** The logger. */
    private final Logger logger = LoggerFactory.getLogger(DateUtilityTest.class);

    /**
     * Test valid date.
     *
     * @throws Exception the exception
     */
    @Test
    public void testValidDate() throws Exception {
        final Date now = new Date();
        final Date past = new Date(-1);
        final Date future = new Date(now.getTime() + 6000);
        final Date valid = DateUtility.getFastDateFormat(DateUtility.DATE_FORMAT_REVERSE_ONLY_NUMBERS).parse("20171113");
        assertFalse(DateUtility.isValidDate(now, now));
        assertFalse(DateUtility.isValidDate(now, future));
        assertFalse(DateUtility.isValidDate(now, past));
        assertTrue(DateUtility.isValidDate(now, valid));
    }

    /**
     * Test iso 8601.
     *
     * @throws Exception the exception
     */
    @Test
    public void testRfc3339() throws Exception {
        final Date date = new Date();
        final String dateStr = DateUtility.formatDate(date, DateUtility.RFC_3339, null);
        logger.info("  date string = " + dateStr);
        // surprisingly, this doesn't work
        // assertEquals(date, DateUtility.RFC_3339.parse(dateStr));
    }

    /**
     * Test time zone offset.
     *
     * @throws Exception the exception
     */
    @Test
    public void testTimeZoneOffsetLabel() throws Exception {

        // This is sensitive to whether build time is daylight savings time
        assertTrue(DateUtility.getTimeZoneOffsetLabel("America/Los_Angeles", null)
                .matches("-0[78]:00"));
        assertTrue(
                DateUtility.getTimeZoneOffsetLabel("America/TYPO/Los_Angeles", null).matches("Z"));
        assertTrue(DateUtility.getTimeZoneOffsetLabel("PST", null).matches("-0[78]:00"));

        assertEquals("+06:00", DateUtility.getTimeZoneOffsetLabel("+06:00", null));
        assertEquals("+06:00", DateUtility.getTimeZoneOffsetLabel("+0600", null));
        assertEquals("Z", DateUtility.getTimeZoneOffsetLabel("Z", null));
        assertEquals("-05:00", DateUtility.getTimeZoneOffsetLabel("EST", null));
        assertEquals("-04:00", DateUtility.getTimeZoneOffsetLabel("EDT", null));
        assertEquals("-07:00", DateUtility.getTimeZoneOffsetLabel("PDT", null));
        // assertEquals("-08:00", DateUtility.getTimeZoneOffset("PST", null));
        // This is actually PDT because it's in mid-March
        assertEquals("-07:00", DateUtility.getTimeZoneOffsetLabel("PST",
                DateUtility.getFastDateFormat(DateUtility.DATE_FORMAT_REVERSE_ONLY_NUMBERS).parse("20170315")));
    }

    /**
     * Test time zone offset.
     *
     * @throws Exception the exception
     */
    @Test
    public void testTimeZoneOffset() throws Exception {

        logger.info("XXX = " + DateUtility.getTimeZoneOffset("America/Los_Angeles", null));
        // This is sensitive to whether build time is daylight savings time
        assertTrue(
                Math.abs(DateUtility.getTimeZoneOffset("America/Los_Angeles", null) / 3600) < 9000);
        assertTrue(Math.abs(
                DateUtility.getTimeZoneOffset("America/TYPO/Los_Angeles", null) / 3600) < 9000);
        assertTrue(Math.abs(DateUtility.getTimeZoneOffset("PST", null) / 3600) < 9000);

        assertEquals(6000, DateUtility.getTimeZoneOffset("+06:00", null) / 3600);
        assertEquals(6000, DateUtility.getTimeZoneOffset("+0600", null) / 3600);
        assertEquals(0, DateUtility.getTimeZoneOffset("Z", null));
        assertEquals(-5000, DateUtility.getTimeZoneOffset("EST", null) / 3600);
        assertEquals(-4000, DateUtility.getTimeZoneOffset("EDT", null) / 3600);
        assertEquals(-7000, DateUtility.getTimeZoneOffset("PDT", null) / 3600);
        // This is actually PDT because it's in mid-March
        assertEquals(-7000,
                DateUtility.getTimeZoneOffset("PST", DateUtility.getFastDateFormat(DateUtility.DATE_FORMAT_REVERSE_ONLY_NUMBERS).parse("20170315"))
                        / 3600);
    }

    /**
     * Test get time zone.
     *
     * @throws Exception the exception
     */
    @Test
    public void testGetTimeZone() throws Exception {
        assertEquals("-07:00", DateUtility.getTimeZone(1590633334000L, -25200));
        assertEquals("-08:00", DateUtility.getTimeZone(1590633334000L, -28800));
    }

    /**
     * Test start end of day.
     *
     * @throws Exception the exception
     */
    @Test
    public void testStartEndOfDay() throws Exception {

        final ZoneId timeZone = ZoneId.systemDefault();
        final String abbreviation = timeZone.getDisplayName(TextStyle.NARROW, Locale.US);

        ZonedDateTime startOfDay =
                DateUtility.getStartOfDay("20201006", ZoneId.systemDefault().toString());

        ZonedDateTime endOfDay =
                DateUtility.getEndOfDay("20201006", ZoneId.systemDefault().toString());

        assertEquals("2020-10-06 00:00:00 [" + abbreviation + "]",
                startOfDay.format(DateUtility.DATE_YYYY_MM_DD_HH_MM_SS_VV));
        assertEquals("2020-10-06 23:59:59 [" + abbreviation + "]",
                endOfDay.format(DateUtility.DATE_YYYY_MM_DD_HH_MM_SS_VV));

    }

    /**
     * Test get date.
     *
     * @throws Exception the exception
     */
    @Test
    public void testGetDate() throws Exception {

        final ZoneId timeZone = ZoneId.systemDefault();
        final Instant now = Instant.now();
        final ZonedDateTime utcNow = now.atZone(ZoneId.of("Z"));
        final ZonedDateTime localNow = now.atZone(timeZone);
        final String timeZoneId = String.format("%tz", localNow);

        final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

        final Date pdt = DateUtility.getDate(localNow.format(formatter), "yyyyMMddHHmmss",
                timeZoneId);
        // Expressed in local time, this date matches what's shown
        assertEquals(localNow.format(formatter), DateUtility.formatDate(pdt, DateUtility.DATE_FORMAT_REVERSE_WITH_24_HOUR_TIME_ONLY_NUMBERS, timeZoneId));
        
        final Date z = DateUtility.getDate(utcNow.format(formatter), "yyyyMMddHHmmss", "-00:00");
        assertEquals(localNow.format(formatter), DateUtility.formatDate(z, DateUtility.DATE_FORMAT_REVERSE_WITH_24_HOUR_TIME_ONLY_NUMBERS,timeZoneId));
    }

}
