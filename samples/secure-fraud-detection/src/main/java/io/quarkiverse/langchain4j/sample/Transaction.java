package io.quarkiverse.langchain4j.sample;

import java.time.LocalDateTime;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;

@Entity
public class Transaction {

    @Id
    @GeneratedValue
    public Long id;

    public double amount;

    public String customerName;

    public String customerEmail;

    public String city;

    public LocalDateTime time;

}
