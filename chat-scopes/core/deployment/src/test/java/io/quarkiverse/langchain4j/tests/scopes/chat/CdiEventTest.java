package io.quarkiverse.langchain4j.tests.scopes.chat;

import java.util.ArrayList;
import java.util.List;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.langchain4j.chatscopes.*;
import io.quarkus.test.QuarkusUnitTest;

public class CdiEventTest {

    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .setArchiveProducer(
                    () -> ShrinkWrap.create(JavaArchive.class).addClasses(
                            EventHandler.class));

    @ApplicationScoped
    public static class EventHandler {
        List<ChatScopeCDIEvent> events = new ArrayList<>();

        public List<ChatScopeCDIEvent> events() {
            return events;
        }

        public void onStarted(@Observes ChatScopeStarted event) {
            events.add(event);
        }

        public void onActivated(@Observes ChatScopeActivated event) {
            events.add(event);
        }

        public void onDeactivated(@Observes ChatScopeDeactivated event) {
            events.add(event);
        }

        public void onEnded(@Observes ChatScopeEnded event) {
            events.add(event);
        }
    }

    @Inject
    EventHandler handler;

    @Test
    public void testCdiEvent() {
        List<ChatScopeCDIEvent> events = handler.events();
        ChatScope.begin();
        ChatScope top = ChatScope.current();
        Assertions.assertInstanceOf(ChatScopeStarted.class, events.get(0));
        Assertions.assertTrue(events.get(0).scope() == top);
        Assertions.assertInstanceOf(ChatScopeActivated.class, events.get(1));
        Assertions.assertTrue(events.get(1).scope() == top);
        events.clear();

        ChatScope.push();
        ChatScope child = ChatScope.current();
        Assertions.assertInstanceOf(ChatScopeDeactivated.class, events.get(0));
        Assertions.assertTrue(events.get(0).scope() == top);
        Assertions.assertInstanceOf(ChatScopeStarted.class, events.get(1));
        Assertions.assertTrue(events.get(1).scope() == child);
        Assertions.assertInstanceOf(ChatScopeActivated.class, events.get(2));
        Assertions.assertTrue(events.get(2).scope() == child);
        events.clear();

        ChatScope.pop();
        Assertions.assertInstanceOf(ChatScopeDeactivated.class, events.get(0));
        Assertions.assertTrue(events.get(0).scope() == child);
        Assertions.assertInstanceOf(ChatScopeEnded.class, events.get(1));
        Assertions.assertTrue(events.get(1).scope() == child);
        Assertions.assertTrue(events.get(2) instanceof ChatScopeActivated);
        Assertions.assertTrue(events.get(2).scope() == top);
        events.clear();
        ChatScope.end();
        Assertions.assertTrue(events.get(0) instanceof ChatScopeDeactivated);
        Assertions.assertTrue(events.get(0).scope() == top);
        Assertions.assertTrue(events.get(1) instanceof ChatScopeEnded);
        Assertions.assertTrue(events.get(1).scope() == top);
    }
}
