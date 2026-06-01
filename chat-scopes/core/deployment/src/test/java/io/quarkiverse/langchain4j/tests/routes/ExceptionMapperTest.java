package io.quarkiverse.langchain4j.tests.routes;

import java.util.ArrayList;
import java.util.List;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.langchain4j.chatscopes.ChatRoute;
import io.quarkiverse.langchain4j.chatscopes.ChatRouteContext;
import io.quarkiverse.langchain4j.chatscopes.ChatRouteExceptionHandler;
import io.quarkiverse.langchain4j.chatscopes.ChatRoutes;
import io.quarkiverse.langchain4j.chatscopes.LocalChatRoutes;
import io.quarkus.test.QuarkusUnitTest;

public class ExceptionMapperTest {
    public static class MyException extends RuntimeException {

    }

    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .setArchiveProducer(
                    () -> ShrinkWrap.create(JavaArchive.class).addClasses(
                            MyChat.class, MyException.class));

    @ApplicationScoped
    public static class MyChat {

        @ChatRoute("default-handler")
        public String defaultHandler() {
            throw new MyException();
        }

        @ChatRoute("default-superclass")
        public String defaultSuperclass() {
            throw new IllegalArgumentException();
        }

        @ChatRoute("handler")
        public String handler() {
            throw new MyException();
        }

        @ChatRoute("handler-superclass")
        public String handlerSuperclass() {
            throw new IllegalArgumentException();
        }

        @ChatRouteExceptionHandler
        public static void defaultExceptionHandler(MyException e, ChatRouteContext ctx) {
            ctx.response().error("default-my-exception");
            ChatRoutes.current("default-superclass");
        }

        @ChatRouteExceptionHandler
        public void defaultSuperclassHandler(RuntimeException e, ChatRouteContext ctx) {
            ctx.response().error("default-superclass");
            ChatRoutes.current("handler");
        }

        @ChatRouteExceptionHandler("handler")
        public void handle(MyException e, ChatRouteContext ctx) {
            ctx.response().error("route-my-exception");
            ChatRoutes.current("handler-superclass");
        }

        @ChatRouteExceptionHandler("handler-superclass")
        public void handleSuperclass(RuntimeException e, ChatRouteContext ctx) {
            ctx.response().error("route-superclass");
        }
    }

    @Inject
    LocalChatRoutes.Client localChatRouter;

    @Test
    public void testExceptionHandler() {
        List<String> errors = new ArrayList<>();

        LocalChatRoutes.Session session = localChatRouter.builder().errorHandler((event) -> {
            errors.add(event);
        }).connect("default-handler");

        session.chat("Hi");

        Assertions.assertEquals(1, errors.size());
        Assertions.assertEquals("default-my-exception", errors.get(0));
        errors.clear();

        session.chat("Hi");

        Assertions.assertEquals(1, errors.size());
        Assertions.assertEquals("default-superclass", errors.get(0));
        errors.clear();

        session.chat("Hi");

        Assertions.assertEquals(1, errors.size());
        Assertions.assertEquals("route-my-exception", errors.get(0));
        errors.clear();

        session.chat("Hi");

        Assertions.assertEquals(1, errors.size());
        Assertions.assertEquals("route-superclass", errors.get(0));
        errors.clear();

        session.close();
    }
}
