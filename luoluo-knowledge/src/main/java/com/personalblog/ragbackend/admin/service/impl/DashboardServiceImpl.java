package com.personalblog.ragbackend.admin.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.personalblog.ragbackend.admin.controller.vo.DashboardOverviewGroupVO;
import com.personalblog.ragbackend.admin.controller.vo.DashboardOverviewKpiVO;
import com.personalblog.ragbackend.admin.controller.vo.DashboardOverviewVO;
import com.personalblog.ragbackend.admin.controller.vo.DashboardPerformanceVO;
import com.personalblog.ragbackend.admin.controller.vo.DashboardTrendPointVO;
import com.personalblog.ragbackend.admin.controller.vo.DashboardTrendSeriesVO;
import com.personalblog.ragbackend.admin.controller.vo.DashboardTrendsVO;
import com.personalblog.ragbackend.admin.service.DashboardService;
import com.personalblog.ragbackend.rag.dao.entity.RagConversationEntity;
import com.personalblog.ragbackend.rag.dao.entity.RagConversationMessageEntity;
import com.personalblog.ragbackend.rag.dao.entity.RagTraceRunEntity;
import com.personalblog.ragbackend.rag.dao.mapper.RagConversationMapper;
import com.personalblog.ragbackend.rag.dao.mapper.RagConversationMessageMapper;
import com.personalblog.ragbackend.rag.dao.mapper.RagTraceRunMapper;
import com.personalblog.ragbackend.member.domain.MemberUser;
import com.personalblog.ragbackend.member.mapper.MemberUserMapper;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class DashboardServiceImpl implements DashboardService {
    private static final String STATUS_SUCCESS = "SUCCESS";
    private static final String STATUS_ERROR = "ERROR";
    private static final String ROLE_ASSISTANT = "assistant";
    private static final String NO_DOC_REPLY = "未检索到与问题相关的文档内容。";
    private static final String GRANULARITY_DAY = "day";
    private static final String GRANULARITY_HOUR = "hour";
    private static final long SLOW_LATENCY_THRESHOLD_MS = 20_000L;
    private static final DateTimeFormatter HOUR_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:00:00");

    private final MemberUserMapper memberUserMapper;
    private final RagConversationMapper conversationMapper;
    private final RagConversationMessageMapper messageMapper;
    private final RagTraceRunMapper traceRunMapper;

    public DashboardServiceImpl(MemberUserMapper memberUserMapper,
                                RagConversationMapper conversationMapper,
                                RagConversationMessageMapper messageMapper,
                                RagTraceRunMapper traceRunMapper) {
        this.memberUserMapper = memberUserMapper;
        this.conversationMapper = conversationMapper;
        this.messageMapper = messageMapper;
        this.traceRunMapper = traceRunMapper;
    }

    @Override
    public DashboardOverviewVO loadOverview(String window) {
        WindowRange range = resolveWindowRange(window, Duration.ofHours(24));

        long totalUsers = memberUserMapper.selectCount(Wrappers.lambdaQuery(MemberUser.class).eq(MemberUser::getDeleted, 0));
        long usersInWindow = countUsers(range.start, range.end);
        long totalSessions = conversationMapper.selectCount(Wrappers.lambdaQuery(RagConversationEntity.class).eq(RagConversationEntity::getDeleted, 0));
        long sessionsInWindow = countConversations(range.start, range.end);
        long sessionsPrevWindow = countConversations(range.prevStart, range.prevEnd);
        long totalMessages = messageMapper.selectCount(Wrappers.lambdaQuery(RagConversationMessageEntity.class).eq(RagConversationMessageEntity::getDeleted, 0));
        long messagesInWindow = countMessages(range.start, range.end);
        long messagesPrevWindow = countMessages(range.prevStart, range.prevEnd);
        long activeUsers = countActiveUsers(range.start, range.end);
        long activeUsersPrev = countActiveUsers(range.prevStart, range.prevEnd);

        DashboardOverviewGroupVO kpis = new DashboardOverviewGroupVO(
                buildKpi(totalUsers, usersInWindow, null),
                buildKpi(activeUsers, activeUsers - activeUsersPrev, calcPct(activeUsers, activeUsersPrev)),
                buildKpi(totalSessions, sessionsInWindow, null),
                buildKpi(sessionsInWindow, sessionsInWindow - sessionsPrevWindow, calcPct(sessionsInWindow, sessionsPrevWindow)),
                buildKpi(totalMessages, messagesInWindow, null),
                buildKpi(messagesInWindow, messagesInWindow - messagesPrevWindow, calcPct(messagesInWindow, messagesPrevWindow))
        );
        return new DashboardOverviewVO(range.windowLabel, range.compareLabel, System.currentTimeMillis(), kpis);
    }

    @Override
    public DashboardPerformanceVO loadPerformance(String window) {
        WindowRange range = resolveWindowRange(window, Duration.ofHours(24));
        List<Long> durations = listDurations(range.start, range.end);
        long avgLatency = average(durations);
        long p95Latency = percentile(durations);
        long success = countTraceRuns(range.start, range.end, STATUS_SUCCESS);
        long error = countTraceRuns(range.start, range.end, STATUS_ERROR);
        long total = success + error;
        long assistantCount = countAssistantMessages(range.start, range.end);
        long noDocCount = countNoDocMessages(range.start, range.end);
        long slowCount = durations.stream().filter(each -> each > SLOW_LATENCY_THRESHOLD_MS).count();

        return new DashboardPerformanceVO(
                range.windowLabel,
                avgLatency,
                p95Latency,
                total == 0 ? 0.0 : round1(success * 100.0 / total),
                total == 0 ? 0.0 : round1(error * 100.0 / total),
                assistantCount == 0 ? 0.0 : round1(noDocCount * 100.0 / assistantCount),
                durations.isEmpty() ? 0.0 : round1(slowCount * 100.0 / durations.size())
        );
    }

    @Override
    public DashboardTrendsVO loadTrends(String metric, String window, String granularity) {
        String normalizedMetric = metric == null ? "" : metric.trim().toLowerCase(Locale.ROOT);
        Duration windowDuration = parseWindow(window, Duration.ofDays(7));
        WindowRange range = resolveWindowRange(window, Duration.ofDays(7));
        String resolvedGranularity = resolveTrendGranularity(granularity, windowDuration);
        ZoneId zoneId = ZoneId.systemDefault();
        List<DashboardTrendSeriesVO> series = new ArrayList<>();

        if (GRANULARITY_HOUR.equals(resolvedGranularity)) {
            LocalDateTime endHourExclusive = range.end.truncatedTo(ChronoUnit.HOURS).plusHours(1);
            LocalDateTime startHour = endHourExclusive.minusHours(Math.max(1, windowDuration.toHours()));
            buildHourlySeries(normalizedMetric, startHour, endHourExclusive, zoneId, series);
        } else {
            LocalDate startDay = range.start.toLocalDate();
            LocalDate endExclusiveDay = range.end.toLocalDate().plusDays(1);
            buildDailySeries(normalizedMetric, startDay, endExclusiveDay, zoneId, series);
        }

        return new DashboardTrendsVO(metric, range.windowLabel, resolvedGranularity, series);
    }

    private void buildDailySeries(String metric,
                                  LocalDate startDay,
                                  LocalDate endExclusiveDay,
                                  ZoneId zoneId,
                                  List<DashboardTrendSeriesVO> series) {
        if ("sessions".equals(metric)) {
            series.add(new DashboardTrendSeriesVO("sessions", buildPoints(startDay, endExclusiveDay, zoneId, countConversationsByDay(startDay, endExclusiveDay))));
            return;
        }
        if ("messages".equals(metric)) {
            series.add(new DashboardTrendSeriesVO("messages", buildPoints(startDay, endExclusiveDay, zoneId, countMessagesByDay(startDay, endExclusiveDay))));
            return;
        }
        if ("activeusers".equals(metric)) {
            series.add(new DashboardTrendSeriesVO("activeUsers", buildPoints(startDay, endExclusiveDay, zoneId, countActiveUsersByDay(startDay, endExclusiveDay))));
            return;
        }
        if ("avglatency".equals(metric)) {
            series.add(new DashboardTrendSeriesVO("avgLatency", buildPointsDouble(startDay, endExclusiveDay, zoneId, averageLatencyByDay(startDay, endExclusiveDay))));
            return;
        }
        if ("quality".equals(metric)) {
            Map<LocalDate, Long> successMap = countTraceRunsByDay(startDay, endExclusiveDay, STATUS_SUCCESS);
            Map<LocalDate, Long> errorMap = countTraceRunsByDay(startDay, endExclusiveDay, STATUS_ERROR);
            Map<LocalDate, Long> assistantMap = countAssistantMessagesByDay(startDay, endExclusiveDay);
            Map<LocalDate, Long> noDocMap = countNoDocMessagesByDay(startDay, endExclusiveDay);
            Map<LocalDate, Double> errorRate = new HashMap<>();
            Map<LocalDate, Double> noDocRate = new HashMap<>();
            for (LocalDate day = startDay; day.isBefore(endExclusiveDay); day = day.plusDays(1)) {
                long total = successMap.getOrDefault(day, 0L) + errorMap.getOrDefault(day, 0L);
                long assistant = assistantMap.getOrDefault(day, 0L);
                errorRate.put(day, total == 0 ? 0.0 : round1(errorMap.getOrDefault(day, 0L) * 100.0 / total));
                noDocRate.put(day, assistant == 0 ? 0.0 : round1(noDocMap.getOrDefault(day, 0L) * 100.0 / assistant));
            }
            series.add(new DashboardTrendSeriesVO("errorRate", buildPointsDouble(startDay, endExclusiveDay, zoneId, errorRate)));
            series.add(new DashboardTrendSeriesVO("noDocRate", buildPointsDouble(startDay, endExclusiveDay, zoneId, noDocRate)));
        }
    }

    private void buildHourlySeries(String metric,
                                   LocalDateTime startHour,
                                   LocalDateTime endHourExclusive,
                                   ZoneId zoneId,
                                   List<DashboardTrendSeriesVO> series) {
        if ("sessions".equals(metric)) {
            series.add(new DashboardTrendSeriesVO("sessions", buildPointsByHour(startHour, endHourExclusive, zoneId, countConversationsByHour(startHour, endHourExclusive))));
            return;
        }
        if ("messages".equals(metric)) {
            series.add(new DashboardTrendSeriesVO("messages", buildPointsByHour(startHour, endHourExclusive, zoneId, countMessagesByHour(startHour, endHourExclusive))));
            return;
        }
        if ("activeusers".equals(metric)) {
            series.add(new DashboardTrendSeriesVO("activeUsers", buildPointsByHour(startHour, endHourExclusive, zoneId, countActiveUsersByHour(startHour, endHourExclusive))));
            return;
        }
        if ("avglatency".equals(metric)) {
            series.add(new DashboardTrendSeriesVO("avgLatency", buildPointsDoubleByHour(startHour, endHourExclusive, zoneId, averageLatencyByHour(startHour, endHourExclusive))));
            return;
        }
        if ("quality".equals(metric)) {
            Map<LocalDateTime, Long> successMap = countTraceRunsByHour(startHour, endHourExclusive, STATUS_SUCCESS);
            Map<LocalDateTime, Long> errorMap = countTraceRunsByHour(startHour, endHourExclusive, STATUS_ERROR);
            Map<LocalDateTime, Long> assistantMap = countAssistantMessagesByHour(startHour, endHourExclusive);
            Map<LocalDateTime, Long> noDocMap = countNoDocMessagesByHour(startHour, endHourExclusive);
            Map<LocalDateTime, Double> errorRate = new HashMap<>();
            Map<LocalDateTime, Double> noDocRate = new HashMap<>();
            for (LocalDateTime hour = startHour; hour.isBefore(endHourExclusive); hour = hour.plusHours(1)) {
                long total = successMap.getOrDefault(hour, 0L) + errorMap.getOrDefault(hour, 0L);
                long assistant = assistantMap.getOrDefault(hour, 0L);
                errorRate.put(hour, total == 0 ? 0.0 : round1(errorMap.getOrDefault(hour, 0L) * 100.0 / total));
                noDocRate.put(hour, assistant == 0 ? 0.0 : round1(noDocMap.getOrDefault(hour, 0L) * 100.0 / assistant));
            }
            series.add(new DashboardTrendSeriesVO("errorRate", buildPointsDoubleByHour(startHour, endHourExclusive, zoneId, errorRate)));
            series.add(new DashboardTrendSeriesVO("noDocRate", buildPointsDoubleByHour(startHour, endHourExclusive, zoneId, noDocRate)));
        }
    }

    private long countUsers(LocalDateTime start, LocalDateTime end) {
        return memberUserMapper.selectCount(Wrappers.lambdaQuery(MemberUser.class)
                .eq(MemberUser::getDeleted, 0)
                .ge(MemberUser::getCreatedAt, start)
                .lt(MemberUser::getCreatedAt, end));
    }

    private long countConversations(LocalDateTime start, LocalDateTime end) {
        return conversationMapper.selectCount(Wrappers.lambdaQuery(RagConversationEntity.class)
                .eq(RagConversationEntity::getDeleted, 0)
                .ge(RagConversationEntity::getCreatedAt, start)
                .lt(RagConversationEntity::getCreatedAt, end));
    }

    private long countMessages(LocalDateTime start, LocalDateTime end) {
        return messageMapper.selectCount(Wrappers.lambdaQuery(RagConversationMessageEntity.class)
                .eq(RagConversationMessageEntity::getDeleted, 0)
                .ge(RagConversationMessageEntity::getCreatedAt, start)
                .lt(RagConversationMessageEntity::getCreatedAt, end));
    }

    private long countActiveUsers(LocalDateTime start, LocalDateTime end) {
        QueryWrapper<RagConversationMessageEntity> wrapper = new QueryWrapper<>();
        wrapper.select("count(distinct user_id) as cnt")
                .eq("deleted", 0)
                .ge("create_time", start)
                .lt("create_time", end);
        return extractCount(messageMapper.selectMaps(wrapper));
    }

    private long countTraceRuns(LocalDateTime start, LocalDateTime end, String status) {
        QueryWrapper<RagTraceRunEntity> wrapper = new QueryWrapper<>();
        wrapper.eq("deleted", 0).ge("start_time", start).lt("start_time", end);
        if (status != null) {
            wrapper.eq("status", status);
        }
        return traceRunMapper.selectCount(wrapper);
    }

    private long countAssistantMessages(LocalDateTime start, LocalDateTime end) {
        QueryWrapper<RagConversationMessageEntity> wrapper = new QueryWrapper<>();
        wrapper.eq("deleted", 0).ge("create_time", start).lt("create_time", end).eq("role", ROLE_ASSISTANT);
        return messageMapper.selectCount(wrapper);
    }

    private long countNoDocMessages(LocalDateTime start, LocalDateTime end) {
        QueryWrapper<RagConversationMessageEntity> wrapper = new QueryWrapper<>();
        wrapper.eq("deleted", 0).ge("create_time", start).lt("create_time", end).eq("role", ROLE_ASSISTANT).eq("content", NO_DOC_REPLY);
        return messageMapper.selectCount(wrapper);
    }

    private List<Long> listDurations(LocalDateTime start, LocalDateTime end) {
        QueryWrapper<RagTraceRunEntity> wrapper = new QueryWrapper<>();
        wrapper.select("duration_ms").eq("deleted", 0).eq("status", STATUS_SUCCESS).ge("start_time", start).lt("start_time", end);
        List<Object> objs = traceRunMapper.selectObjs(wrapper);
        List<Long> durations = new ArrayList<>();
        if (objs == null) {
            return durations;
        }
        for (Object obj : objs) {
            if (obj instanceof Number number && number.longValue() > 0) {
                durations.add(number.longValue());
            }
        }
        return durations;
    }

    private Map<LocalDate, Long> countConversationsByDay(LocalDate start, LocalDate endExclusive) {
        QueryWrapper<RagConversationEntity> wrapper = new QueryWrapper<>();
        wrapper.select("to_char(create_time,'YYYY-MM-DD') as d", "count(*) as cnt")
                .eq("deleted", 0)
                .ge("create_time", start.atStartOfDay())
                .lt("create_time", endExclusive.atStartOfDay())
                .groupBy("d");
        return mapLongResults(conversationMapper.selectMaps(wrapper), "d");
    }

    private Map<LocalDate, Long> countMessagesByDay(LocalDate start, LocalDate endExclusive) {
        QueryWrapper<RagConversationMessageEntity> wrapper = new QueryWrapper<>();
        wrapper.select("to_char(create_time,'YYYY-MM-DD') as d", "count(*) as cnt")
                .eq("deleted", 0)
                .ge("create_time", start.atStartOfDay())
                .lt("create_time", endExclusive.atStartOfDay())
                .groupBy("d");
        return mapLongResults(messageMapper.selectMaps(wrapper), "d");
    }

    private Map<LocalDate, Long> countActiveUsersByDay(LocalDate start, LocalDate endExclusive) {
        QueryWrapper<RagConversationMessageEntity> wrapper = new QueryWrapper<>();
        wrapper.select("to_char(create_time,'YYYY-MM-DD') as d", "count(distinct user_id) as cnt")
                .eq("deleted", 0)
                .ge("create_time", start.atStartOfDay())
                .lt("create_time", endExclusive.atStartOfDay())
                .groupBy("d");
        return mapLongResults(messageMapper.selectMaps(wrapper), "d");
    }

    private Map<LocalDate, Long> countAssistantMessagesByDay(LocalDate start, LocalDate endExclusive) {
        QueryWrapper<RagConversationMessageEntity> wrapper = new QueryWrapper<>();
        wrapper.select("to_char(create_time,'YYYY-MM-DD') as d", "count(*) as cnt")
                .eq("deleted", 0)
                .eq("role", ROLE_ASSISTANT)
                .ge("create_time", start.atStartOfDay())
                .lt("create_time", endExclusive.atStartOfDay())
                .groupBy("d");
        return mapLongResults(messageMapper.selectMaps(wrapper), "d");
    }

    private Map<LocalDate, Long> countNoDocMessagesByDay(LocalDate start, LocalDate endExclusive) {
        QueryWrapper<RagConversationMessageEntity> wrapper = new QueryWrapper<>();
        wrapper.select("to_char(create_time,'YYYY-MM-DD') as d", "count(*) as cnt")
                .eq("deleted", 0)
                .eq("role", ROLE_ASSISTANT)
                .eq("content", NO_DOC_REPLY)
                .ge("create_time", start.atStartOfDay())
                .lt("create_time", endExclusive.atStartOfDay())
                .groupBy("d");
        return mapLongResults(messageMapper.selectMaps(wrapper), "d");
    }

    private Map<LocalDate, Long> countTraceRunsByDay(LocalDate start, LocalDate endExclusive, String status) {
        QueryWrapper<RagTraceRunEntity> wrapper = new QueryWrapper<>();
        wrapper.select("to_char(start_time,'YYYY-MM-DD') as d", "count(*) as cnt")
                .eq("deleted", 0)
                .ge("start_time", start.atStartOfDay())
                .lt("start_time", endExclusive.atStartOfDay());
        if (status != null) {
            wrapper.eq("status", status);
        }
        wrapper.groupBy("d");
        return mapLongResults(traceRunMapper.selectMaps(wrapper), "d");
    }

    private Map<LocalDate, Double> averageLatencyByDay(LocalDate start, LocalDate endExclusive) {
        QueryWrapper<RagTraceRunEntity> wrapper = new QueryWrapper<>();
        wrapper.select("to_char(start_time,'YYYY-MM-DD') as d", "avg(duration_ms) as avg")
                .eq("deleted", 0)
                .eq("status", STATUS_SUCCESS)
                .ge("start_time", start.atStartOfDay())
                .lt("start_time", endExclusive.atStartOfDay())
                .groupBy("d");
        return mapDoubleResults(traceRunMapper.selectMaps(wrapper), "d");
    }

    private Map<LocalDateTime, Long> countConversationsByHour(LocalDateTime start, LocalDateTime endExclusive) {
        QueryWrapper<RagConversationEntity> wrapper = new QueryWrapper<>();
        wrapper.select("to_char(create_time,'YYYY-MM-DD HH24:00:00') as h", "count(*) as cnt")
                .eq("deleted", 0)
                .ge("create_time", start)
                .lt("create_time", endExclusive)
                .groupBy("h");
        return mapLongHourResults(conversationMapper.selectMaps(wrapper), "h");
    }

    private Map<LocalDateTime, Long> countMessagesByHour(LocalDateTime start, LocalDateTime endExclusive) {
        QueryWrapper<RagConversationMessageEntity> wrapper = new QueryWrapper<>();
        wrapper.select("to_char(create_time,'YYYY-MM-DD HH24:00:00') as h", "count(*) as cnt")
                .eq("deleted", 0)
                .ge("create_time", start)
                .lt("create_time", endExclusive)
                .groupBy("h");
        return mapLongHourResults(messageMapper.selectMaps(wrapper), "h");
    }

    private Map<LocalDateTime, Long> countActiveUsersByHour(LocalDateTime start, LocalDateTime endExclusive) {
        QueryWrapper<RagConversationMessageEntity> wrapper = new QueryWrapper<>();
        wrapper.select("to_char(create_time,'YYYY-MM-DD HH24:00:00') as h", "count(distinct user_id) as cnt")
                .eq("deleted", 0)
                .ge("create_time", start)
                .lt("create_time", endExclusive)
                .groupBy("h");
        return mapLongHourResults(messageMapper.selectMaps(wrapper), "h");
    }

    private Map<LocalDateTime, Long> countAssistantMessagesByHour(LocalDateTime start, LocalDateTime endExclusive) {
        QueryWrapper<RagConversationMessageEntity> wrapper = new QueryWrapper<>();
        wrapper.select("to_char(create_time,'YYYY-MM-DD HH24:00:00') as h", "count(*) as cnt")
                .eq("deleted", 0)
                .eq("role", ROLE_ASSISTANT)
                .ge("create_time", start)
                .lt("create_time", endExclusive)
                .groupBy("h");
        return mapLongHourResults(messageMapper.selectMaps(wrapper), "h");
    }

    private Map<LocalDateTime, Long> countNoDocMessagesByHour(LocalDateTime start, LocalDateTime endExclusive) {
        QueryWrapper<RagConversationMessageEntity> wrapper = new QueryWrapper<>();
        wrapper.select("to_char(create_time,'YYYY-MM-DD HH24:00:00') as h", "count(*) as cnt")
                .eq("deleted", 0)
                .eq("role", ROLE_ASSISTANT)
                .eq("content", NO_DOC_REPLY)
                .ge("create_time", start)
                .lt("create_time", endExclusive)
                .groupBy("h");
        return mapLongHourResults(messageMapper.selectMaps(wrapper), "h");
    }

    private Map<LocalDateTime, Long> countTraceRunsByHour(LocalDateTime start, LocalDateTime endExclusive, String status) {
        QueryWrapper<RagTraceRunEntity> wrapper = new QueryWrapper<>();
        wrapper.select("to_char(start_time,'YYYY-MM-DD HH24:00:00') as h", "count(*) as cnt")
                .eq("deleted", 0)
                .ge("start_time", start)
                .lt("start_time", endExclusive);
        if (status != null) {
            wrapper.eq("status", status);
        }
        wrapper.groupBy("h");
        return mapLongHourResults(traceRunMapper.selectMaps(wrapper), "h");
    }

    private Map<LocalDateTime, Double> averageLatencyByHour(LocalDateTime start, LocalDateTime endExclusive) {
        QueryWrapper<RagTraceRunEntity> wrapper = new QueryWrapper<>();
        wrapper.select("to_char(start_time,'YYYY-MM-DD HH24:00:00') as h", "avg(duration_ms) as avg")
                .eq("deleted", 0)
                .eq("status", STATUS_SUCCESS)
                .ge("start_time", start)
                .lt("start_time", endExclusive)
                .groupBy("h");
        return mapDoubleHourResults(traceRunMapper.selectMaps(wrapper), "h");
    }

    private DashboardOverviewKpiVO buildKpi(long value, long delta, Double deltaPct) {
        return new DashboardOverviewKpiVO(value, delta, deltaPct);
    }

    private Double calcPct(long current, long previous) {
        if (previous <= 0) {
            return null;
        }
        return round1((current - previous) * 100.0 / previous);
    }

    private long extractCount(List<Map<String, Object>> rows) {
        if (rows == null || rows.isEmpty()) {
            return 0L;
        }
        Object value = rows.get(0).get("cnt");
        return value instanceof Number number ? number.longValue() : 0L;
    }

    private Map<LocalDate, Long> mapLongResults(List<Map<String, Object>> rows, String key) {
        Map<LocalDate, Long> result = new HashMap<>();
        if (rows == null) {
            return result;
        }
        for (Map<String, Object> row : rows) {
            LocalDate date = parseLocalDate(row.get(key));
            if (date == null) {
                continue;
            }
            Object value = row.get("cnt");
            result.put(date, value instanceof Number number ? number.longValue() : 0L);
        }
        return result;
    }

    private Map<LocalDate, Double> mapDoubleResults(List<Map<String, Object>> rows, String key) {
        Map<LocalDate, Double> result = new HashMap<>();
        if (rows == null) {
            return result;
        }
        for (Map<String, Object> row : rows) {
            LocalDate date = parseLocalDate(row.get(key));
            if (date == null) {
                continue;
            }
            Object value = row.get("avg");
            result.put(date, value instanceof Number number ? round1(number.doubleValue()) : 0.0);
        }
        return result;
    }

    private Map<LocalDateTime, Long> mapLongHourResults(List<Map<String, Object>> rows, String key) {
        Map<LocalDateTime, Long> result = new HashMap<>();
        if (rows == null) {
            return result;
        }
        for (Map<String, Object> row : rows) {
            LocalDateTime dateTime = parseLocalDateTime(row.get(key));
            if (dateTime == null) {
                continue;
            }
            Object value = row.get("cnt");
            result.put(dateTime, value instanceof Number number ? number.longValue() : 0L);
        }
        return result;
    }

    private Map<LocalDateTime, Double> mapDoubleHourResults(List<Map<String, Object>> rows, String key) {
        Map<LocalDateTime, Double> result = new HashMap<>();
        if (rows == null) {
            return result;
        }
        for (Map<String, Object> row : rows) {
            LocalDateTime dateTime = parseLocalDateTime(row.get(key));
            if (dateTime == null) {
                continue;
            }
            Object value = row.get("avg");
            result.put(dateTime, value instanceof Number number ? round1(number.doubleValue()) : 0.0);
        }
        return result;
    }

    private List<DashboardTrendPointVO> buildPoints(LocalDate start, LocalDate endExclusive, ZoneId zoneId, Map<LocalDate, Long> values) {
        List<DashboardTrendPointVO> points = new ArrayList<>();
        for (LocalDate cursor = start; cursor.isBefore(endExclusive); cursor = cursor.plusDays(1)) {
            points.add(new DashboardTrendPointVO(cursor.atStartOfDay(zoneId).toInstant().toEpochMilli(), (double) values.getOrDefault(cursor, 0L)));
        }
        return points;
    }

    private List<DashboardTrendPointVO> buildPointsDouble(LocalDate start, LocalDate endExclusive, ZoneId zoneId, Map<LocalDate, Double> values) {
        List<DashboardTrendPointVO> points = new ArrayList<>();
        for (LocalDate cursor = start; cursor.isBefore(endExclusive); cursor = cursor.plusDays(1)) {
            points.add(new DashboardTrendPointVO(cursor.atStartOfDay(zoneId).toInstant().toEpochMilli(), values.getOrDefault(cursor, 0.0)));
        }
        return points;
    }

    private List<DashboardTrendPointVO> buildPointsByHour(LocalDateTime start, LocalDateTime endExclusive, ZoneId zoneId, Map<LocalDateTime, Long> values) {
        List<DashboardTrendPointVO> points = new ArrayList<>();
        for (LocalDateTime cursor = start; cursor.isBefore(endExclusive); cursor = cursor.plusHours(1)) {
            points.add(new DashboardTrendPointVO(cursor.atZone(zoneId).toInstant().toEpochMilli(), (double) values.getOrDefault(cursor, 0L)));
        }
        return points;
    }

    private List<DashboardTrendPointVO> buildPointsDoubleByHour(LocalDateTime start, LocalDateTime endExclusive, ZoneId zoneId, Map<LocalDateTime, Double> values) {
        List<DashboardTrendPointVO> points = new ArrayList<>();
        for (LocalDateTime cursor = start; cursor.isBefore(endExclusive); cursor = cursor.plusHours(1)) {
            points.add(new DashboardTrendPointVO(cursor.atZone(zoneId).toInstant().toEpochMilli(), values.getOrDefault(cursor, 0.0)));
        }
        return points;
    }

    private long average(List<Long> values) {
        if (values == null || values.isEmpty()) {
            return 0L;
        }
        return Math.round(values.stream().mapToLong(Long::longValue).average().orElse(0));
    }

    private long percentile(List<Long> values) {
        if (values == null || values.isEmpty()) {
            return 0L;
        }
        List<Long> sorted = new ArrayList<>(values);
        sorted.sort(Long::compareTo);
        int index = (int) Math.ceil(sorted.size() * 0.95) - 1;
        index = Math.max(0, Math.min(index, sorted.size() - 1));
        return sorted.get(index);
    }

    private double round1(double value) {
        return Math.round(value * 10.0) / 10.0;
    }

    private LocalDate parseLocalDate(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return LocalDate.parse(String.valueOf(value));
        } catch (Exception ignored) {
            return null;
        }
    }

    private LocalDateTime parseLocalDateTime(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return LocalDateTime.parse(String.valueOf(value), HOUR_FORMATTER);
        } catch (Exception ignored) {
            return null;
        }
    }

    private String resolveTrendGranularity(String granularity, Duration window) {
        if (granularity != null && !granularity.isBlank()) {
            return granularity.trim().toLowerCase(Locale.ROOT);
        }
        return window.toHours() <= 48 ? GRANULARITY_HOUR : GRANULARITY_DAY;
    }

    private Duration parseWindow(String window, Duration defaultWindow) {
        if (window == null || window.isBlank()) {
            return defaultWindow;
        }
        String normalized = window.trim().toLowerCase(Locale.ROOT);
        if (normalized.endsWith("h")) {
            return Duration.ofHours(Long.parseLong(normalized.substring(0, normalized.length() - 1)));
        }
        if (normalized.endsWith("d")) {
            return Duration.ofDays(Long.parseLong(normalized.substring(0, normalized.length() - 1)));
        }
        return defaultWindow;
    }

    private WindowRange resolveWindowRange(String window, Duration defaultWindow) {
        Duration duration = parseWindow(window, defaultWindow);
        LocalDateTime end = LocalDateTime.now();
        LocalDateTime start = end.minus(duration);
        return new WindowRange(
                start,
                end,
                start.minus(duration),
                start,
                window == null || window.isBlank() ? formatWindow(defaultWindow) : window,
                "prev_" + (window == null || window.isBlank() ? formatWindow(defaultWindow) : window)
        );
    }

    private String formatWindow(Duration duration) {
        long hours = duration.toHours();
        if (hours % 24 == 0) {
            return (hours / 24) + "d";
        }
        return hours + "h";
    }

    private record WindowRange(LocalDateTime start,
                               LocalDateTime end,
                               LocalDateTime prevStart,
                               LocalDateTime prevEnd,
                               String windowLabel,
                               String compareLabel) {
    }
}


