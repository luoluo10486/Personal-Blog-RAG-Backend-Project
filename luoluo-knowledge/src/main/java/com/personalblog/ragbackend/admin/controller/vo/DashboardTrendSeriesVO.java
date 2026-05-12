package com.personalblog.ragbackend.admin.controller.vo;

import java.util.List;

public record DashboardTrendSeriesVO(String name, List<DashboardTrendPointVO> data) {
}
