/*
 * Copyright 2024 SNOMED International - All Rights Reserved.
 *
 * NOTICE:  All information contained herein is, and remains the property of SNOMED International
 * The intellectual and technical concepts contained herein are proprietary to
 * SNOMED International and may be covered by U.S. and Foreign Patents, patents in process,
 * and are protected by trade secret or copyright law.  Dissemination of this information
 * or reproduction of this material is strictly forbidden.
 */

package org.ihtsdo.refsetservice.util;

import java.text.SimpleDateFormat;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.temporal.ChronoField;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.TimeZone;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.time.DateUtils;
import org.apache.commons.lang3.time.FastDateFormat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Utility class for interacting with dates.
 */
public final class DateUtility {

    /** The Constant LOG. */
    private static final Logger LOG = LoggerFactory.getLogger(DateUtility.class);

    /** The zone map. */
    private static Map<String, String> zoneMap = new HashMap<>();
    static {
        zoneMap.put("EDT", "-04:00");
        zoneMap.put("PDT", "-07:00");
    }

    /** The Constant RFC_3339. */
    public static final String RFC_3339 = "yyyy-MM-dd'T'HH:mm:ssZZZZZ";

    /** The Constant hh:mm:ss A. */
    public static final String TIME_FORMAT = "hh:mm:ss A";

    /** The Constant HH:mm:ss. */
    public static final String TIME_FORMAT_24_HOUR = "HH:mm:ss";

    /** The Constant HHmmss. */
    public static final String TIME_FORMAT_24_HOUR_ONLY_NUMBERS = "HHmmss";

    /** The Constant MM/dd/yyyy. */
    public static final String DATE_FORMAT_US_STANDARD = "MM/dd/yyyy";

    /** The Constant MM-dd-yyyy. */
    public static final String DATE_FORMAT_US_DASH = "MM-dd-yyyy";

    /** The Constant MMddyyyy. */
    public static final String DATE_FORMAT_US_STANDARD_ONLY_NUMBERS = "MMddyyyy";

    /** The Constant MM/dd/yyyy hh:mm:ss A. */
    public static final String DATE_FORMAT_US_STANDARD_WITH_TIME = DATE_FORMAT_US_STANDARD + " " + TIME_FORMAT;

    /** The Constant MM-dd-yyyy hh:mm:ss A. */
    public static final String DATE_FORMAT_US_DASH_WITH_TIME = DATE_FORMAT_US_DASH + " " + TIME_FORMAT;

    /** The Constant MM/dd/yyyy HH:mm:ss. */
    public static final String DATE_FORMAT_US_STANDARD_WITH_24_HOUR_TIME = DATE_FORMAT_US_STANDARD + " " + TIME_FORMAT_24_HOUR;

    /** The Constant MM-dd-yyyy HH:mm:ss. */
    public static final String DATE_FORMAT_US_DASH_WITH_24_HOUR_TIME = DATE_FORMAT_US_DASH + " " + TIME_FORMAT_24_HOUR;

    /** The Constant yyyy-MM-dd. */
    public static final String DATE_FORMAT_REVERSE = "yyyy-MM-dd";

    /** The Constant yyyyMMdd. */
    public static final String DATE_FORMAT_REVERSE_ONLY_NUMBERS = "yyyyMMdd";

    /** The Constant yyyy-MM-dd hh:mm:ss A. */
    public static final String DATE_FORMAT_REVERSE_WITH_TIME = DATE_FORMAT_REVERSE + " " + TIME_FORMAT;

    /** The Constant yyyy-MM-dd HH:mm:ss. */
    public static final String DATE_FORMAT_REVERSE_WITH_24_HOUR_TIME = DATE_FORMAT_REVERSE + " " + TIME_FORMAT_24_HOUR;

    /** The Constant yyyyMMddHHmmss. */
    public static final String DATE_FORMAT_REVERSE_WITH_24_HOUR_TIME_ONLY_NUMBERS = DATE_FORMAT_REVERSE_ONLY_NUMBERS + TIME_FORMAT_24_HOUR_ONLY_NUMBERS;

    /** The Constant date format year. */
    public static final String DATE_FORMAT_YEAR = "yyyy";

    /** The Constant date format timezone. */
    public static final String DATE_FORMAT_TIMEZONE = "XXX";

    /** The Constant date format milliesonds. */
    public static final String DATE_FORMAT_MILLISECONDS = "SSS";

    /** The Constant DAY. */
    public static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("yyyyMMdd");

