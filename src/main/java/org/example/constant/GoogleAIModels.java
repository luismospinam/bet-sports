package org.example.constant;

public enum GoogleAIModels {
    GEMINI_PRO("gemini-pro"),
    GEMINI_1_5_PRO("gemini-1.5-pro-preview-0409");

    private final String name;

    GoogleAIModels(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }
}
