package io.quarkiverse.langchain4j.chatscopes.passivation.test.shutdown;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;

import io.quarkiverse.langchain4j.chatscopes.ChatScoped;
import io.quarkiverse.langchain4j.chatscopes.PostActivate;
import io.quarkiverse.langchain4j.chatscopes.PrePassivate;

@ChatScoped
public class CounterBean {
    private int counter = 0;
    private int passivationCounter = 0;
    private boolean postActivateCalled = false;

    public void increment() {
        counter++;
    }

    public int getCounter() {
        return counter;
    }

    @PrePassivate
    public void prePassivate() {
        System.out.println("**************** PRE PASSIVATE COUNTER: " + counter);
        passivationCounter = counter + 1000;
    }

    public int getPassivationCounter() {
        return passivationCounter;
    }

    public boolean isPostActivateCalled() {
        return postActivateCalled;
    }

    @PostActivate
    public void postActivate() {
        System.out.println("**************** POST ACTIVATE COUNTER: " + counter);
        postActivateCalled = true;
    }

    @PostConstruct
    public void init() {
        System.out.println("**************** POST CONSTRUCT COUNTER: " + counter);
    }

    @PreDestroy
    public void destroy() {
        System.out.println("**************** PRE DESTROY COUNTER");
    }

}
