package com.personalblog.ragbackend.knowledge.schedule;

import org.springframework.scheduling.support.CronExpression;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;

public final class CronScheduleHelper {
    private CronScheduleHelper() {
    }

    public static Date nextRunTime(String cron, Date from) {
        if (!StringUtils.hasText(cron) || from == null) {
            return null;
        }
        CronExpression expression = CronExpression.parse(cron.trim());
        LocalDateTime next = expression.next(LocalDateTime.ofInstant(from.toInstant(), ZoneId.systemDefault()));
        return next == null ? null : Date.from(next.atZone(ZoneId.systemDefault()).toInstant());
    }

    public static boolean isIntervalLessThan(String cron, Date from, long minSeconds) {
        if (!StringUtils.hasText(cron) || from == null) {
            return true;
        }
        CronExpression expression = CronExpression.parse(cron.trim());
        LocalDateTime first = expression.next(LocalDateTime.ofInstant(from.toInstant(), ZoneId.systemDefault()));
        if (first == null) {
            return true;
        }
        LocalDateTime second = expression.next(first);
        if (second == null) {
            return true;
        }
        return Duration.between(first, second).getSeconds() < minSeconds;
    }
}
