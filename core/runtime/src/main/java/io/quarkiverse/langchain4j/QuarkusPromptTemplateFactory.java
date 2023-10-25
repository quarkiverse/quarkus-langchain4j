package io.quarkiverse.langchain4j;

import java.util.Map;
import java.util.function.Function;
import java.util.function.Supplier;

import dev.langchain4j.spi.prompt.PromptTemplateFactory;
import io.quarkus.arc.Arc;
import io.quarkus.arc.impl.LazyValue;
import io.quarkus.qute.Engine;
import io.quarkus.qute.ParserHelper;
import io.quarkus.qute.ParserHook;
import io.quarkus.qute.TemplateInstance;

public class QuarkusPromptTemplateFactory implements PromptTemplateFactory {

    private final LazyValue<Engine> engineLazyValue;

    public QuarkusPromptTemplateFactory() {
        engineLazyValue = new LazyValue<>(new Supplier<Engine>() {
            @Override
            public Engine get() {
                return Arc.container().instance(Engine.class).get().newBuilder()
                        .addParserHook(new MustacheTemplateVariableStyleParserHook()).build();
            }
        });
    }

    @Override
    public Template create(Input input) {
        return new QuteTemplate(engineLazyValue.get().parse(input.getTemplate()));
    }

    public static class MustacheTemplateVariableStyleParserHook implements ParserHook {

        @Override
        public void beforeParsing(ParserHelper parserHelper) {
            parserHelper.addContentFilter(new Function<String, String>() {
                @Override
                public String apply(String contents) {
                    return contents.replace("{{", "{").replace("}}", "}");
                }
            });
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
