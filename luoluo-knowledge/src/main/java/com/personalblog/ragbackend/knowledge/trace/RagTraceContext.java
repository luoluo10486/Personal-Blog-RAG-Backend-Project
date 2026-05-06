package com.personalblog.ragbackend.knowledge.trace;

import java.util.ArrayDeque;
import java.util.Deque;

public final class RagTraceContext {
    private static final ThreadLocal<String> TRACE_ID = new ThreadLocal<>();
    private static final ThreadLocal<String> TASK_ID = new ThreadLocal<>();
    private static final ThreadLocal<Deque<String>> NODE_STACK = new ThreadLocal<>();

    private RagTraceContext() {
    }

    public static String getTraceId() {
        return TRACE_ID.get();
    }

    public static void setTraceId(String traceId) {
        TRACE_ID.set(traceId);
    }

    public static String getTaskId() {
        return TASK_ID.get();
    }

    public static void setTaskId(String taskId) {
        TASK_ID.set(taskId);
    }

    public static int depth() {
        Deque<String> stack = NODE_STACK.get();
        return stack == null ? 0 : stack.size();
    }

    public static String currentNodeId() {
        Deque<String> stack = NODE_STACK.get();
        return stack == null ? null : stack.peek();
    }

    public static void pushNode(String nodeId) {
        Deque<String> stack = NODE_STACK.get();
        if (stack == null) {
            stack = new ArrayDeque<>();
            NODE_STACK.set(stack);
        }
        stack.push(nodeId);
    }

    public static void popNode() {
        Deque<String> stack = NODE_STACK.get();
        if (stack == null || stack.isEmpty()) {
            return;
        }
        stack.pop();
        if (stack.isEmpty()) {
            NODE_STACK.remove();
        }
    }

    public static void clear() {
        TRACE_ID.remove();
        TASK_ID.remove();
        NODE_STACK.remove();
    }
}
