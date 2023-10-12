package io.quarkiverse.langchain4j;

import com.fasterxml.jackson.annotation.JsonInclude;

public class ChatMessage {

    /**
     * Must be either 'system', 'user', 'assistant' or 'function'
     */
    private String role;
    @JsonInclude
    private String content;
    //name is optional, The name of the author of this message. May contain a-z, A-Z, 0-9, and underscores, with a maximum length of 64 characters.
    private String name;

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }
}
