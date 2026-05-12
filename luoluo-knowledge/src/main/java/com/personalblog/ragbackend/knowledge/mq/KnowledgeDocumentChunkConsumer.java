package com.personalblog.ragbackend.knowledge.mq;

import com.personalblog.ragbackend.common.context.LoginUser;
import com.personalblog.ragbackend.common.context.UserContext;
import com.personalblog.ragbackend.knowledge.mq.event.KnowledgeDocumentChunkEvent;
import com.personalblog.ragbackend.knowledge.service.admin.KnowledgeDocumentAdminService;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
@RocketMQMessageListener(
        topic = "knowledge-document-chunk_topic${unique-name:}",
        consumerGroup = "knowledge-document-chunk_cg${unique-name:}"
)
public class KnowledgeDocumentChunkConsumer implements RocketMQListener<MessageWrapper<KnowledgeDocumentChunkEvent>> {
    private static final Logger log = LoggerFactory.getLogger(KnowledgeDocumentChunkConsumer.class);

    private final KnowledgeDocumentAdminService knowledgeDocumentAdminService;

    public KnowledgeDocumentChunkConsumer(KnowledgeDocumentAdminService knowledgeDocumentAdminService) {
        this.knowledgeDocumentAdminService = knowledgeDocumentAdminService;
    }

    @Override
    public void onMessage(MessageWrapper<KnowledgeDocumentChunkEvent> message) {
        KnowledgeDocumentChunkEvent event = message == null ? null : message.getBody();
        if (event == null || event.getDocumentId() == null) {
            log.warn("Skip empty knowledge document chunk event");
            return;
        }

        LoginUser loginUser = null;
        if (StringUtils.hasText(event.getOperator())) {
            loginUser = new LoginUser();
            loginUser.setUsername(event.getOperator());
        }

        if (loginUser != null) {
            UserContext.set(loginUser);
        }
        try {
            knowledgeDocumentAdminService.executeChunk(event.getDocumentId());
        } finally {
            UserContext.clear();
        }
    }
}
