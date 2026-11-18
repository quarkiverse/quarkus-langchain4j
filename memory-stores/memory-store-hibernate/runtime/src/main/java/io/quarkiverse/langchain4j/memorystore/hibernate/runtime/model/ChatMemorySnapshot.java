package io.quarkiverse.langchain4j.memorystore.hibernate.runtime.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** One ordered message snapshot, with a version for conflicting database transactions. */
@Entity(name = "LangChain4jChatMemorySnapshot")
@Table(name = "langchain4j_chat_memory")
public class ChatMemorySnapshot {
    @Id
    @Column(name = "memory_id", length = 255, nullable = false)
    private String memoryId;

    @JdbcTypeCode(SqlTypes.LONG32VARCHAR)
    @Column(name = "messages_json", nullable = false)
    private String messagesJson;

    @Version
    private long version;

    protected ChatMemorySnapshot() {
    }

    public ChatMemorySnapshot(String memoryId, String messagesJson) {
        this.memoryId = memoryId;
        this.messagesJson = messagesJson;
    }

    public String messagesJson() {
        return messagesJson;
    }

    public void messagesJson(String messagesJson) {
        this.messagesJson = messagesJson;
    }
}
