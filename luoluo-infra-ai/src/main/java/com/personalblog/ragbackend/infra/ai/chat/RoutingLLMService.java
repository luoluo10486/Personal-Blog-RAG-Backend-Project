package com.personalblog.ragbackend.infra.ai.chat;

import com.personalblog.ragbackend.infra.ai.convention.ChatRequest;
import com.personalblog.ragbackend.infra.ai.enums.ModelCapability;
import com.personalblog.ragbackend.infra.ai.errorcode.BaseErrorCode;
import com.personalblog.ragbackend.infra.ai.exception.RemoteException;
import com.personalblog.ragbackend.infra.ai.model.ModelHealthStore;
import com.personalblog.ragbackend.infra.ai.model.ModelRoutingExecutor;
import com.personalblog.ragbackend.infra.ai.model.ModelSelector;
import com.personalblog.ragbackend.infra.ai.model.ModelTarget;
import com.personalblog.ragbackend.infra.ai.trace.RagTraceNode;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@Primary
public class RoutingLLMService implements LLMService {

    private static final int FIRST_PACKET_TIMEOUT_SECONDS = 60;
    private static final String STREAM_INTERRUPTED_MESSAGE = "Stream request interrupted";
    private static final String STREAM_NO_PROVIDER_MESSAGE = "No chat model provider available";
    private static final String STREAM_START_FAILED_MESSAGE = "Stream request failed to start";
    private static final String STREAM_TIMEOUT_MESSAGE = "Timed out waiting for first stream packet";
    private static final String STREAM_NO_CONTENT_MESSAGE = "Stream completed without content";
    private static final String STREAM_ALL_FAILED_MESSAGE = "All chat model stream attempts failed";

    private final ModelSelector selector;
    private final ModelHealthStore healthStore;
    private final ModelRoutingExecutor executor;
    private final Map<String, ChatClient> clientsByProvider;

    public RoutingLLMService(ModelSelector selector,
                             ModelHealthStore healthStore,
                             ModelRoutingExecutor executor,
                             List<ChatClient> clients) {
        this.selector = selector;
        this.healthStore = healthStore;
        this.executor = executor;
        this.clientsByProvider = clients.stream()
                .collect(Collectors.toMap(ChatClient::provider, Function.identity()));
    }

    @Override
    @RagTraceNode(name = "llm-chat-routing", type = "LLM_ROUTING")
    public String chat(ChatRequest request) {
        return executor.executeWithFallback(
                ModelCapability.CHAT,
                selector.selectChatCandidates(Boolean.TRUE.equals(request.getThinking())),
                target -> clientsByProvider.get(target.candidate().getProvider()),
                (client, target) -> client.chat(request, target)
        );
    }

    @Override
    public String chat(ChatRequest request, String modelId) {
        if (modelId == null || modelId.isBlank()) {
            return chat(request);
        }
        return executor.executeWithFallback(
                ModelCapability.CHAT,
                List.of(resolveTarget(modelId, Boolean.TRUE.equals(request.getThinking()))),
                target -> clientsByProvider.get(target.candidate().getProvider()),
                (client, target) -> client.chat(request, target)
        );
    }

    @Override
    @RagTraceNode(name = "llm-stream-routing", type = "LLM_ROUTING")
    public StreamCancellationHandle streamChat(ChatRequest request, StreamCallback callback) {
        List<ModelTarget> targets = selector.selectChatCandidates(Boolean.TRUE.equals(request.getThinking()));
        if (targets.isEmpty()) {
            throw new RemoteException(STREAM_NO_PROVIDER_MESSAGE);
        }

        Throwable lastError = null;
        for (ModelTarget target : targets) {
            ChatClient client = clientsByProvider.get(target.candidate().getProvider());
            if (client == null || !healthStore.allowCall(target.id())) {
                continue;
            }

            ProbeStreamBridge bridge = new ProbeStreamBridge(callback);
            StreamCancellationHandle handle;
            try {
                handle = client.streamChat(request, bridge, target);
            } catch (Exception ex) {
                healthStore.markFailure(target.id());
                lastError = ex;
                continue;
            }

            if (handle == null) {
                healthStore.markFailure(target.id());
                lastError = new RemoteException(STREAM_START_FAILED_MESSAGE, BaseErrorCode.REMOTE_ERROR);
                continue;
            }

            ProbeStreamBridge.ProbeResult result = awaitFirstPacket(bridge, handle, callback);
            if (result.isSuccess()) {
                healthStore.markSuccess(target.id());
                return handle;
            }

            healthStore.markFailure(target.id());
            handle.cancel();
            lastError = buildLastError(result);
        }

        throw notifyAllFailed(callback, lastError);
    }

    private ProbeStreamBridge.ProbeResult awaitFirstPacket(ProbeStreamBridge bridge,
                                                           StreamCancellationHandle handle,
                                                           StreamCallback callback) {
        try {
            return bridge.awaitFirstPacket(FIRST_PACKET_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            handle.cancel();
            RemoteException interrupted = new RemoteException(STREAM_INTERRUPTED_MESSAGE, ex, BaseErrorCode.REMOTE_ERROR);
            callback.onError(interrupted);
            throw interrupted;
        }
    }

    private Throwable buildLastError(ProbeStreamBridge.ProbeResult result) {
        return switch (result.getType()) {
            case ERROR -> result.getError() != null
                    ? result.getError()
                    : new RemoteException("Stream request failed", BaseErrorCode.REMOTE_ERROR);
            case TIMEOUT -> new RemoteException(STREAM_TIMEOUT_MESSAGE, BaseErrorCode.REMOTE_ERROR);
            case NO_CONTENT -> new RemoteException(STREAM_NO_CONTENT_MESSAGE, BaseErrorCode.REMOTE_ERROR);
            case SUCCESS -> null;
        };
    }

    private RemoteException notifyAllFailed(StreamCallback callback, Throwable lastError) {
        RemoteException finalException = new RemoteException(
                STREAM_ALL_FAILED_MESSAGE,
                lastError,
                BaseErrorCode.REMOTE_ERROR
        );
        callback.onError(finalException);
        return finalException;
    }

    private ModelTarget resolveTarget(String modelId, boolean deepThinking) {
        return selector.selectChatCandidates(deepThinking).stream()
                .filter(target -> modelId.equals(target.id()))
                .findFirst()
                .orElseThrow(() -> new RemoteException("Chat model is unavailable: " + modelId));
    }
}
