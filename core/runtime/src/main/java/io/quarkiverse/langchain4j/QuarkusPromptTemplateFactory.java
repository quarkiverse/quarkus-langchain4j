package io.quarkiverse.langchain4j;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.function.Supplier;

import dev.langchain4j.spi.ServiceHelper;
import dev.langchain4j.spi.prompt.PromptTemplateFactory;
import io.quarkiverse.langchain4j.spi.PromptTemplateFactoryContentFilterProvider;
import io.quarkus.arc.Arc;
import io.quarkus.arc.ArcContainer;
import io.quarkus.arc.impl.LazyValue;
import io.quarkus.qute.Engine;
import io.quarkus.qute.EngineBuilder;
import io.quarkus.qute.ParserHelper;
import io.quarkus.qute.ParserHook;
import io.quarkus.qute.TemplateInstance;

public class QuarkusPromptTemplateFactory implements PromptTemplateFactory {

    private static final AtomicReference<LazyValue<Engine>> engineLazyValue = new AtomicReference<>();

    public QuarkusPromptTemplateFactory() {
        engineLazyValue.set(new LazyValue<>(new Supplier<Engine>() {
            @Override
            public Engine get() {
                ArcContainer container = Arc.container();
                EngineBuilder builder = container.instance(Engine.class).get().newBuilder()
                        .addParserHook(new MustacheTemplateVariableStyleParserHook());
                // fire event to call DebugQuteEngineObserver#configureEngine(@Observes EngineBuilder builder, QuteConfig config)
                // to track the langchain4j engine builder with Qute debugger
                // see https://github.com/quarkusio/quarkus/blob/84414f0fd571881f5601c1dc73a0f43c07080a87/extensions/qute/runtime/src/main/java/io/quarkus/qute/runtime/debug/DebugQuteEngineObserver.java#L41
                container.beanManager().getEvent().fire(builder);
                return builder.build();
            }
        }));
    }

    public static void clear() {
        LazyValue<Engine> lazyValue = engineLazyValue.get();
        if (lazyValue != null) {
            lazyValue.clear();
        }
    }

    @Override
    public Template create(Input input) {
        String javaElementUri = input.getName();
        return new QuteTemplate(engineLazyValue.get().get().parse(input.getTemplate(), null, javaElementUri));
    }

    public static class MustacheTemplateVariableStyleParserHook implements ParserHook {

        private static final List<Function<String, String>> customContentFilters;
        static {
            List<Function<String, String>> ccf = new ArrayList<>();
            Collection<PromptTemplateFactoryContentFilterProvider> promptTemplateFactoryContentFilterProviders = ServiceHelper
                    .loadFactories(PromptTemplateFactoryContentFilterProvider.class);
            for (PromptTemplateFactoryContentFilterProvider p : promptTemplateFactoryContentFilterProviders) {
                ccf.add(p.getContentFilter());
            }
            customContentFilters = Collections.unmodifiableList(ccf);
        }

        @Override
        public void beforeParsing(ParserHelper parserHelper) {
            parserHelper.addContentFilter(new Function<String, String>() {
                @Override
                public String apply(String contents) {
                    return contents.replace("{{", "{").replace("}}", "}");
                }
            });
            for (Function<String, String> customContentFilter : customContentFilters) {
                parserHelper.addContentFilter(customContentFilter);
            }
        }
    }

    private static class QuteTemplate implements Template {

        private final io.quarkus.qute.Template template;

        private QuteTemplate(io.quarkus.qute.Template template) {
            this.template = template;
        }

        @Override
        public String render(Map<String, Object> vars) {
            TemplateInstance templateInstance = template.instance();
            for (var entry : vars.entrySet()) {
                templateInstance = templateInstance.data(entry.getKey(), entry.getValue());
            }
            return templateInstance.render();
        }
    }

}
