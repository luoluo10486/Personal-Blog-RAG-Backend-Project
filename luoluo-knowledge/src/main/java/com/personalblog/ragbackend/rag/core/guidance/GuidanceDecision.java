package com.personalblog.ragbackend.rag.core.guidance;

public class GuidanceDecision {
    public enum Action {
        NONE,
        PROMPT
    }

    private final Action action;
    private final String prompt;

    private GuidanceDecision(Action action, String prompt) {
        this.action = action;
        this.prompt = prompt;
    }

    public static GuidanceDecision none() {
        return new GuidanceDecision(Action.NONE, null);
    }

    public static GuidanceDecision prompt(String prompt) {
        return new GuidanceDecision(Action.PROMPT, prompt);
    }

    public boolean isPrompt() {
        return action == Action.PROMPT;
    }

    public Action getAction() {
        return action;
    }

    public String getPrompt() {
        return prompt;
    }
}
