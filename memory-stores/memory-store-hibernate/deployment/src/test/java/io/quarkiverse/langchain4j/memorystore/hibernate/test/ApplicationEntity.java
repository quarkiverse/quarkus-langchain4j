package io.quarkiverse.langchain4j.memorystore.hibernate.test;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;

@Entity
public class ApplicationEntity {
    @Id
    public Long id;
}
