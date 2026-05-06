package com.personalblog.ragbackend.knowledge.service.rewrite;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.personalblog.ragbackend.knowledge.dao.entity.QueryTermMappingEntity;
import com.personalblog.ragbackend.knowledge.dto.KnowledgeQueryRewriteResult;
import com.personalblog.ragbackend.knowledge.mapper.QueryTermMappingMapper;
import com.personalblog.ragbackend.knowledge.trace.RagTraceNode;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

@Service
public class QueryTermRewriteService {
    private final QueryTermMappingMapper queryTermMappingMapper;

    public QueryTermRewriteService(QueryTermMappingMapper queryTermMappingMapper) {
        this.queryTermMappingMapper = queryTermMappingMapper;
    }

    @RagTraceNode(name = "query-rewrite", type = "REWRITE")
    public KnowledgeQueryRewriteResult rewrite(String question, String domain) {
        String originalQuestion = question == null ? "" : question.trim();
        if (!StringUtils.hasText(originalQuestion)) {
            return new KnowledgeQueryRewriteResult("", "", List.of());
        }

        List<QueryTermMappingEntity> mappings = queryTermMappingMapper.selectList(
                new QueryWrapper<QueryTermMappingEntity>()
                        .eq("enabled", 1)
                        .eq("deleted", 0)
                        .and(wrapper -> {
                            if (StringUtils.hasText(domain)) {
                                wrapper.eq("domain", domain.trim())
                                        .or()
                                        .isNull("domain")
                                        .or()
                                        .eq("domain", "");
                            } else {
                                wrapper.isNull("domain").or().eq("domain", "");
                            }
                        })
                        .orderByAsc("priority")
                        .orderByAsc("id")
        );

        String rewritten = originalQuestion;
        List<String> appliedMappings = new ArrayList<>();
        for (QueryTermMappingEntity mapping : mappings.stream()
                .sorted(Comparator.comparingInt(value -> value.priority == null ? 100 : value.priority))
                .toList()) {
            String candidate = applyMapping(rewritten, mapping);
            if (!candidate.equals(rewritten)) {
                appliedMappings.add(describe(mapping));
                rewritten = candidate;
            }
        }
        return new KnowledgeQueryRewriteResult(originalQuestion, rewritten, appliedMappings);
    }

    private String applyMapping(String question, QueryTermMappingEntity mapping) {
        if (mapping == null || !StringUtils.hasText(question) || !StringUtils.hasText(mapping.sourceTerm) || !StringUtils.hasText(mapping.targetTerm)) {
            return question;
        }
        String source = mapping.sourceTerm.trim();
        String target = mapping.targetTerm.trim();
        int matchType = mapping.matchType == null ? 1 : mapping.matchType;
        return switch (matchType) {
            case 2 -> replacePrefix(question, source, target);
            case 3 -> replaceRegex(question, source, target);
            case 4 -> replaceWholeWord(question, source, target);
            default -> question.replace(source, target);
        };
    }

    private String replacePrefix(String question, String source, String target) {
        if (question.startsWith(source)) {
            return target + question.substring(source.length());
        }
        return question;
    }

    private String replaceRegex(String question, String source, String target) {
        try {
            return question.replaceAll(source, target);
        } catch (RuntimeException ignored) {
            return question;
        }
    }

    private String replaceWholeWord(String question, String source, String target) {
        String safeSource = Pattern.quote(source);
        String pattern = "(?<![\\p{L}\\p{N}_])" + safeSource + "(?![\\p{L}\\p{N}_])";
        try {
            return question.replaceAll(pattern, target);
        } catch (RuntimeException ignored) {
            return question.replace(source, target);
        }
    }

    private String describe(QueryTermMappingEntity mapping) {
        String domain = StringUtils.hasText(mapping.domain) ? mapping.domain.trim() : "*";
        return String.format(Locale.ROOT, "%s:%s->%s#%s",
                domain,
                mapping.sourceTerm,
                mapping.targetTerm,
                mapping.matchType == null ? 1 : mapping.matchType);
    }
}
