package io.quarkiverse.langchain4j.chatscopes.passivation.test;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;

import jakarta.decorator.Decorator;
import jakarta.decorator.Delegate;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import jakarta.interceptor.AroundInvoke;
import jakarta.interceptor.Interceptor;
import jakarta.interceptor.InterceptorBinding;
import jakarta.interceptor.InvocationContext;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonFilter;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.jsonFormatVisitors.JsonObjectFormatVisitor;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.ser.FilterProvider;
import com.fasterxml.jackson.databind.ser.PropertyFilter;
import com.fasterxml.jackson.databind.ser.PropertyWriter;
import com.fasterxml.jackson.databind.ser.impl.SimpleFilterProvider;

import io.quarkiverse.langchain4j.chatscopes.ChatScope;
import io.quarkiverse.langchain4j.chatscopes.ChatScoped;
import io.quarkiverse.langchain4j.chatscopes.internal.ChatScopeStoreManager;
import io.quarkiverse.langchain4j.chatscopes.spi.ChatScopeStore;
import io.quarkus.arc.ClientProxy;
import io.quarkus.arc.InjectableBean;
import io.quarkus.arc.Subclass;
import io.quarkus.arc.Unremovable;
import io.quarkus.test.QuarkusUnitTest;

// test a prototype of implementation
public class PassivationWIPTest {
    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .setArchiveProducer(
                    () -> ShrinkWrap.create(JavaArchive.class).addClasses(
                            ChatBean.class, ChatDecorator.class, NestedBean.class, MyInterceptor.class,
                            MyInterceptorBinding.class, MyStore.class, Passivator.class, WIPPassivationBeanPropertyFilter.class,
                            GlobalFilterMixIn.class));

    public static void printFields(Object obj) {
        Class<?> clazz = obj.getClass();
        System.out.println("Class=" + clazz.getName());
        for (Class<?> i : clazz.getInterfaces()) {
            System.out.println("Interface=" + i.getName());
        }
        for (Field field : clazz.getDeclaredFields()) {
            field.setAccessible(true);
            Object value;
            try {
                value = field.get(obj);
            } catch (IllegalAccessException e) {
                throw new RuntimeException(e);
            }
            String actualType = value == null ? "null" : value.getClass().getName();
            System.out.println("field=" + field.getName()
                    + ", declaredType=" + field.getType().getName()
                    + ", actualType=" + actualType);
        }
    }

    @Documented
    @Inherited
    @Retention(RetentionPolicy.RUNTIME)
    @Target({ ElementType.TYPE, ElementType.METHOD })
    @InterceptorBinding
    public @interface MyInterceptorBinding {

    }

    @Interceptor
    @MyInterceptorBinding
    public static class MyInterceptor {
        @AroundInvoke
        public Object intercept(InvocationContext ctx) throws Exception {
            System.out.println("Interceptor called: ");
            return ctx.proceed();
        }
    }

    @ChatScoped
    public static class NestedBean {

    }

    public static interface Chat {
        String helloWorld();
    }

    @Decorator
    public static class AnotherDecorator implements Chat {
        @Inject
        @Delegate
        Chat delegate;

        @Override
        public String helloWorld() {
            System.out.println("Decorated called: next" + delegate.getClass().getName());
            System.out.println("----> THIS");
            printFields(this);
            System.out.println("----> DELEGATE");
            printFields(delegate);
            return delegate.helloWorld();
        }
    }

    @Decorator
    public static class ChatDecorator implements Chat {

        @Inject
        @Delegate
        Chat delegate;

        @Override
        public String helloWorld() {
            System.out.println("Decorated called: next" + delegate.getClass().getName());
            System.out.println("----> THIS");
            printFields(this);
            System.out.println("----> DELEGATE");
            printFields(delegate);
            return delegate.helloWorld();
        }
    }

    @ChatScoped
    public static class ChatBean implements Chat {
        @Inject
        NestedBean nestedBean;

        String val = "myField";

        public ChatBean() {
        }

        @Override
        //@MyInterceptorBinding
        public String helloWorld() {
            printFields(this);

            new Exception("STACK TRACE").printStackTrace();
            System.out.println("returning hellow world");
            return "Hello World";
        }

    }

    @Inject
    ChatBean chatBean;

    static ObjectMapper mapper = new ObjectMapper();
    static FilterProvider filters;

    static {
        mapper.setDefaultMergeable(true);
        mapper.setVisibility(PropertyAccessor.ALL, JsonAutoDetect.Visibility.NONE);
        mapper.setVisibility(PropertyAccessor.FIELD, JsonAutoDetect.Visibility.ANY);

        SimpleFilterProvider filterProvider = new SimpleFilterProvider();
        filterProvider.setDefaultFilter(new WIPPassivationBeanPropertyFilter())
                .setFailOnUnknownId(false);
        filters = filterProvider;
        mapper.setFilterProvider(filterProvider);
        mapper.addMixIn(Object.class, GlobalFilterMixIn.class);
    }

