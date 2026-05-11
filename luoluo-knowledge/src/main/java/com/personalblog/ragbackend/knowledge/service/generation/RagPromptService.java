package com.personalblog.ragbackend.knowledge.service.generation;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.personalblog.ragbackend.infra.ai.convention.ChatMessage;
import com.personalblog.ragbackend.infra.ai.convention.ChatRequest;
import com.personalblog.ragbackend.knowledge.service.prompt.PromptTemplateLoader;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class RagPromptService {
    private static final String CHAT_SYSTEM_PROMPT_PATH = "prompt/chat-system.st";

    private final PromptTemplateLoader promptTemplateLoader;
    private final KnowledgeContextFormatter knowledgeContextFormatter;

    public RagPromptService(PromptTemplateLoader promptTemplateLoader,
                            KnowledgeContextFormatter knowledgeContextFormatter) {
        this.promptTemplateLoader = promptTemplateLoader;
        this.knowledgeContextFormatter = knowledgeContextFormatter;
    }

    public ChatRequest buildChatRequest(RagPromptContext promptContext,
                                        List<ChatMessage> history,
                                        boolean deepThinking,
                                        boolean hasMcp) {
        return ChatRequest.builder()
                .messages(buildStructuredMessages(promptContext, history))
                .thinking(deepThinking)
                .temperature(hasMcp ? 0.3D : 0D)
                .topP(hasMcp ? 0.8D : 1D)
                .maxTokens(1024)
                .build();
    }

    public List<ChatMessage> buildStructuredMessages(RagPromptContext promptContext,
                                                     List<ChatMessage> history) {
        List<ChatMessage> messages = new ArrayList<>();
        messages.add(ChatMessage.system(promptTemplateLoader.load(CHAT_SYSTEM_PROMPT_PATH)));
        if (CollUtil.isNotEmpty(history)) {
            messages.addAll(history);
        }
        messages.add(ChatMessage.user(buildUserPrompt(promptContext)));
        return messages;
    }

    private String buildUserPrompt(RagPromptContext promptContext) {
        StringBuilder builder = new StringBuilder();
        builder.append("Question: ").append(StrUtil.blankToDefault(promptContext.question(), "")).append("\n\n");
        String kbContext = knowledgeContextFormatter.formatKbContext(promptContext.kbIntents(), promptContext.chunks());
        if (StrUtil.isNotBlank(kbContext)) {
            builder.append(kbContext).append("\n\n");
        }
        String mcpContext = knowledgeContextFormatter.formatMcpContext(promptContext.mcpContext());
        if (StrUtil.isNotBlank(mcpContext)) {
            builder.append(mcpContext).append("\n\n");
        }
        builder.append("Answer the question using only the evidence above. ");
        builder.append("If evidence is insufficient, say so directly. ");
        builder.append("Use [Kx] and [Mx] citations for key claims.");
        return builder.toString();
    }
}
