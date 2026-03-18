package io.quarkiverse.langchain4j.chatscopes.websocket.internal;

import jakarta.interceptor.AroundInvoke;
import jakarta.interceptor.Interceptor;
import jakarta.interceptor.InvocationContext;

import org.commonmark.node.Node;
import org.commonmark.parser.Parser;
import org.commonmark.renderer.html.HtmlRenderer;

import dev.langchain4j.service.Result;
import io.quarkiverse.langchain4j.chatscopes.MarkdownToHtml;

@Interceptor
@MarkdownToHtml
public class MarkdownToHtmlInterceptor {
    static Parser parser;
    static HtmlRenderer renderer;

    static {
        parser = Parser.builder().build();
        renderer = HtmlRenderer.builder().build();
    }

    public static String markdownToHtml(String markdown) {
        try {
            Node document = parser.parse(markdown);
            return "<p>" + renderer.render(document) + "</p>";
        } catch (Exception e) {
            return markdown;
        }
    }

    @AroundInvoke
    public Object invoke(InvocationContext ctx) throws Exception {
        Object value = ctx.proceed();
        if (value instanceof String) {
            return markdownToHtml((String) value);
        } else if (value instanceof Result) {
            Result result = (Result) value;
            if (result.content() == null || !(result.content() instanceof String)) {
                return value;
            }
            String content = (String) result.content();
            content = markdownToHtml(content);
            return Result.builder().content(content).build();
        } else {
            return value;
        }
    }

}
