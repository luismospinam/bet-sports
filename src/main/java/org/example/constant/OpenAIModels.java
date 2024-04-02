package org.example.constant;

public enum OpenAIModels {
     GPT_4_PREVIEW("gpt-4-vision-preview"),
    GPT_4("gpt-4"),
    GPT_4_TURBO_PREVIEW("gpt-4-turbo-preview"),
    GPT_3_5_TURBO("gpt-3.5-turbo");

    OpenAIModels(String name) {
        this.name = name;
    }

    private final String name;

    public String getName() {
        return name;
    }
}