    /** The format ex: 2020-10-06 00:00:00 [America/Los_Angeles]. */
    public static final DateTimeFormatter DATE_YYYY_MM_DD_HH_MM_SS_VV = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss '['VV']'");

    /**
     * Instantiates an empty {@link DateUtility}.
     */
    private DateUtility() {

        // n/a
    }

    /**
     * Returns a FastDateFormat in the format pattern given.
     *
     * @param format the format patern
     * @return the end of day
     */
    public static FastDateFormat getFastDateFormat(final String format) {

        return FastDateFormat.getInstance(format);
    }

    /**
     * Returns the time zone offset.
     *
     * @param tz the tz
     * @param date the date
     * @return the time zone offset
     */
    public static String getTimeZoneOffsetLabel(final String tz, final Date date) {

        // Return UTC for null timezone.
        if (tz == null) {
            return ZoneOffset.UTC.getId();
        }

        final Instant instant = date == null ? Instant.now() : date.toInstant();
        // First try this style
        try {
            return ZoneId.of(tz).getRules().getOffset(instant).getId();
        } catch (final Exception e) {
            // n/a
        }
        try {
            return ZoneOffset.of(tz).getId();
        } catch (final Exception e) {
            // n/a
        }
        try {
            final String x = TimeZone.getTimeZone(tz).toZoneId().getRules().getOffset(instant).getId();
            if ("Z".equals(x)) {
                return ZoneId.of(tz, zoneMap).getRules().getOffset(instant).getId();
            }
            if (x != null) {
                return x;
            }
        } catch (final Exception e) {
            // n/a
        }
        return ZoneOffset.UTC.getId();
    }

    /**
     * Returns the time zone offset.
     *
     * @param tz the tz
     * @param date the date
     * @return the time zone offset
     */
    public static long getTimeZoneOffset(final String tz, final Date date) {

        if (tz == null) {
            return ZoneOffset.UTC.getTotalSeconds() * 1000;
        }

        final Instant instant = date == null ? Instant.now() : date.toInstant();
        // First try this style
        try {
            return ZoneId.of(tz).getRules().getOffset(instant).getTotalSeconds() * 1000;
        } catch (final Exception e) {
            // n/a
        }
        try {
            return ZoneOffset.of(tz).getTotalSeconds() * 1000;
        } catch (final Exception e) {
            // n/a
        }
        try {
            final String x = TimeZone.getTimeZone(tz).toZoneId().getRules().getOffset(instant).getId();
            if ("Z".equals(x)) {
                return ZoneId.of(tz, zoneMap).getRules().getOffset(instant).getTotalSeconds() * 1000;
            }
            if (x != null) {
                return ZoneOffset.of(x).getTotalSeconds() * 1000;
            }
        } catch (final Exception e) {
            // n/a
        }
        LOG.warn("    REVERTING to default time zone UTC = " + tz);
        return ZoneOffset.UTC.getTotalSeconds() * 1000;
    }

    /**
     * Returns the time zone.
     *
     * @param time the time
     * @param secondsOffset the seconds offset
     * @return the time zone
     */
    public static String getTimeZone(final long time, final int secondsOffset) {

        final ZoneId id = Instant.ofEpochMilli(time).atOffset(ZoneOffset.ofTotalSeconds(secondsOffset)).toZonedDateTime().getZone();
        return id.getId();
    }

    /**
     * Indicates whether or not valid date is the case.
     *
     * @param now the now
     * @param date the date
     * @return <code>true</code> if so, <code>false</code> otherwise
     */
    public static boolean isValidDate(final Date now, final Date date) {

        if (date == null) {
            return false;
        }
        // Not in the future
        final boolean future = date.after(now);
        // Not before Jan 1 1970
        final boolean past = date.before(new Date(0));
        // Not within 5 seconds before "now"
        // final boolean nowish = Math.abs(now.getTime() - date.getTime()) <
        // 5000;
        // Not "today"
        final boolean today = DateUtils.truncate(now, Calendar.DAY_OF_MONTH).equals(DateUtils.truncate(date, Calendar.DAY_OF_MONTH));
        return !future && !past && !today;
    }

    /**
     * Indicates whether or not valid date string (yyyy-MM-dd or yyyymmdd) is the case.
     *
     * @param dateString the date string
     * @return <code>true</code> if so, <code>false</code> otherwise
     */
    public static boolean isValidDateString(final String dateString) {
        
        if (StringUtils.isEmpty(dateString)) {
            return false;
        }
        try {
            if (dateString.contains("-")) {
                SimpleDateFormat df = new SimpleDateFormat(DateUtility.DATE_FORMAT_REVERSE);
                df.parse(dateString);   
            } else {
                SimpleDateFormat df = new SimpleDateFormat(DateUtility.DATE_FORMAT_REVERSE_ONLY_NUMBERS);
                df.parse(dateString);
            }
            return true;
        } catch (Exception e) {
            LOG.warn("Date string is not in the format of yyyy-MM-dd or yyyymmdd. Got {}", dateString);
            return false;
        }        
    }

