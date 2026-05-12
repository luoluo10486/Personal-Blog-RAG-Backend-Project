package com.personalblog.ragbackend.admin.controller;

import com.personalblog.ragbackend.admin.controller.vo.DashboardOverviewVO;
import com.personalblog.ragbackend.admin.controller.vo.DashboardPerformanceVO;
import com.personalblog.ragbackend.admin.controller.vo.DashboardTrendsVO;
import com.personalblog.ragbackend.admin.service.DashboardService;
import com.personalblog.ragbackend.common.satoken.annotation.MemberLoginRequired;
import com.personalblog.ragbackend.common.web.domain.R;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin/dashboard")
@MemberLoginRequired
public class DashboardController {
    private final DashboardService dashboardService;

    public DashboardController(DashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    @GetMapping("/overview")
    public R<DashboardOverviewVO> overview(@RequestParam(required = false) String window) {
        return R.ok(dashboardService.loadOverview(window));
    }

    @GetMapping("/performance")
    public R<DashboardPerformanceVO> performance(@RequestParam(required = false) String window) {
        return R.ok(dashboardService.loadPerformance(window));
    }

    @GetMapping("/trends")
    public R<DashboardTrendsVO> trends(@RequestParam String metric,
                                       @RequestParam(required = false) String window,
                                       @RequestParam(required = false) String granularity) {
        return R.ok(dashboardService.loadTrends(metric, window, granularity));
    }
}
