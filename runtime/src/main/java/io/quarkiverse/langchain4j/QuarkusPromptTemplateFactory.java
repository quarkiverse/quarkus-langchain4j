package io.quarkiverse.langchain4j;

import java.util.Map;

import dev.langchain4j.spi.prompt.PromptTemplateFactory;
import io.quarkus.qute.Engine;
import io.quarkus.qute.ParserHelper;
import io.quarkus.qute.ParserHook;
import io.quarkus.qute.TemplateInstance;

public class QuarkusPromptTemplateFactory implements PromptTemplateFactory {

    Engine ENGINE = Engine.builder().addDefaults().addParserHook(new ParserHook() {
        @Override
        public void beforeParsing(ParserHelper parserHelper) {
            parserHelper.addContentFilter(contents -> contents.replace("{{", "{"));
            parserHelper.addContentFilter(contents -> contents.replace("}}", "}"));
        }
    }).build();

    @Override
    public Template create(Input input) {
        return new QuteTemplate(ENGINE.parse(input.getTemplate()));
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
