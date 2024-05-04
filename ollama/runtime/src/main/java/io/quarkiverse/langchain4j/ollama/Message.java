package io.quarkiverse.langchain4j.ollama;

import java.util.List;

public record Message(Role role, String content, List<String> images) {

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private Role role;
        private String content;
        private List<String> images;

        public Builder role(Role role) {
            this.role = role;
            return this;
        }

        public Builder content(String content) {
            this.content = content;
            return this;
        }

        public Builder images(List<String> images) {
            this.images = images;
            return this;
        }

        public Message build() {
            return new Message(role, content, images);
        }
    }

}
