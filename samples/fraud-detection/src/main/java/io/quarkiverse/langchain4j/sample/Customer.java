package io.quarkiverse.langchain4j.sample;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;

@Entity
public class Customer {

    @Id
    @GeneratedValue
    public Long id;
    public String name;
    public String email;

}
