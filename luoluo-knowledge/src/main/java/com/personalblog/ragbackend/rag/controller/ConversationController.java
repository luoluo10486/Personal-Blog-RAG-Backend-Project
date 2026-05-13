package com.personalblog.ragbackend.rag.controller;

import com.personalblog.ragbackend.common.context.UserContext;
import com.personalblog.ragbackend.common.satoken.annotation.MemberLoginRequired;
import com.personalblog.ragbackend.common.web.domain.Result;
import com.personalblog.ragbackend.common.web.domain.Results;
import com.personalblog.ragbackend.rag.controller.request.ConversationUpdateRequest;
import com.personalblog.ragbackend.rag.controller.vo.ConversationMessageVO;
import com.personalblog.ragbackend.rag.controller.vo.ConversationVO;
import com.personalblog.ragbackend.rag.enums.ConversationMessageOrder;
import com.personalblog.ragbackend.rag.service.ConversationMessageService;
import com.personalblog.ragbackend.rag.service.ConversationService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@MemberLoginRequired
@RequiredArgsConstructor
public class ConversationController {
    private final ConversationService conversationService;
    private final ConversationMessageService conversationMessageService;

    @GetMapping("/conversations")
    public Result<List<ConversationVO>> listConversations() {
        return Results.success(conversationService.listByUserId(UserContext.getUserId()));
    }

    @PutMapping("/conversations/{conversationId}")
    public Result<Void> rename(@PathVariable String conversationId,
                               @RequestBody ConversationUpdateRequest requestParam) {
        conversationService.rename(conversationId, requestParam);
        return Results.success();
    }

    @DeleteMapping("/conversations/{conversationId}")
    public Result<Void> delete(@PathVariable String conversationId) {
        conversationService.delete(conversationId);
        return Results.success();
    }

    @GetMapping("/conversations/{conversationId}/messages")
    public Result<List<ConversationMessageVO>> listMessages(@PathVariable String conversationId) {
        return Results.success(conversationMessageService.listMessages(
                conversationId,
                UserContext.getUserId(),
                null,
                ConversationMessageOrder.ASC));
    }
}
