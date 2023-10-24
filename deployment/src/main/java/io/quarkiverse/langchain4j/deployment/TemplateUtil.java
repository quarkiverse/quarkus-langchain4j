package io.quarkiverse.langchain4j.deployment;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import io.quarkiverse.langchain4j.QuarkusPromptTemplateFactory;
import io.quarkus.qute.Engine;
import io.quarkus.qute.Expression;
import io.quarkus.qute.Template;

class TemplateUtil {

    static List<List<Expression.Part>> parts(String templateStr) {
        Template template = Holder.ENGINE.parse(templateStr);
        List<Expression> expressions = template.getExpressions();
        if (expressions.isEmpty()) {
            return Collections.emptyList();
        }
        return expressions.stream().map(Expression::getParts).collect(Collectors.toList());
    }

    private static class Holder {
        private static final Engine ENGINE = Engine.builder().addDefaults()
                .addParserHook(new QuarkusPromptTemplateFactory.MustacheTemplateVariableStyleParserHook()).build();
    }
}
