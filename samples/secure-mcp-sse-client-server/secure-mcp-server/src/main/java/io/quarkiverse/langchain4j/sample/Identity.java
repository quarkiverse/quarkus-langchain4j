package io.quarkiverse.langchain4j.sample;

import io.quarkus.hibernate.orm.panache.PanacheEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;

@Entity
public class Identity extends PanacheEntity {
    @Column(unique = true)
    public String name;
    public String permission;
    public static Identity findByName(String name) {
        return find("name", name).firstResult();
    }
}
