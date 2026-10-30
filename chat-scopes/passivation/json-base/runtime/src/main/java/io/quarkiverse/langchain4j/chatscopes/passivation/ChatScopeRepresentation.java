package io.quarkiverse.langchain4j.chatscopes.passivation;

import java.util.HashMap;
import java.util.Map;

public class ChatScopeRepresentation {
    public String id;
    public String parent;
    public String route;
    public Map<String, String> beans = new HashMap<>();

    public ChatScopeRepresentation() {
    }

    public ChatScopeRepresentation(String id) {
        this.id = id;
    }
}
