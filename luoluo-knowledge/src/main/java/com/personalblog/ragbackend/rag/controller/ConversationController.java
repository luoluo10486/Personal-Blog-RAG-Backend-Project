package com.personalblog.ragbackend.rag.controller;

import com.personalblog.ragbackend.common.context.UserContext;
import com.personalblog.ragbackend.common.satoken.annotation.MemberLoginRequired;
import com.personalblog.ragbackend.common.web.domain.R;
import com.personalblog.ragbackend.rag.controller.request.ConversationUpdateRequest;
import com.personalblog.ragbackend.rag.controller.vo.ConversationMessageVO;
import com.personalblog.ragbackend.rag.controller.vo.ConversationVO;
import com.personalblog.ragbackend.rag.enums.ConversationMessageOrder;
import com.personalblog.ragbackend.rag.service.ConversationMessageService;
import com.personalblog.ragbackend.rag.service.ConversationService;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@MemberLoginRequired
public class ConversationController {
    private final ConversationService conversationService;
    private final ConversationMessageService conversationMessageService;

    public ConversationController(ConversationService conversationService,
                                   ConversationMessageService conversationMessageService) {
        this.conversationService = conversationService;
        this.conversationMessageService = conversationMessageService;
    }

    @GetMapping("/conversations")
    public R<List<ConversationVO>> listConversations() {
        return R.ok(conversationService.listByUserId(UserContext.getUserId()));
    }

    @PutMapping("/conversations/{conversationId}")
    public R<Void> rename(@PathVariable String conversationId,
                          @RequestBody ConversationUpdateRequest request) {
        conversationService.rename(conversationId, request);
        return R.ok();
    }

    @DeleteMapping("/conversations/{conversationId}")
    public R<Void> delete(@PathVariable String conversationId) {
        conversationService.delete(conversationId);
        return R.ok();
    }

    @GetMapping("/conversations/{conversationId}/messages")
    public R<List<ConversationMessageVO>> listMessages(@PathVariable String conversationId) {
        return R.ok(conversationMessageService.listMessages(
                conversationId,
                UserContext.getUserId(),
                null,
                ConversationMessageOrder.ASC));
    }
}
