package io.quarkiverse.langchain4j.bedrock.deployment;

import static com.github.tomakehurst.wiremock.core.WireMockApp.FILES_ROOT;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;

import java.util.List;

import org.jboss.logging.Logger;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.common.Notifier;
import com.github.tomakehurst.wiremock.common.SingleRootFileSource;
import com.github.tomakehurst.wiremock.extension.responsetemplating.ResponseTemplateTransformer;
import com.github.tomakehurst.wiremock.extension.responsetemplating.TemplateEngine;

public class BedrockTestBase {

    private static final Logger LOGGER = Logger.getLogger(BedrockTestBase.class);

    public static final int WM_PORT = 8089;

    protected static WireMockServer wireMockServer;

    @BeforeAll
    static void beforeAll() {
        wireMockServer = new WireMockServer(options()
                .port(WM_PORT)
                .notifier(createNotifier(true))
                .extensions(new ResponseTemplateTransformer(TemplateEngine.defaultTemplateEngine(), false,
                        new SingleRootFileSource("src/test/resources").child(FILES_ROOT), List.of())));
        WireMock.configureFor(new WireMock(wireMockServer));
        wireMockServer.start();
    }

    @AfterAll
    static void afterAll() {
        wireMockServer.stop();
    }

    @BeforeEach
    void setup() {
        wireMockServer.resetAll();
    }

    private static Notifier createNotifier(final boolean verbose) {
        return new Notifier() {

            @Override
            public void info(final String s) {
                if (verbose) {
                    LOGGER.info(s);
                }
            }

            @Override
            public void error(final String s) {
                LOGGER.error(s);
            }

            @Override
            public void error(final String s, final Throwable throwable) {
                LOGGER.error(s, throwable);
            }
        };
    }
}