    /**
     * Returns the date.
     *
     * @param yyyymmdd the yyyymmdd
     * @param timeZone the time zone
     * @return the date
     */
    public static ZonedDateTime getStartOfDay(final String yyyymmdd, final String timeZone) {

        final LocalDate localDate = LocalDate.parse(yyyymmdd, DAY);
        final ZonedDateTime startOfDay = localDate.atStartOfDay(ZoneId.of(timeZone));

        return startOfDay;
    }

    /**
     * Returns the end of day.
     *
     * @param yyyymmdd the yyyymmdd
     * @param timeZone the time zone
     * @return the end of day
     */
    public static ZonedDateTime getEndOfDay(final String yyyymmdd, final String timeZone) {

        final LocalDate localDate = LocalDate.parse(yyyymmdd, DAY);
        final ZonedDateTime endOfDay = localDate.atTime(LocalTime.MAX).atZone(ZoneId.of(timeZone));

        return endOfDay;
    }

    /**
     * Returns the date for a string that includes a time component.
     *
     * @param dateString the date string
     * @param pattern the pattern
     * @param timeZone the time zone
     * @return the date
     * @throws Exception the exception
     */
    public static Date getDate(final String dateString, final String pattern, final String timeZone) throws Exception {

        String newTimezone;
        final DateTimeFormatter formatter = new DateTimeFormatterBuilder().appendPattern(pattern).parseDefaulting(ChronoField.HOUR_OF_DAY, 0)
            .parseDefaulting(ChronoField.MINUTE_OF_HOUR, 0).parseDefaulting(ChronoField.SECOND_OF_MINUTE, 0).toFormatter();

        if (timeZone == null) {
            newTimezone = "-00:00";
        } else {
            newTimezone = new String(timeZone);
        }

        final LocalDateTime ldt = LocalDateTime.parse(dateString, formatter);
        final ZonedDateTime zdt = ZonedDateTime.of(ldt, ZoneId.of(newTimezone));
        return Date.from(zdt.toInstant());
    }

    /**
     * Returns the date when the date string has no time component.
     *
     * @param dateString the date string
     * @param pattern the pattern
     * @return the date
     * @throws Exception the exception
     */
    public static Date getDateWithNoTime(final String dateString, final String pattern) throws Exception {

        return getDate(dateString + " 00:00", pattern + " HH:mm", null);
    }
    
    /**
     * 
     * @param dateString the date string
     * @param pattern the pattern
     * @return the date
     * @throws Exception the exception
     */
    public static LocalDate getLocalDateNoTime(final String dateString, final String pattern) throws Exception {
        
        return LocalDate.parse(dateString);
    }
    
    /**
     * Returns the local date no time.
     *
     * @param date the date
     * @return the local date no time
     * @throws Exception the exception
     */
    public static LocalDate getLocalDateNoTimeUTC(final Date date) throws Exception {
        
        if (date == null) {
            return null;
        }
        
        return date.toInstant().atZone(ZoneOffset.UTC).toLocalDate();
    }

    /**
     * Returns a new date object at the specified timezone, or UTC.
     *
     * @param timeZone the time zone
     * @return the date object
     * @throws Exception the exception
     */
    public static OffsetDateTime getNewDate(final String timeZone) throws Exception {

        String newTimezone;

        if (timeZone == null) {
            newTimezone = "-00:00";
        } else {
            newTimezone = new String(timeZone);
        }

        final OffsetDateTime newDate = OffsetDateTime.now(ZoneId.of(newTimezone));
        return newDate;
    }

    /**
     * Returns a new date object at the specified timezone, or UTC.
     *
     * @param date the date
     * @param format the format
     * @param timeZone the time zone
     * @return the string
     * @throws Exception the exception
     */
    public static String formatDate(final Date date, final String format, final String timeZone) throws Exception {

        String newTimezone;

        if (timeZone == null) {
            newTimezone = "-00:00";
        } else {
            newTimezone = new String(timeZone);
        }

        final Instant instant = date.toInstant();
        final ZonedDateTime dateTime = instant.atOffset(ZoneOffset.of(newTimezone)).toZonedDateTime();
        return dateTime.format(DateTimeFormatter.ofPattern(format));
    }

}
