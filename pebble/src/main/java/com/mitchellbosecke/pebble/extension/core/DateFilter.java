/*
 * This file is part of Pebble.
 *
 * Copyright (c) 2014 by Mitchell Bösecke
 *
 * For the full copyright and license information, please view the LICENSE
 * file that was distributed with this source code.
 */
package com.mitchellbosecke.pebble.extension.core;

import com.mitchellbosecke.pebble.error.PebbleException;
import com.mitchellbosecke.pebble.extension.Filter;
import com.mitchellbosecke.pebble.extension.escaper.SafeString;
import com.mitchellbosecke.pebble.template.EvaluationContext;
import com.mitchellbosecke.pebble.template.PebbleTemplate;

import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.DateTimeException;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.Period;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.Temporal;
import java.time.temporal.TemporalAccessor;
import java.time.temporal.TemporalQueries;
import java.time.temporal.UnsupportedTemporalTypeException;
import java.util.*;

import static java.lang.String.format;

public class DateFilter implements Filter {

  private final List<String> argumentNames = new ArrayList<>();

  public DateFilter() {
    this.argumentNames.add("format");
    this.argumentNames.add("existingFormat");
    this.argumentNames.add("timeZone");
    this.argumentNames.add("alterTime");
  }

  @Override
  public List<String> getArgumentNames() {
    return this.argumentNames;
  }

  @Override
  public Object apply(Object input, Map<String, Object> args, PebbleTemplate self,
      EvaluationContext context, int lineNumber) throws PebbleException {
    if (input == null) {
      return null;
    }
    final Locale locale = context.getLocale();
    final String format = (String) args.get("format");
    final String timeZone = (String) args.get("timeZone");
    final String alterTime = (String) args.get("alterTime");

    Duration alterTimeDuration = null;
    if (alterTime != null) {alterTimeDuration = Duration.parse(alterTime);
    }

    if (Temporal.class.isAssignableFrom(input.getClass())) {
      return this.applyTemporal((Temporal) input, self, locale, lineNumber, format, timeZone, alterTimeDuration);
    }
    return this.applyDate(
            input, self, locale, lineNumber,
            format, (String) args.get("existingFormat"), timeZone, alterTimeDuration);
  }

  private Object applyDate(Object dateOrString, final PebbleTemplate self, final Locale locale,
      int lineNumber, final String format, final String existingFormatString, final String timeZone,
      Duration alterTimeDuration
  ) throws PebbleException {
    Date date;
    DateFormat existingFormat;
    DateFormat intendedFormat;
    if (existingFormatString != null) {
      existingFormat = new SimpleDateFormat(existingFormatString, locale);
      try {
        date = existingFormat.parse(dateOrString.toString());
      } catch (ParseException e) {
        throw new PebbleException(e, String.format("Could not parse the string '%s' into a date.",
            dateOrString.toString()), lineNumber, self.getName());
      }
    } else {
      if (dateOrString instanceof Date) {
        date = (Date) dateOrString;
      } else if (dateOrString instanceof Number) {
        date = new Date(((Number) dateOrString).longValue());
      } else {
        throw new IllegalArgumentException(
            format("Unsupported argument type: %s (value: %s)", dateOrString.getClass().getName(),
                dateOrString));
      }
    }
    intendedFormat = new SimpleDateFormat(format == null ? "yyyy-MM-dd'T'HH:mm:ssZ" : format, locale);
    if (timeZone != null) {
      intendedFormat.setTimeZone(TimeZone.getTimeZone(timeZone));
    }

    if (alterTimeDuration != null) {
      date = Date.from(date.toInstant().plus(alterTimeDuration));
    }

    return new SafeString(intendedFormat.format(date));
  }

  private Object applyTemporal(Temporal input, PebbleTemplate self,
      final Locale locale,
      int lineNumber, final String format, final String timeZone,
      Duration alterTimeDuration) throws PebbleException {
    DateTimeFormatter formatter = format != null
        ? DateTimeFormatter.ofPattern(format, locale)
        : DateTimeFormatter.ISO_DATE_TIME;

    ZoneId zoneId = getZoneId(input, timeZone);
    formatter = formatter.withZone(zoneId);

    if (alterTimeDuration != null) {
      try {
        input = input.plus(alterTimeDuration);
      }
      catch (UnsupportedTemporalTypeException e){
        // this will ignore everything below 1 day
        Period alterTimePeriod = Period.ofDays((int) alterTimeDuration.toDays());
        input = input.plus(alterTimePeriod);
      }
    }

    try {
      return new SafeString(formatter.format(input));
    } catch (DateTimeException dte) {
      throw new PebbleException(
              dte,
              String.format("Could not format instance '%s' of type %s into a date.", input.toString(), input.getClass()),
              lineNumber,
              self.getName());
    }
  }

  private ZoneId getZoneId(Temporal input, String timeZone) {
    // First try the time zone of the input.
    ZoneId zoneId = input.query(TemporalQueries.zone());
    if (zoneId == null && timeZone != null) {
      // Fallback to time zone provided as filter argument.
      zoneId = ZoneId.of(timeZone);
    }
    if (zoneId == null) {
      // Fallback to system time zone.
      zoneId = ZoneId.systemDefault();
    }
    return zoneId;
  }

}
