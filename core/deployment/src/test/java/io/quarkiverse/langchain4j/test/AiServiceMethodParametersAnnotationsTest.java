package io.quarkiverse.langchain4j.test;

import static io.restassured.RestAssured.get;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;

import java.util.function.Consumer;
import java.util.function.Supplier;

import jakarta.validation.constraints.Email;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;

import org.jboss.jandex.DotName;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.UserMessage;
import io.quarkiverse.langchain4j.RegisterAiService;
import io.quarkiverse.langchain4j.deployment.items.MethodParameterAllowedAnnotationsBuildItem;
import io.quarkiverse.langchain4j.deployment.items.MethodParameterIgnoredAnnotationsBuildItem;
import io.quarkus.builder.BuildChainBuilder;
import io.quarkus.builder.BuildContext;
import io.quarkus.builder.BuildStep;
import io.quarkus.test.QuarkusUnitTest;

/**
 * This class aims at testing which ai service method parameters are allowed to be used as prompt template variable.
 * Allowance is driven by annotations hold by the method parameter.
 */
public class AiServiceMethodParametersAnnotationsTest {

    private static final DotName ANNOTATION_INCLUDED_BY_BUILD_ITEM = DotName.createSimple(AnnotationIncludedByBuildItem.class);
    private static final DotName ANNOTATION_IGNORED_BY_BUILD_ITEM = DotName.createSimple(AnnotationIgnoredByBuildItem.class);

    public @interface AnnotationIncludedByBuildItem {
    }

    public @interface AnnotationIgnoredByBuildItem {
    }

    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addClasses(MyResource.class, MyService.class, MirrorModelSupplier.class))
            .addBuildChainCustomizer(buildCustomizer());

    protected static Consumer<BuildChainBuilder> buildCustomizer() {
        return new Consumer<BuildChainBuilder>() {
            @Override
            public void accept(BuildChainBuilder builder) {
                builder.addBuildStep(new BuildStep() {
                    @Override
                    public void execute(BuildContext context) {
                        context.produce(new MethodParameterAllowedAnnotationsBuildItem(
                                anno -> {
                                    // Any parameter annotated with @AnnotationIncludedByBuildItem could be used as template variable
                                    return ANNOTATION_INCLUDED_BY_BUILD_ITEM.equals(anno.name());
                                }));
                        context.produce(new MethodParameterIgnoredAnnotationsBuildItem(
                                anno -> {
                                    // Any @AnnotationIgnoredByBuildItem should not influence the parameter allowance as template variable
                                    return ANNOTATION_IGNORED_BY_BUILD_ITEM.equals(anno.name());
                                }));
                    }
                })
                        .produces(MethodParameterAllowedAnnotationsBuildItem.class)
                        .produces(MethodParameterIgnoredAnnotationsBuildItem.class)
                        .build();
            }
        };
    }

    public static class MirrorModelSupplier implements Supplier<ChatLanguageModel> {
        @Override
        public ChatLanguageModel get() {
            return (messages) -> new Response<>(new AiMessage(messages.get(0).text()));
        }
    }

    @Path("/test")
    static class MyResource {

        private final MyService service;

        MyResource(MyService service) {
            this.service = service;
        }

        @GET
        @Path("validAnnotationCombinationsAreIncluded")
        public String validAnnotationCombinationsAreIncluded() {
            return service.validAnnotationCombinationsAreIncluded("arg1", "arg2", "arg3", "arg4", "arg5", "arg6");
        }

        @GET
        @Path("deprecatedIsNotIncluded")
        public String deprecatedIsNotIncluded() {
            return service.deprecatedIsNotIncluded("arg1");
        }

        @GET
        @Path("deprecatedEmailIsNotIncluded")
        public String deprecatedEmailIsNotIncluded() {
            return service.deprecatedEmailIsNotIncluded("arg1");
        }

        @GET
        @Path("annotationIncludedFromBuildItemIsIncluded")
        public String annotationIncludedFromBuildItemIsIncluded() {
            return service.annotationIncludedFromBuildItemIsIncluded("arg1");
        }

        @GET
        @Path("annotationIgnoredFromBuildItemIsIncluded")
        public String annotationIgnoredFromBuildItemIsIncluded() {
            return service.annotationIgnoredFromBuildItemIsIncluded("arg1");
        }
    }

    @RegisterAiService(chatLanguageModelSupplier = MirrorModelSupplier.class, chatMemoryProviderSupplier = RegisterAiService.NoChatMemoryProviderSupplier.class)
    interface MyService {
        @UserMessage("@∅={none}, @Email={email}, @MemoryId={memId}, @Email@MemoryId={emailAndMemId}, @Deprecated@MemoryId={deprecatedAndMemId}, @Email@Deprecated@MemoryId={emailDeprecatedAndMemId}")
        String validAnnotationCombinationsAreIncluded(
                String none, @Email String email, @MemoryId String memId,
                @Email @MemoryId String emailAndMemId, @Deprecated @MemoryId String deprecatedAndMemId,
                @Email @Deprecated @MemoryId String emailDeprecatedAndMemId);

        @UserMessage("@Deprecated={deprecated}")
        String deprecatedIsNotIncluded(@Deprecated String deprecated);

        @UserMessage("@Deprecated@Email={deprecatedEmail}")
        String deprecatedEmailIsNotIncluded(@Deprecated @Email String deprecatedEmail);

        @UserMessage("@AnnotationIncludedByBuildItem={included}")
        String annotationIncludedFromBuildItemIsIncluded(@AnnotationIncludedByBuildItem String included);

        @UserMessage("@AnnotationIgnoredByBuildItem={ignored}")
        String annotationIgnoredFromBuildItemIsIncluded(@AnnotationIgnoredByBuildItem String ignored);
    }

    @Test
    void validAnnotationCombinationsShouldBeIncluded() {
        get("test/validAnnotationCombinationsAreIncluded")
                .then()
                .statusCode(200)
                .body(equalTo(
                        "@∅=arg1, @Email=arg2, @MemoryId=arg3, @Email@MemoryId=arg4, @Deprecated@MemoryId=arg5, @Email@Deprecated@MemoryId=arg6"));
    }

    @Test
    void deprecatedShouldNotBeIncluded() {
        get("test/deprecatedIsNotIncluded")
                .then()
                .statusCode(500)
                .body(containsString("io.quarkus.qute.TemplateException"));
    }

    @Test
    void deprecatedEmailShouldNotBeIncluded() {
        get("test/deprecatedEmailIsNotIncluded")
                .then()
                .statusCode(500)
                .body(containsString("io.quarkus.qute.TemplateException"));
    }

    @Test
    void annotationIncludedFromBuildItemShouldBeIncluded() {
        get("test/annotationIncludedFromBuildItemIsIncluded")
                .then()
                .statusCode(200)
                .body(equalTo("@AnnotationIncludedByBuildItem=arg1"));
    }

    @Test
    void annotationIgnoredFromBuildItemShouldBeIncluded() {
        get("test/annotationIgnoredFromBuildItemIsIncluded")
                .then()
                .statusCode(200)
                .body(equalTo("@AnnotationIgnoredByBuildItem=arg1"));
    }
}