    public static class Passivator implements ChatScopeStore.PassivateTransaction {
        public static Map<String, Map<String, String>> scopes = new HashMap<>();

        ObjectMapper mapper = new ObjectMapper();
        FilterProvider filters;

        public Passivator() {
            mapper.setDefaultMergeable(true);
            mapper.setVisibility(PropertyAccessor.ALL, JsonAutoDetect.Visibility.NONE);
            mapper.setVisibility(PropertyAccessor.FIELD, JsonAutoDetect.Visibility.ANY);

            SimpleFilterProvider filterProvider = new SimpleFilterProvider();
            filterProvider.setDefaultFilter(new WIPPassivationBeanPropertyFilter())
                    .setFailOnUnknownId(false);
            filters = filterProvider;
            mapper.setFilterProvider(filterProvider);
            mapper.addMixIn(Object.class, GlobalFilterMixIn.class);
        }

        @Override
        public void passivate(ChatScope scope, Map<InjectableBean<?>, Object> beans) {
            for (Map.Entry<InjectableBean<?>, Object> entry : beans.entrySet()) {
                passivate(scope, entry.getKey(), entry.getValue());
            }
        }

        public void passivate(ChatScope scope, InjectableBean<?> bean, Object instance) {
            Map<String, String> beans = scopes.computeIfAbsent(scope.getId(), k -> new HashMap<>());
            try {
                System.out.println("Passivating " + bean.getIdentifier() + " class " + instance.getClass().getName());

                String json = null;
                if (instance instanceof Subclass) {
                    json = mapper.writerFor(instance.getClass().getSuperclass()).writeValueAsString(instance);
                } else {
                    json = mapper.writeValueAsString(instance);

                }
                System.out.println(json);
                beans.put(bean.getIdentifier(), json);
            } catch (JsonProcessingException e) {
                throw new RuntimeException(e);
            }

        }

        @Override
        public void commit() {

        }

        @Override
        public void rollback() {

        }
    }

    @Singleton
    @Unremovable
    public static class MyStore implements ChatScopeStore {
        @Override
        public PassivateTransaction beginPassivate() {
            return new Passivator();
        }

        @Override
        public PassivatedScope activate(String chatScopeId) {
            return null;
        }

        @Override
        public Object activateBean(ChatScope scope, String beanId, Object instance) {
            return null;
        }

        @Override
        public void destroy(ChatScope scope) {

        }
    }

    @Test
    public void test() {
        System.out.println("--------------- CHAT SCOPE PASSIVATION --------------");
        ChatScope.begin();
        System.out.println(">>>> INJECTED " + ClientProxy.unwrap(chatBean).getClass().getName());
        chatBean.helloWorld();
        ChatScopeStoreManager.passivate(ChatScope.current());
        ChatScope.end();
    }

    public static class Pojo {
        public String name;

        public Pojo(String name) {
            this.name = name;
        }

        public Pojo() {
        }
    }

    @JsonFilter("globalFilter")
    private interface GlobalFilterMixIn {
    }

    //@Test
    public void testFilter() throws Exception {
        PropertyFilter filter = new PropertyFilter() {
            @Override
            public void serializeAsField(Object pojo, JsonGenerator gen, SerializerProvider prov, PropertyWriter writer)
                    throws Exception {

                System.out.println("************** serializeAsField " + writer.getName());
                writer.serializeAsField(pojo, gen, prov);
            }

            @Override
            public void serializeAsElement(Object elementValue, JsonGenerator gen, SerializerProvider prov,
                    PropertyWriter writer)
                    throws Exception {
                System.out.println("********** serializeAsElement " + writer.getName());
                writer.serializeAsElement(elementValue, gen, prov);

            }

            @Override
            public void depositSchemaProperty(PropertyWriter writer, ObjectNode propertiesNode, SerializerProvider provider)
                    throws JsonMappingException {
                writer.depositSchemaProperty(propertiesNode, provider);

            }

            @Override
            public void depositSchemaProperty(PropertyWriter writer, JsonObjectFormatVisitor objectVisitor,
                    SerializerProvider provider)
                    throws JsonMappingException {
                writer.depositSchemaProperty(objectVisitor, provider);
            }

        };
        ObjectMapper mapper = new ObjectMapper();
        mapper.addMixIn(Object.class, GlobalFilterMixIn.class);

        SimpleFilterProvider filterProvider = new SimpleFilterProvider()
                .setDefaultFilter(filter)
                .setFailOnUnknownId(false); // Prevents crashing on types with specific explicit filters

        mapper.setFilterProvider(filterProvider);
        mapper.writer(filterProvider).writeValueAsString(new Pojo("Bill"));

    }

}
