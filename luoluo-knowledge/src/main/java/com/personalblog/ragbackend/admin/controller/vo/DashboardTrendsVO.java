package com.personalblog.ragbackend.admin.controller.vo;

import java.util.List;

public record DashboardTrendsVO(String metric,
                                String window,
                                String granularity,
                                List<DashboardTrendSeriesVO> series) {
}
