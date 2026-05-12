package com.personalblog.ragbackend.admin.controller.vo;

public record DashboardOverviewVO(String window,
                                  String compareWindow,
                                  Long updatedAt,
                                  DashboardOverviewGroupVO kpis) {
}
