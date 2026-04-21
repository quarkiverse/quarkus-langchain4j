package io.quarkiverse.langchain4j.tests.scopes.invocation;

import io.quarkiverse.langchain4j.chatscopes.InvocationScoped;

@InvocationScoped
public class CounterBean {

    int counter = 0;

    public void increment() {
        counter++;
    }

    public int getCounter() {
        return counter;
    }

}
