package com.personalblog.ragbackend.admin.service;

import com.personalblog.ragbackend.admin.controller.vo.DashboardOverviewVO;
import com.personalblog.ragbackend.admin.controller.vo.DashboardPerformanceVO;
import com.personalblog.ragbackend.admin.controller.vo.DashboardTrendsVO;

public interface DashboardService {
    DashboardOverviewVO loadOverview(String window);

    DashboardPerformanceVO loadPerformance(String window);

    DashboardTrendsVO loadTrends(String metric, String window, String granularity);
}
