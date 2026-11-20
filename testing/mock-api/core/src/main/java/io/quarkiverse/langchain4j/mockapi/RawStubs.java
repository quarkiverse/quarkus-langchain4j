package io.quarkiverse.langchain4j.mockapi;

import java.time.Duration;
import java.util.Arrays;

import io.quarkiverse.langchain4j.mockapi.internal.AdminClient;
import io.quarkiverse.langchain4j.mockapi.internal.model.RawStub;

/**
 * Registers raw HTTP stubs that bypass the dialects.
 */
public final class RawStubs {

    private final AdminClient admin;

    RawStubs(AdminClient admin) {
        this.admin = admin;
    }

    public Builder on(String method, String pathGlob) {
        return new Builder(method, pathGlob);
    }

    public final class Builder {
        private final String method;
        private final String pathGlob;
        private String bodyJsonPointer;
        private String bodyExpected;
        private Duration delay;

        private Builder(String method, String pathGlob) {
            this.method = method;
            this.pathGlob = pathGlob;
        }

        /**
         * Only matches requests whose JSON body has the text {@code expected} at {@code jsonPointer}.
         */
        public Builder whenBody(String jsonPointer, String expected) {
            this.bodyJsonPointer = jsonPointer;
            this.bodyExpected = expected;
            return this;
        }

        /**
         * Delays every response of this stub that has no delay of its own.
         */
        public Builder delay(Duration delay) {
            this.delay = delay;
            return this;
        }

        public void reply(int status, String jsonBody) {
            replies(RawResponse.json(status, jsonBody));
        }

        /**
         * Sends the responses in order, one per matching request; the last one repeats.
         */
        public void replies(RawResponse... inOrder) {
            admin.postJson("/_admin/raw-stubs", new RawStub(method, pathGlob, bodyJsonPointer, bodyExpected,
                    Arrays.stream(inOrder).map(r -> delay != null && r.delay() == null ? r.delay(delay) : r).toList()));
        }
    }
}
