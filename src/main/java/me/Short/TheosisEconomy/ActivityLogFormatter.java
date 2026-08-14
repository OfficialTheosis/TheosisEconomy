package me.Short.TheosisEconomy;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.logging.Formatter;
import java.util.logging.LogRecord;

public class ActivityLogFormatter extends Formatter
{

    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault());

    @Override
    public String format(LogRecord record)
    {
        return "[" + DATE_TIME_FORMATTER.format(record.getInstant()) + " " + record.getLevel() + "] " + formatMessage(record) + System.lineSeparator();
    }

}