package com.personalblog.ragbackend.rag.core.intent;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;

import java.util.List;

public final class NodeScoreFilters {
    private NodeScoreFilters() {
    }

    public static List<NodeScore> mcp(List<NodeScore> scores) {
        if (CollUtil.isEmpty(scores)) {
            return List.of();
        }
        return scores.stream()
                .filter(ns -> ns != null && ns.node() != null && ns.node().isMCP())
                .filter(ns -> StrUtil.isNotBlank(ns.node().getMcpToolId()))
                .toList();
    }

    public static List<NodeScore> kb(List<NodeScore> scores) {
        if (CollUtil.isEmpty(scores)) {
            return List.of();
        }
        return scores.stream()
                .filter(ns -> ns != null && ns.node() != null && ns.node().isKB())
                .toList();
    }

    public static List<NodeScore> kb(List<NodeScore> scores, double minScore) {
        if (CollUtil.isEmpty(scores)) {
            return List.of();
        }
        return scores.stream()
                .filter(ns -> ns != null && ns.score() >= minScore)
                .filter(ns -> ns.node() != null && ns.node().isKB())
                .toList();
    }
}
