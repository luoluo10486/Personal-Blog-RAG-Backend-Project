package com.personalblog.ragbackend.rag.mq;

import com.personalblog.ragbackend.common.context.LoginUser;
import com.personalblog.ragbackend.common.context.UserContext;
import com.personalblog.ragbackend.knowledge.mq.MessageWrapper;
import com.personalblog.ragbackend.rag.mq.event.MessageFeedbackEvent;
import com.personalblog.ragbackend.rag.service.MessageFeedbackService;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
@RocketMQMessageListener(
        topic = "message-feedback_topic${unique-name:}",
        consumerGroup = "message-feedback_cg${unique-name:}"
)
public class MessageFeedbackConsumer implements RocketMQListener<MessageWrapper<MessageFeedbackEvent>> {
    private final MessageFeedbackService feedbackService;

    public MessageFeedbackConsumer(MessageFeedbackService feedbackService) {
        this.feedbackService = feedbackService;
    }

    @Override
    public void onMessage(MessageWrapper<MessageFeedbackEvent> message) {
        MessageFeedbackEvent event = message == null ? null : message.getBody();
        if (event == null || event.getMessageId() == null || event.getUserId() == null) {
            return;
        }
        LoginUser loginUser = new LoginUser();
        loginUser.setUserId(String.valueOf(event.getUserId()));
        if (StringUtils.hasText(String.valueOf(event.getUserId()))) {
            UserContext.set(loginUser);
        }
        try {
            feedbackService.submitFeedbackByEvent(event);
        } finally {
            UserContext.clear();
        }
    }
}
