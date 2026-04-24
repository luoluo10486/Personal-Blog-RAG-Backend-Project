package com.personalblog.ragbackend.knowledge.service.generation;

import com.personalblog.ragbackend.knowledge.domain.KnowledgeChunk;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class TemplateKnowledgeAnswerGenerator implements KnowledgeAnswerGenerator {
    @Override
    public String generate(String question, List<KnowledgeChunk> chunks) {
        if (chunks == null || chunks.isEmpty()) {
            return "知识库模块骨架已就绪，但还没有接入真实检索结果。";
        }
        return "已从知识库召回 " + chunks.size() + " 条资料，后续可在这里接入独立的 LLM 生成链路。";
    }
}
