package io.quarkiverse.langchain4j.anthropic.deployment;

import static com.github.tomakehurst.wiremock.client.WireMock.absent;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.not;
import static com.github.tomakehurst.wiremock.client.WireMock.okForContentType;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;
import static org.awaitility.Awaitility.await;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;

import jakarta.inject.Inject;
import jakarta.ws.rs.core.MediaType;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.StreamingResponseHandler;
import dev.langchain4j.model.anthropic.AnthropicStreamingChatModel;
import dev.langchain4j.model.chat.StreamingChatLanguageModel;
import dev.langchain4j.model.output.Response;
import io.quarkus.arc.ClientProxy;
import io.quarkus.test.QuarkusUnitTest;

class AnthropicStreamingChatLanguageModelSmokeTest extends AnthropicSmokeTest {
    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class))
            .overrideRuntimeConfigKey("quarkus.langchain4j.anthropic.api-key", API_KEY)
            .overrideRuntimeConfigKey("quarkus.langchain4j.anthropic.log-requests", "true")
            .overrideRuntimeConfigKey("quarkus.langchain4j.anthropic.log-responses", "true")
            .overrideRuntimeConfigKey("quarkus.langchain4j.anthropic.base-url", "http://localhost:%d".formatted(WIREMOCK_PORT));

    @Inject
    StreamingChatLanguageModel streamingChatModel;

    @Test
    void streaming() {
        assertThat(ClientProxy.unwrap(streamingChatModel))
                .isInstanceOf(AnthropicStreamingChatModel.class);

        // I got this stream directly from Claude on the command line
        // See https://docs.anthropic.com/claude/reference/messages-streaming#raw-http-stream-response
        var eventStream = """
                event: message_start
                data: {"type":"message_start","message":{"id":"msg_01Grp1T2X3zAkZETBiUgFsJK","type":"message","role":"assistant","content":[],"model":"claude-3-haiku-20240307","stop_reason":null,"stop_sequence":null,"usage":{"input_tokens":14,"output_tokens":1}}      }

                event: content_block_start
                data: {"type":"content_block_start","index":0,"content_block":{"type":"text","text":""}           }

                event: ping
                data: {"type": "ping"}

                event: content_block_delta
                data: {"type":"content_block_delta","index":0,"delta":{"type":"text_delta","text":"As"}        }

                event: content_block_delta
                data: {"type":"content_block_delta","index":0,"delta":{"type":"text_delta","text":" an"}               }

                event: content_block_delta
                data: {"type":"content_block_delta","index":0,"delta":{"type":"text_delta","text":" AI"}         }

                event: content_block_delta
                data: {"type":"content_block_delta","index":0,"delta":{"type":"text_delta","text":" language"}           }

                event: content_block_delta
                data: {"type":"content_block_delta","index":0,"delta":{"type":"text_delta","text":" model"}     }

                event: content_block_delta
                data: {"type":"content_block_delta","index":0,"delta":{"type":"text_delta","text":","}         }

                event: content_block_delta
                data: {"type":"content_block_delta","index":0,"delta":{"type":"text_delta","text":" I"}          }

                event: content_block_delta
                data: {"type":"content_block_delta","index":0,"delta":{"type":"text_delta","text":" don"}     }

                event: content_block_delta
                data: {"type":"content_block_delta","index":0,"delta":{"type":"text_delta","text":"'t"}       }

                event: content_block_delta
                data: {"type":"content_block_delta","index":0,"delta":{"type":"text_delta","text":" have"}   }

                event: content_block_delta
                data: {"type":"content_block_delta","index":0,"delta":{"type":"text_delta","text":" personal"}   }

                event: content_block_delta
                data: {"type":"content_block_delta","index":0,"delta":{"type":"text_delta","text":" feelings"}              }

                event: content_block_delta
                data: {"type":"content_block_delta","index":0,"delta":{"type":"text_delta","text":" or"}  }

                event: content_block_delta
                data: {"type":"content_block_delta","index":0,"delta":{"type":"text_delta","text":" emotions"}               }

                event: content_block_delta
                data: {"type":"content_block_delta","index":0,"delta":{"type":"text_delta","text":","}    }

                event: content_block_delta
                data: {"type":"content_block_delta","index":0,"delta":{"type":"text_delta","text":" but"}      }

                event: content_block_delta
                data: {"type":"content_block_delta","index":0,"delta":{"type":"text_delta","text":" I"}     }

                event: content_block_delta
                data: {"type":"content_block_delta","index":0,"delta":{"type":"text_delta","text":"'m"}           }

                event: content_block_delta
                data: {"type":"content_block_delta","index":0,"delta":{"type":"text_delta","text":" here"}   }

                event: content_block_delta
                data: {"type":"content_block_delta","index":0,"delta":{"type":"text_delta","text":" and"}}

                event: content_block_delta
                data: {"type":"content_block_delta","index":0,"delta":{"type":"text_delta","text":" ready"}            }

                event: content_block_delta
                data: {"type":"content_block_delta","index":0,"delta":{"type":"text_delta","text":" to"}     }

                event: content_block_delta
                data: {"type":"content_block_delta","index":0,"delta":{"type":"text_delta","text":" assist"}        }

                event: content_block_delta
                data: {"type":"content_block_delta","index":0,"delta":{"type":"text_delta","text":" you"}        }

                event: content_block_delta
                data: {"type":"content_block_delta","index":0,"delta":{"type":"text_delta","text":" to"}}

                event: content_block_delta
                data: {"type":"content_block_delta","index":0,"delta":{"type":"text_delta","text":" the"}             }

                event: content_block_delta
                data: {"type":"content_block_delta","index":0,"delta":{"type":"text_delta","text":" best"} }

                event: content_block_delta
                data: {"type":"content_block_delta","index":0,"delta":{"type":"text_delta","text":" of"}}

                event: content_block_delta
                data: {"type":"content_block_delta","index":0,"delta":{"type":"text_delta","text":" my"}             }

                event: content_block_delta
                data: {"type":"content_block_delta","index":0,"delta":{"type":"text_delta","text":" abilities"}              }

                event: content_block_delta
                data: {"type":"content_block_delta","index":0,"delta":{"type":"text_delta","text":"."}      }

                event: content_block_delta
                data: {"type":"content_block_delta","index":0,"delta":{"type":"text_delta","text":" How"}           }

                event: content_block_delta
                data: {"type":"content_block_delta","index":0,"delta":{"type":"text_delta","text":" can"}          }

                event: content_block_delta
                data: {"type":"content_block_delta","index":0,"delta":{"type":"text_delta","text":" I"}       }

                event: content_block_delta
                data: {"type":"content_block_delta","index":0,"delta":{"type":"text_delta","text":" help"}       }

                event: content_block_delta
                data: {"type":"content_block_delta","index":0,"delta":{"type":"text_delta","text":" you"}             }

                event: content_block_delta
                data: {"type":"content_block_delta","index":0,"delta":{"type":"text_delta","text":" today"} }

                event: content_block_delta
                data: {"type":"content_block_delta","index":0,"delta":{"type":"text_delta","text":"?"}    }

                event: content_block_stop
                data: {"type":"content_block_stop","index":0            }

                event: message_delta
                data: {"type":"message_delta","delta":{"stop_reason":"end_turn","stop_sequence":null},"usage":{"output_tokens":41}  }

                event: message_stop
                data: {"type":"message_stop"           }

                """;

        wireMockServer.stubFor(
                post(urlPathEqualTo("/messages"))
                        .withHeader("x-api-key", equalTo(API_KEY))
                        .withHeader("anthropic-version", not(absent()))
                        .willReturn(okForContentType(MediaType.SERVER_SENT_EVENTS, eventStream)));

        var streamingResponse = new AtomicReference<AiMessage>();
        streamingChatModel.generate("Hello, how are you today?", new StreamingResponseHandler<>() {
            @Override
            public void onNext(String token) {
            }

            @Override
            public void onError(Throwable error) {
                fail("Streaming failed: %s".formatted(error.getMessage()), error);
            }

            @Override
            public void onComplete(Response<AiMessage> response) {
                streamingResponse.set(response.content());
            }
        });

        await()
                .atMost(Duration.ofMinutes(1))
                .pollInterval(Duration.ofSeconds(2))
                .until(() -> streamingResponse.get() != null);

        assertThat(streamingResponse.get().text())
                .isNotNull()
                .isEqualTo(
                        "As an AI language model, I don't have personal feelings or emotions, but I'm here and ready to assist you to the best of my abilities. How can I help you today?");

        assertThat(wireMockServer.getAllServeEvents())
                .hasSize(1);

        var serveEvent = wireMockServer.getAllServeEvents().get(0);
        var loggedRequest = serveEvent.getRequest();

        assertThat(loggedRequest.getHeader("User-Agent"))
                .isEqualTo("Quarkus REST Client");

        var requestBody = """
                {
                  "model" : "claude-3-haiku-20240307",
                  "messages" : [ {
                    "role" : "user",
                    "content" : [ {
                      "type" : "text",
                      "text" : "Hello, how are you today?"
                    } ]
                  } ],
                  "system" : [ ],
                  "max_tokens" : 1024,
                  "stream" : true,
                  "top_k" : 40
                }""";
        assertThat(new String(loggedRequest.getBody()))
                .isEqualTo(requestBody)
                .contains(CHAT_MODEL_ID);

        wireMockServer.verify(
                1,
                postRequestedFor(urlPathEqualTo("/messages"))
                        .withHeader("x-api-key", equalTo(API_KEY))
                        .withHeader("anthropic-version", not(absent()))
                        .withRequestBody(equalToJson(requestBody)));
    }
}
