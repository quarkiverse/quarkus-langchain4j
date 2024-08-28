package io.quarkiverse.langchain4j.deployment;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import org.jboss.jandex.AnnotationInstance;
import org.jboss.jandex.AnnotationValue;

import io.quarkiverse.langchain4j.QuarkusPromptTemplateFactory;
import io.quarkus.qute.Engine;
import io.quarkus.qute.Expression;
import io.quarkus.qute.Template;

public class TemplateUtil {

    static List<List<Expression.Part>> parts(String templateStr) {
        Template template = Holder.ENGINE.parse(templateStr);
        List<Expression> expressions = template.getExpressions();
        if (expressions.isEmpty()) {
            return Collections.emptyList();
        }
        return expressions.stream().map(Expression::getParts).collect(Collectors.toList());
    }

    /**
     * Meant to be called with instances of {@link dev.langchain4j.service.SystemMessage} or
     * {@link dev.langchain4j.service.UserMessage}
     *
     * @return the String value of the template or an empty string if not specified
     */
    public static String getTemplateFromAnnotationInstance(AnnotationInstance instance) {

        if (instance == null) {
            return "";
        }

        AnnotationValue fromResourceValue = instance.value("fromResource");
        if (fromResourceValue != null) {
            String fromResource = fromResourceValue.asString();
            if (!fromResource.startsWith("/")) {
                fromResource = "/" + fromResource;

            }
            try (InputStream is = Thread.currentThread().getContextClassLoader().getResourceAsStream(fromResource)) {
                if (is != null) {
                    return new String(is.readAllBytes());
                } else {
                    throw new FileNotFoundException("Resource not found: " + fromResource);
                }
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        } else {
            AnnotationValue valueValue = instance.value();
            if (valueValue != null) {
                AnnotationValue delimiterValue = instance.value("delimiter");
                String delimiter = delimiterValue != null ? delimiterValue.asString() : AiServicesProcessor.DEFAULT_DELIMITER;
                return String.join(delimiter, valueValue.asStringArray());
            }

        }
        return "";
    }

    private static class Holder {
        private static final Engine ENGINE = Engine.builder().addDefaults()
                .addParserHook(new QuarkusPromptTemplateFactory.MustacheTemplateVariableStyleParserHook()).build();
    }
}
