package io.quarkiverse.langchain4j.sample.compaction;

final class Prompts {

    // No tools on purpose: recall questions must be answered from the conversation, not by looking things up again.
    static final String SYSTEM = """
            You are the support agent of Brew & Co, an online shop selling coffee equipment.
            Answer only from the conversation. If the information is not in the conversation, say you do not know.
            Be concise and quote ids, tracking numbers and amounts exactly.
            """;

    private Prompts() {
    }
}
