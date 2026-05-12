package com.personalblog.ragbackend.admin.controller.vo;

public record DashboardOverviewGroupVO(DashboardOverviewKpiVO totalUsers,
                                       DashboardOverviewKpiVO activeUsers,
                                       DashboardOverviewKpiVO totalSessions,
                                       DashboardOverviewKpiVO sessions24h,
                                       DashboardOverviewKpiVO totalMessages,
                                       DashboardOverviewKpiVO messages24h) {
}
