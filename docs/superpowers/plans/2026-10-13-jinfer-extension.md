# Jinfer (Qxotic) Extension Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the `llama3-java` Quarkus extension with a new `jinfer` extension that delegates to `com.qxotic:jinfer-langchain4j` and exposes chat, streaming-chat, embedding, scoring, transcription, and text-to-speech models.

**Architecture:** A standard quarkus-langchain4j model provider (runtime + deployment), mirroring `ollama`. Chat/streaming/embedding/scoring/transcription are wired through the core provider-candidate SPI; TTS (no core SPI) is produced as a self-contained synthetic bean. The recorder builds `Jinfer*Model` instances from config and caches the chat model so streaming shares its loaded weights; all models are `AutoCloseable` and closed on shutdown.

**Tech Stack:** Java 25, Quarkus 3.40.0, LangChain4j 1.21.0, `com.qxotic:jinfer-*` 0.3.1, SmallRye Config, Maven.

**Spec:** `docs/superpowers/specs/2026-10-13-jinfer-extension-design.md`

## Global Constraints

- JDK **25** required to build/run the module. Build with `JAVA_HOME=/home/gandrian/.sdkman/candidates/java/25.0.3-tem`. `maven.compiler.release=25`.
- Module is gated behind a `jdk25-plus` Maven profile; the main build on lower JDKs must not attempt it.
- Required JVM flags at runtime: `--add-modules jdk.incubator.vector` and `--enable-native-access=ALL-UNNAMED`. **No `--enable-preview`** (jinfer does not use preview).
- Base Java package: `io.quarkiverse.langchain4j.jinfer`. Constants `PROVIDER = "jinfer"`, `FEATURE = "langchain4j-jinfer"`.
- Config root: `quarkus.langchain4j.jinfer.*` (default + `@ModelName` named configs).
- Versions governed by importing `com.qxotic:jinfer-bom:0.3.1`; add `<jinfer.version>0.3.1</jinfer.version>` to the root pom. LangChain4j is already `1.21.0` in the repo — do not change it.
- Runtime deps: `jinfer-langchain4j` + `jinfer-models-all` (umbrella port) + `jam-vector` (runtime scope). Do not add `jam-native` (needs a native lib).
- jinfer builder parameter types are boxed where noted: `temperature(Double)`, `topP(Double)`, `topK(Integer)`, `minP(Double)`, `maxOutputTokens(Integer)`, `seed(Long)`; primitives: `contextCapacity(int)`, `maxReasoningTokens(int)`, `speed(double)`, `maxInputChars(int)`. Embedding has **no** `dimensions()` builder.
- Commit frequently (per task). Work on a dedicated branch, not `main`.

## Review Focus

- **No model configured for an enabled type** → the model builder must fail fast at bean creation with a clear message naming the config key (e.g. `quarkus.langchain4j.jinfer.chat-model.model`), not a jinfer NPE. Pinned in Task 3 (chat), re-used by Tasks 4–7.
- **`enable-integration=false`** → chat/streaming/embedding/scoring/transcription return the LangChain4j `Disabled*` model; TTS (no `Disabled*` variant) returns a model that throws a clear "integration disabled" message on use. Pinned in Task 3 and Task 7.
- **Both `model` and `model-path` set** → `model-path` (local file) wins; no download attempted. Pinned in Task 3.
- **Streaming + chat both injected** → only one set of weights is loaded (streaming shares the cached chat model), and shutdown closes it exactly once. Pinned in Task 3.
- **Named config (`@ModelName("x")`) for TTS** → the TTS bean for name `x` resolves from `quarkus.langchain4j.jinfer."x".tts-model.*` even though TTS has no core SPI. Pinned in Task 7.

---

## Task 1: Module scaffold + reactor/BOM wiring (empty extension builds on JDK 25)

**Files:**
- Create: `model-providers/jinfer/pom.xml`
- Create: `model-providers/jinfer/runtime/pom.xml`
- Create: `model-providers/jinfer/deployment/pom.xml`
- Create: `model-providers/jinfer/runtime/src/main/resources/META-INF/quarkus-extension.yaml`
- Create: `model-providers/jinfer/runtime/src/main/resources/META-INF/beans.xml` (empty file)
- Modify: `model-providers/pom.xml` (add `jinfer` under a `jdk25-plus` profile)
- Modify: `pom.xml` (root: `jinfer.version`, `jinfer-bom` import, BOM entry)

**Interfaces:**
- Produces: Maven artifacts `quarkus-langchain4j-jinfer` (runtime), `quarkus-langchain4j-jinfer-deployment` (deployment), parent `quarkus-langchain4j-jinfer-parent`.

- [ ] **Step 1: Parent pom** — `model-providers/jinfer/pom.xml`:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <parent>
        <groupId>io.quarkiverse.langchain4j</groupId>
        <artifactId>quarkus-langchain4j-parent</artifactId>
        <version>999-SNAPSHOT</version>
        <relativePath>../../pom.xml</relativePath>
    </parent>
    <artifactId>quarkus-langchain4j-jinfer-parent</artifactId>
    <name>Quarkus LangChain4j - Jinfer - Parent</name>
    <packaging>pom</packaging>
    <properties>
        <maven.compiler.release>25</maven.compiler.release>
    </properties>
    <modules>
        <module>deployment</module>
        <module>runtime</module>
    </modules>
</project>
```

(Verify the parent `<relativePath>` and `<version>` against a sibling, e.g. `model-providers/ollama/pom.xml`, and match exactly.)

- [ ] **Step 2: Runtime pom** — `model-providers/jinfer/runtime/pom.xml`. Model it on `model-providers/ollama/runtime/pom.xml` for the `quarkus-extension-maven-plugin` + `maven-compiler-plugin` structure, with these dependencies and compiler settings:

```xml
<dependencies>
    <dependency>
        <groupId>io.quarkus</groupId>
        <artifactId>quarkus-arc</artifactId>
    </dependency>
    <dependency>
        <groupId>io.quarkiverse.langchain4j</groupId>
        <artifactId>quarkus-langchain4j-core</artifactId>
        <version>${project.version}</version>
    </dependency>
    <dependency>
        <groupId>com.qxotic</groupId>
        <artifactId>jinfer-langchain4j</artifactId>
    </dependency>
    <dependency>
        <groupId>com.qxotic</groupId>
        <artifactId>jinfer-models-all</artifactId>
    </dependency>
    <dependency>
        <groupId>com.qxotic</groupId>
        <artifactId>jam-vector</artifactId>
        <scope>runtime</scope>
    </dependency>
</dependencies>
```

Compiler plugin must set `<release>25</release>` and `--add-modules=jdk.incubator.vector`:

```xml
<plugin>
    <artifactId>maven-compiler-plugin</artifactId>
    <configuration>
        <release>25</release>
        <compilerArgs>
            <arg>--add-modules=jdk.incubator.vector</arg>
        </compilerArgs>
    </configuration>
</plugin>
```

The `quarkus-extension-maven-plugin` `<devMode>` block is added in Task 8 — for now include the plugin with only the `<deployment>` element (copy the shape from ollama).

- [ ] **Step 3: Deployment pom** — `model-providers/jinfer/deployment/pom.xml`, modeled on `model-providers/ollama/deployment/pom.xml`:

```xml
<dependencies>
    <dependency>
        <groupId>io.quarkiverse.langchain4j</groupId>
        <artifactId>quarkus-langchain4j-core-deployment</artifactId>
        <version>${project.version}</version>
    </dependency>
    <dependency>
        <groupId>io.quarkiverse.langchain4j</groupId>
        <artifactId>quarkus-langchain4j-jinfer</artifactId>
        <version>${project.version}</version>
    </dependency>
    <dependency>
        <groupId>io.quarkus</groupId>
        <artifactId>quarkus-junit5-internal</artifactId>
        <scope>test</scope>
    </dependency>
    <dependency>
        <groupId>org.assertj</groupId>
        <artifactId>assertj-core</artifactId>
        <version>${assertj.version}</version>
        <scope>test</scope>
    </dependency>
</dependencies>
```

Set `<release>25</release>` + `--add-modules=jdk.incubator.vector` in the compiler plugin and add the `quarkus-extension-processor` annotation-processor path (copy from ollama deployment pom).

- [ ] **Step 4: `quarkus-extension.yaml`** — `model-providers/jinfer/runtime/src/main/resources/META-INF/quarkus-extension.yaml`:

```yaml
name: "LangChain4j Jinfer"
artifact: "${project.groupId}:${project.artifactId}:${project.version}"
metadata:
  keywords:
    - "ai"
    - "jinfer"
    - "qxotic"
    - "llm"
  categories:
    - "ai"
  status:
    - "preview"
```

(Copy exact field set from `model-providers/ollama/runtime/src/main/resources/META-INF/quarkus-extension.yaml` and adapt name/keywords.)

- [ ] **Step 5: empty `beans.xml`** — create `model-providers/jinfer/runtime/src/main/resources/META-INF/beans.xml` as an empty file.

- [ ] **Step 6: Root pom wiring** — in `pom.xml`:
  - Add property near the other versions (alphabetical-ish, by the removed `jlama.version` location): `<jinfer.version>0.3.1</jinfer.version>`.
  - In `<dependencyManagement><dependencies>`, add the BOM import (before/after the langchain4j-bom import):

```xml
<dependency>
    <groupId>com.qxotic</groupId>
    <artifactId>jinfer-bom</artifactId>
    <version>${jinfer.version}</version>
    <type>pom</type>
    <scope>import</scope>
</dependency>
```

  - Add the extension BOM entry alongside the other `quarkus-langchain4j-*` entries:

```xml
<dependency>
    <groupId>io.quarkiverse.langchain4j</groupId>
    <artifactId>quarkus-langchain4j-jinfer</artifactId>
    <version>${project.version}</version>
</dependency>
```

- [ ] **Step 7: Register module** — in `model-providers/pom.xml`, add a `jdk25-plus` profile mirroring the existing `jdk21-plus`/`jdk22-plus` profiles:

```xml
<profile>
    <id>jdk25-plus</id>
    <activation>
        <jdk>[25,)</jdk>
    </activation>
    <modules>
        <module>jinfer</module>
    </modules>
</profile>
```

- [ ] **Step 8: Verify the empty module builds on JDK 25**

Run: `JAVA_HOME=/home/gandrian/.sdkman/candidates/java/25.0.3-tem ./mvnw -q -pl model-providers/jinfer,model-providers/jinfer/runtime,model-providers/jinfer/deployment -am install -DskipTests -Dno-format -ntp`
Expected: BUILD SUCCESS; the three jinfer artifacts install. Confirms BOM resolves `com.qxotic:*` and the reactor wiring is correct.

- [ ] **Step 9: Commit**

```bash
git add model-providers/jinfer model-providers/pom.xml pom.xml
git commit -m "feat(jinfer): scaffold jinfer extension module and BOM wiring"
```

---

## Task 2: Config interfaces

**Files:**
- Create: `model-providers/jinfer/runtime/src/main/java/io/quarkiverse/langchain4j/jinfer/runtime/config/LangChain4jJinferConfig.java`
- Create: `.../runtime/config/ChatModelConfig.java`
- Create: `.../runtime/config/EmbeddingModelConfig.java`
- Create: `.../runtime/config/ScoringModelConfig.java`
- Create: `.../runtime/config/TranscriptionModelConfig.java`
- Create: `.../runtime/config/TtsModelConfig.java`
- Create: `model-providers/jinfer/deployment/src/main/java/io/quarkiverse/langchain4j/jinfer/deployment/LangChain4jJinferBuildConfig.java`
- Create: `.../deployment/ChatModelBuildConfig.java` (+ EmbeddingModelBuildConfig, ScoringModelBuildConfig, TranscriptionModelBuildConfig, TtsModelBuildConfig)

**Interfaces:**
- Produces: `LangChain4jJinferConfig` (RUN_TIME, prefix `quarkus.langchain4j.jinfer`) with `defaultConfig()` + `namedConfig()` and nested `JinferConfig` exposing `enableIntegration()`, `chatModel()`, `embeddingModel()`, `scoringModel()`, `transcriptionModel()`, `ttsModel()`. `LangChain4jJinferBuildConfig` (BUILD_TIME, same prefix) exposing per-type `enabled()`.

- [ ] **Step 1: `LangChain4jJinferConfig`** — mirror `LangChain4jOllamaConfig` exactly (same annotations: `@ConfigRoot(phase = RUN_TIME)`, `@ConfigMapping(prefix = "quarkus.langchain4j.jinfer")`, `@WithParentName` default, `@ConfigDocSection @ConfigDocMapKey("model-name") @WithParentName @WithDefaults Map<String, JinferConfig> namedConfig()`). Nested `JinferConfig`:

```java
interface JinferConfig {
    /** Whether to enable the integration. Set to {@code false} to disable all models. */
    @WithDefault("true")
    Boolean enableIntegration();

    /** Chat model settings. */
    ChatModelConfig chatModel();

    /** Embedding model settings. */
    EmbeddingModelConfig embeddingModel();

    /** Scoring (rerank) model settings. */
    ScoringModelConfig scoringModel();

    /** Audio transcription model settings. */
    TranscriptionModelConfig transcriptionModel();

    /** Text-to-speech model settings. */
    TtsModelConfig ttsModel();
}
```

- [ ] **Step 2: `ChatModelConfig`** (`@ConfigGroup`):

```java
@ConfigGroup
public interface ChatModelConfig {
    /** HuggingFace/ModelScope GGUF model ref, e.g. {@code owner/repo:Q8_0}. Required unless model-path is set. */
    Optional<String> model();

    /** Path to a local GGUF file. Takes precedence over {@code model}; no download occurs. */
    Optional<Path> modelPath();

    /** Context window size in tokens (0 = model's full context). */
    OptionalInt contextCapacity();

    /** Sampling temperature. */
    @WithDefault("${quarkus.langchain4j.temperature:0.7}")
    Double temperature();

    /** Nucleus sampling top-p. */
    OptionalDouble topP();

    /** Top-k sampling. */
    OptionalInt topK();

    /** Min-p sampling. */
    OptionalDouble minP();

    /** Maximum number of tokens to generate. */
    OptionalInt maxOutputTokens();

    /** Maximum reasoning tokens (-1 uncaps). */
    OptionalInt maxReasoningTokens();

    /** RNG seed. */
    OptionalLong seed();

    /** Path to a read-only cached-prompt artifact. */
    Optional<Path> promptCache();

    /** Enable model "thinking"/reasoning. */
    Optional<Boolean> thinking();
}
```

- [ ] **Step 3: `EmbeddingModelConfig`**:

```java
@ConfigGroup
public interface EmbeddingModelConfig {
    Optional<String> model();
    Optional<Path> modelPath();
    OptionalInt contextCapacity();
}
```

- [ ] **Step 4: `ScoringModelConfig`**:

```java
@ConfigGroup
public interface ScoringModelConfig {
    Optional<String> model();
    Optional<Path> modelPath();
    OptionalInt contextCapacity();
    /** Optional reranker instruction (rejected if the reranker has no instruction slot). */
    Optional<String> instruction();
}
```

- [ ] **Step 5: `TranscriptionModelConfig`**:

```java
@ConfigGroup
public interface TranscriptionModelConfig {
    Optional<String> model();
    Optional<Path> modelPath();
}
```

- [ ] **Step 6: `TtsModelConfig`**:

```java
@ConfigGroup
public interface TtsModelConfig {
    Optional<String> model();
    Optional<Path> modelPath();
    /** Speech speed multiplier (> 0). */
    OptionalDouble speed();
    /** Maximum input characters. */
    OptionalInt maxInputChars();
}
```

- [ ] **Step 7: Build-time config** — `LangChain4jJinferBuildConfig` mirrors `LangChain4jOllamaOpenAiBuildConfig` (`@ConfigRoot(phase = BUILD_TIME)`, `@ConfigMapping(prefix = "quarkus.langchain4j.jinfer")`) but flat with all five `*BuildConfig` sub-groups:

```java
@ConfigRoot(phase = BUILD_TIME)
@ConfigMapping(prefix = "quarkus.langchain4j.jinfer")
public interface LangChain4jJinferBuildConfig {
    ChatModelBuildConfig chatModel();
    EmbeddingModelBuildConfig embeddingModel();
    ScoringModelBuildConfig scoringModel();
    TranscriptionModelBuildConfig transcriptionModel();
    TtsModelBuildConfig ttsModel();
}
```

Each `*BuildConfig` is the ollama `ChatModelBuildConfig` verbatim (just renamed), i.e. `@ConfigGroup` with `@ConfigDocDefault("true") Optional<Boolean> enabled();`.

- [ ] **Step 8: Compile check**

Run: `JAVA_HOME=/home/gandrian/.sdkman/candidates/java/25.0.3-tem ./mvnw -q -pl model-providers/jinfer/runtime,model-providers/jinfer/deployment -am compile -Dno-format -ntp`
Expected: BUILD SUCCESS.

- [ ] **Step 9: Commit**

```bash
git add model-providers/jinfer
git commit -m "feat(jinfer): add configuration interfaces"
```

---

## Task 3: Recorder + processor for chat & streaming (+ shutdown lifecycle, shared weights)

**Files:**
- Create: `model-providers/jinfer/runtime/src/main/java/io/quarkiverse/langchain4j/jinfer/runtime/JinferRecorder.java`
- Create: `model-providers/jinfer/runtime/src/main/java/io/quarkiverse/langchain4j/jinfer/runtime/JinferModels.java` (static registry + shared chat-model cache)
- Create: `model-providers/jinfer/deployment/src/main/java/io/quarkiverse/langchain4j/jinfer/deployment/JinferProcessor.java`
- Test: `model-providers/jinfer/deployment/src/test/java/io/quarkiverse/langchain4j/jinfer/deployment/JinferChatBeansTest.java`

**Interfaces:**
- Consumes: `LangChain4jJinferConfig` (Task 2).
- Produces: `JinferRecorder.chatModel(String) : Function<SyntheticCreationalContext<ChatModel>, ChatModel>`, `JinferRecorder.streamingChatModel(String) : Function<SyntheticCreationalContext<StreamingChatModel>, StreamingChatModel>`, `JinferRecorder.closeModelsOnShutdown(ShutdownContext)`. `JinferModels.registerForClose(AutoCloseable)`, `JinferModels.sharedChatModel(String, Supplier<JinferChatModel>)`, `JinferModels.closeAll()`.

- [ ] **Step 1: Write the failing test** — `JinferChatBeansTest`. Use `QuarkusUnitTest` with `enable-integration=false` so no model loads, and assert the `ChatModel` and `StreamingChatModel` beans are injectable and are the `Disabled*` types:

```java
package io.quarkiverse.langchain4j.jinfer.deployment;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.inject.Inject;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.DisabledChatModel;
import dev.langchain4j.model.chat.DisabledStreamingChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import io.quarkus.test.QuarkusUnitTest;

public class JinferChatBeansTest {

    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class))
            .overrideConfigKey("quarkus.langchain4j.jinfer.enable-integration", "false");

    @Inject
    ChatModel chatModel;

    @Inject
    StreamingChatModel streamingChatModel;

    @Test
    void beansAreProducedAndDisabled() {
        assertThat(chatModel).isInstanceOf(DisabledChatModel.class);
        assertThat(streamingChatModel).isInstanceOf(DisabledStreamingChatModel.class);
    }
}
```

- [ ] **Step 2: Run test, verify it fails**

Run: `JAVA_HOME=/home/gandrian/.sdkman/candidates/java/25.0.3-tem ./mvnw -q -pl model-providers/jinfer/deployment -am test -Dtest=JinferChatBeansTest -Dno-format -ntp`
Expected: FAIL — no `ChatModel` bean (unsatisfied dependency), because no recorder/processor exists yet.

- [ ] **Step 3: `JinferModels` registry** — holds the shutdown set and the shared chat-model cache (keyed by configName) so a streaming bean reuses the chat model's weights:

```java
package io.quarkiverse.langchain4j.jinfer.runtime;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

import com.qxotic.jinfer.langchain4j.JinferChatModel;

public final class JinferModels {

    private static final List<AutoCloseable> CLOSEABLES = new ArrayList<>();
    private static final Map<String, JinferChatModel> SHARED_CHAT = new ConcurrentHashMap<>();

    private JinferModels() {
    }

    public static synchronized void registerForClose(AutoCloseable closeable) {
        CLOSEABLES.add(closeable);
    }

    /** Returns the chat model for this config name, building (and registering for close) once. */
    public static JinferChatModel sharedChatModel(String configName, Supplier<JinferChatModel> factory) {
        return SHARED_CHAT.computeIfAbsent(configName, name -> {
            JinferChatModel model = factory.get();
            registerForClose(model);
            return model;
        });
    }

    public static synchronized void closeAll() {
        for (AutoCloseable c : CLOSEABLES) {
            try {
                c.close();
            } catch (Exception ignored) {
                // best-effort close on shutdown
            }
        }
        CLOSEABLES.clear();
        SHARED_CHAT.clear();
    }
}
```

- [ ] **Step 4: `JinferRecorder`** — chat + streaming + shutdown. Mirror `OllamaRecorder` structure (constructor takes `RuntimeValue<LangChain4jJinferConfig>`; `correspondingConfig` helper; `CHAT_MODEL_LISTENER_TYPE_LITERAL`). Chat builds + caches via `JinferModels.sharedChatModel`; streaming reuses it via `.streaming()`:

```java
package io.quarkiverse.langchain4j.jinfer.runtime;

import java.util.function.Function;
import java.util.stream.Collectors;

import jakarta.enterprise.inject.Instance;
import jakarta.enterprise.util.TypeLiteral;

import com.qxotic.jinfer.langchain4j.JinferChatModel;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.ChatModelListener;
import dev.langchain4j.model.chat.DisabledChatModel;
import dev.langchain4j.model.chat.DisabledStreamingChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import io.quarkiverse.langchain4j.jinfer.runtime.config.ChatModelConfig;
import io.quarkiverse.langchain4j.jinfer.runtime.config.LangChain4jJinferConfig;
import io.quarkiverse.langchain4j.runtime.NamedConfigUtil;
import io.quarkus.arc.SyntheticCreationalContext;
import io.quarkus.runtime.RuntimeValue;
import io.quarkus.runtime.ShutdownContext;
import io.quarkus.runtime.annotations.Recorder;

@Recorder
public class JinferRecorder {

    private static final TypeLiteral<Instance<ChatModelListener>> CHAT_MODEL_LISTENER_TYPE_LITERAL = new TypeLiteral<>() {
    };

    private final RuntimeValue<LangChain4jJinferConfig> runtimeConfig;

    public JinferRecorder(RuntimeValue<LangChain4jJinferConfig> runtimeConfig) {
        this.runtimeConfig = runtimeConfig;
    }

    public Function<SyntheticCreationalContext<ChatModel>, ChatModel> chatModel(String configName) {
        LangChain4jJinferConfig.JinferConfig config = correspondingConfig(configName);
        if (!config.enableIntegration()) {
            return ctx -> new DisabledChatModel();
        }
        ChatModelConfig chat = config.chatModel();
        return ctx -> JinferModels.sharedChatModel(configName,
                () -> buildChatModel(configName, chat,
                        ctx.getInjectedReference(CHAT_MODEL_LISTENER_TYPE_LITERAL)));
    }

    public Function<SyntheticCreationalContext<StreamingChatModel>, StreamingChatModel> streamingChatModel(
            String configName) {
        LangChain4jJinferConfig.JinferConfig config = correspondingConfig(configName);
        if (!config.enableIntegration()) {
            return ctx -> new DisabledStreamingChatModel();
        }
        ChatModelConfig chat = config.chatModel();
        return ctx -> JinferModels.sharedChatModel(configName,
                () -> buildChatModel(configName, chat,
                        ctx.getInjectedReference(CHAT_MODEL_LISTENER_TYPE_LITERAL)))
                .streaming();
    }

    public void closeModelsOnShutdown(ShutdownContext shutdownContext) {
        shutdownContext.addShutdownTask(JinferModels::closeAll);
    }

    private JinferChatModel buildChatModel(String configName, ChatModelConfig chat,
            Instance<ChatModelListener> listeners) {
        JinferChatModel.Builder builder = JinferChatModel.builder();
        applyModelRef(builder::modelPath, builder::model, configName, "chat-model",
                chat.model(), chat.modelPath());
        chat.contextCapacity().ifPresent(builder::contextCapacity);
        builder.temperature(chat.temperature());
        chat.topP().ifPresent(v -> builder.topP(v));
        chat.topK().ifPresent(v -> builder.topK(v));
        chat.minP().ifPresent(v -> builder.minP(v));
        chat.maxOutputTokens().ifPresent(v -> builder.maxOutputTokens(v));
        chat.maxReasoningTokens().ifPresent(builder::maxReasoningTokens);
        chat.seed().ifPresent(v -> builder.seed(v));
        chat.promptCache().ifPresent(builder::promptCache);
        chat.thinking().ifPresent(builder::thinking);
        builder.listeners(listeners.stream().collect(Collectors.toList()));
        return builder.build();
    }

    private LangChain4jJinferConfig.JinferConfig correspondingConfig(String configName) {
        return NamedConfigUtil.isDefault(configName)
                ? runtimeConfig.getValue().defaultConfig()
                : runtimeConfig.getValue().namedConfig().get(configName);
    }
}
```

Also add the shared helper used by every model type (put it in `JinferRecorder` as a `static` package-visible method so Tasks 4–7 reuse it):

```java
// In JinferRecorder (or a small JinferConfigSupport helper class):
static void applyModelRef(java.util.function.Consumer<java.nio.file.Path> pathSetter,
        java.util.function.Consumer<String> refSetter,
        String configName, String typeKey,
        java.util.Optional<String> model, java.util.Optional<java.nio.file.Path> modelPath) {
    if (modelPath.isPresent()) {
        pathSetter.accept(modelPath.get());
    } else if (model.isPresent()) {
        refSetter.accept(model.get());
    } else {
        throw new IllegalStateException("Jinfer " + typeKey + " requires either "
                + "'quarkus.langchain4j.jinfer." + qualify(configName, typeKey) + ".model' or '.model-path' to be set");
    }
}

static String qualify(String configName, String typeKey) {
    return NamedConfigUtil.isDefault(configName) ? typeKey : "\"" + configName + "\"." + typeKey;
}
```

Note: `applyModelRef` takes `builder::modelPath` and `builder::model` — this relies on `model-path` winning over `model` (Review Focus item 3).

- [ ] **Step 5: `JinferProcessor`** — feature, chat/streaming candidate, chat/streaming beans, shutdown registration. Mirror `OllamaProcessor.generateBeans` exactly for the chat/streaming blocks (from the captured pattern), using `JinferChatModel.Builder` / no customizer injection (jinfer has no `ModelBuilderCustomizer` builder type, so inject only `ChatModelListener`):

```java
package io.quarkiverse.langchain4j.jinfer.deployment;

import static io.quarkiverse.langchain4j.deployment.LangChain4jDotNames.CHAT_MODEL;
import static io.quarkiverse.langchain4j.deployment.LangChain4jDotNames.STREAMING_CHAT_MODEL;

import java.util.List;

import jakarta.enterprise.context.ApplicationScoped;

import org.jboss.jandex.AnnotationInstance;
import org.jboss.jandex.ClassType;
import org.jboss.jandex.ParameterizedType;
import org.jboss.jandex.Type;

import io.quarkiverse.langchain4j.ModelName;
import io.quarkiverse.langchain4j.deployment.DotNames;
import io.quarkiverse.langchain4j.deployment.items.ChatModelProviderCandidateBuildItem;
import io.quarkiverse.langchain4j.deployment.items.SelectedChatModelProviderBuildItem;
import io.quarkiverse.langchain4j.jinfer.runtime.JinferRecorder;
import io.quarkiverse.langchain4j.runtime.NamedConfigUtil;
import io.quarkus.arc.deployment.SyntheticBeanBuildItem;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.annotations.ExecutionTime;
import io.quarkus.deployment.annotations.Record;
import io.quarkus.deployment.builditem.FeatureBuildItem;
import io.quarkus.deployment.builditem.ShutdownContextBuildItem;

public class JinferProcessor {

    private static final String FEATURE = "langchain4j-jinfer";
    private static final String PROVIDER = "jinfer";

    @BuildStep
    FeatureBuildItem feature() {
        return new FeatureBuildItem(FEATURE);
    }

    @BuildStep
    void providerCandidates(BuildProducer<ChatModelProviderCandidateBuildItem> chatProducer,
            LangChain4jJinferBuildConfig config) {
        if (config.chatModel().enabled().isEmpty() || config.chatModel().enabled().get()) {
            chatProducer.produce(new ChatModelProviderCandidateBuildItem(PROVIDER));
        }
        // embedding/scoring/transcription candidates added in later tasks
    }

    @BuildStep
    @Record(ExecutionTime.RUNTIME_INIT)
    void generateChatBeans(JinferRecorder recorder,
            List<SelectedChatModelProviderBuildItem> selectedChat,
            ShutdownContextBuildItem shutdownContext,
            BuildProducer<SyntheticBeanBuildItem> beanProducer) {
        for (var selected : selectedChat) {
            if (!PROVIDER.equals(selected.getProvider())) {
                continue;
            }
            String configName = selected.getConfigName();

            var chat = SyntheticBeanBuildItem.configure(CHAT_MODEL)
                    .setRuntimeInit()
                    .defaultBean()
                    .scope(ApplicationScoped.class)
                    .addInjectionPoint(ParameterizedType.create(DotNames.CDI_INSTANCE,
                            new Type[] { ClassType.create(DotNames.CHAT_MODEL_LISTENER) }, null))
                    .createWith(recorder.chatModel(configName));
            addQualifierIfNecessary(chat, configName);
            beanProducer.produce(chat.done());

            var streaming = SyntheticBeanBuildItem.configure(STREAMING_CHAT_MODEL)
                    .setRuntimeInit()
                    .defaultBean()
                    .scope(ApplicationScoped.class)
                    .addInjectionPoint(ParameterizedType.create(DotNames.CDI_INSTANCE,
                            new Type[] { ClassType.create(DotNames.CHAT_MODEL_LISTENER) }, null))
                    .createWith(recorder.streamingChatModel(configName));
            addQualifierIfNecessary(streaming, configName);
            beanProducer.produce(streaming.done());
        }
    }

    @BuildStep
    @Record(ExecutionTime.RUNTIME_INIT)
    void registerShutdown(JinferRecorder recorder, ShutdownContextBuildItem shutdownContext) {
        recorder.closeModelsOnShutdown(shutdownContext);
    }

    static void addQualifierIfNecessary(SyntheticBeanBuildItem.ExtendedBeanConfigurator builder, String configName) {
        if (!NamedConfigUtil.isDefault(configName)) {
            builder.addQualifier(AnnotationInstance.builder(ModelName.class).add("value", configName).build());
        }
    }
}
```

(Confirm `ShutdownContextBuildItem` is the right way to obtain a `ShutdownContext` for a `@Record(RUNTIME_INIT)` method — it is passed as the recorder arg in Quarkus; adjust to the exact API used elsewhere in the repo, e.g. grep `ShutdownContextBuildItem` in `core`.)

- [ ] **Step 6: Run the test, verify it passes**

Run: `JAVA_HOME=/home/gandrian/.sdkman/candidates/java/25.0.3-tem ./mvnw -q -pl model-providers/jinfer/deployment -am test -Dtest=JinferChatBeansTest -Dno-format -ntp`
Expected: PASS.

- [ ] **Step 7: Commit**

```bash
git add model-providers/jinfer
git commit -m "feat(jinfer): chat and streaming chat models with shared weights and shutdown"
```

---

## Task 4: Embedding model

**Files:**
- Modify: `JinferRecorder.java` (add `embeddingModel`)
- Modify: `JinferProcessor.java` (candidate + bean)
- Test: `.../deployment/JinferEmbeddingBeanTest.java`

**Interfaces:**
- Produces: `JinferRecorder.embeddingModel(String) : Function<SyntheticCreationalContext<EmbeddingModel>, EmbeddingModel>`.

- [ ] **Step 1: Failing test** — assert an `EmbeddingModel` bean is produced and is `DisabledEmbeddingModel` with `enable-integration=false` (same structure as `JinferChatBeansTest`).
- [ ] **Step 2: Run, verify fails** (`-Dtest=JinferEmbeddingBeanTest`): FAIL (no bean).
- [ ] **Step 3: Recorder** — add:

```java
public Function<SyntheticCreationalContext<EmbeddingModel>, EmbeddingModel> embeddingModel(String configName) {
    LangChain4jJinferConfig.JinferConfig config = correspondingConfig(configName);
    if (!config.enableIntegration()) {
        return ctx -> new DisabledEmbeddingModel();
    }
    EmbeddingModelConfig emb = config.embeddingModel();
    return ctx -> {
        JinferEmbeddingModel.Builder builder = JinferEmbeddingModel.builder();
        applyModelRef(builder::modelPath, builder::model, configName, "embedding-model", emb.model(), emb.modelPath());
        emb.contextCapacity().ifPresent(builder::contextCapacity);
        builder.listeners(ctx.getInjectedReference(EMBEDDING_MODEL_LISTENER_TYPE_LITERAL).stream().collect(Collectors.toList()));
        JinferEmbeddingModel model = builder.build();
        JinferModels.registerForClose(model);
        return model;
    };
}
```

Add `private static final TypeLiteral<Instance<EmbeddingModelListener>> EMBEDDING_MODEL_LISTENER_TYPE_LITERAL = new TypeLiteral<>() {};` and imports for `JinferEmbeddingModel`, `EmbeddingModel`, `DisabledEmbeddingModel`, `EmbeddingModelListener`.

- [ ] **Step 4: Processor** — add `EmbeddingModelProviderCandidateBuildItem` to `providerCandidates` (gated by `config.embeddingModel().enabled()`), and a `generateEmbeddingBeans` `@BuildStep @Record(RUNTIME_INIT)` consuming `List<SelectedEmbeddingModelCandidateBuildItem>`, producing `EMBEDDING_MODEL` beans with `.unremovable()` and the `CHAT_MODEL_LISTENER`→`DotNames.CHAT_MODEL_LISTENER` replaced by injecting nothing extra (embedding listener is read inside the recorder from CDI via the injection point — mirror ollama's embedding block which injects only the customizer; here inject the `EmbeddingModelListener` instance). Use `import static ...LangChain4jDotNames.EMBEDDING_MODEL;` and add an injection point for `EmbeddingModelListener` (add a `DotNames.EMBEDDING_MODEL_LISTENER` if present; otherwise create a local `DotName`).
- [ ] **Step 5: Run, verify passes.**
- [ ] **Step 6: Commit** — `feat(jinfer): embedding model`.

---

## Task 5: Scoring (rerank) model

**Files:**
- Modify: `JinferRecorder.java` (add `scoringModel`)
- Modify: `JinferProcessor.java` (candidate + bean)
- Test: `.../deployment/JinferScoringBeanTest.java`

**Interfaces:**
- Produces: `JinferRecorder.scoringModel(String) : Function<SyntheticCreationalContext<ScoringModel>, ScoringModel>`.

- [ ] **Step 1: Failing test** — assert a `ScoringModel` bean exists. With `enable-integration=false`, scoring has no LangChain4j `Disabled*` variant — assert the bean is present and that *calling* `scoreAll` throws an `IllegalStateException` with message containing "disabled". (Verify whether `dev.langchain4j.model.scoring.DisabledScoringModel` exists; if it does, assert that type instead and simplify.)
- [ ] **Step 2: Run, verify fails.**
- [ ] **Step 3: Recorder** — add `scoringModel`. Enabled branch builds `JinferScoringModel.builder()` with model ref + `contextCapacity` + `instruction`, registers for close. Disabled branch: return a `ScoringModel` lambda/anonymous class whose `scoreAll` throws `new IllegalStateException("Jinfer integration is disabled")` (or `DisabledScoringModel` if it exists).
- [ ] **Step 4: Processor** — add `ScoringModelProviderCandidateBuildItem` to candidates (gated by `scoringModel().enabled()`); add `generateScoringBeans` consuming `List<SelectedScoringModelProviderBuildItem>`, producing `SCORING_MODEL` beans (`.unremovable()`, `.setRuntimeInit()`, `.defaultBean()`, `.createWith(recorder.scoringModel(configName))`). `import static ...LangChain4jDotNames.SCORING_MODEL;`
- [ ] **Step 5: Run, verify passes.**
- [ ] **Step 6: Commit** — `feat(jinfer): scoring (rerank) model`.

---

## Task 6: Audio transcription model

**Files:**
- Modify: `JinferRecorder.java` (add `transcriptionModel`)
- Modify: `JinferProcessor.java` (candidate + bean)
- Test: `.../deployment/JinferTranscriptionBeanTest.java`

**Interfaces:**
- Produces: `JinferRecorder.transcriptionModel(String) : Function<SyntheticCreationalContext<AudioTranscriptionModel>, AudioTranscriptionModel>`.

- [ ] **Step 1: Failing test** — assert an `AudioTranscriptionModel` bean exists and is `DisabledAudioTranscriptionModel` with `enable-integration=false` (that `Disabled*` type is confirmed to exist — openai uses it).
- [ ] **Step 2: Run, verify fails.**
- [ ] **Step 3: Recorder** — add `transcriptionModel`. Enabled: `JinferTranscriptionModel.builder()` with model ref only (no other knobs), register for close. Disabled: `new DisabledAudioTranscriptionModel()`.
- [ ] **Step 4: Processor** — add `AudioTranscriptionModelProviderCandidateBuildItem` to candidates (gated by `transcriptionModel().enabled()`); add `generateTranscriptionBeans` consuming `List<SelectedAudioTranscriptionModelProviderBuildItem>`, producing `AUDIO_TRANSCRIPTION_MODEL` beans. `import static ...LangChain4jDotNames.AUDIO_TRANSCRIPTION_MODEL;`
- [ ] **Step 5: Run, verify passes.**
- [ ] **Step 6: Commit** — `feat(jinfer): audio transcription model`.

---

## Task 7: Text-to-speech model (self-contained, no core SPI)

**Files:**
- Modify: `JinferRecorder.java` (add `ttsModel`)
- Create: `.../deployment/JinferDotNames.java` (local `TEXT_TO_SPEECH_MODEL` DotName)
- Modify: `JinferProcessor.java` (direct bean step with build-time config-name enumeration)
- Test: `.../deployment/JinferTtsBeanTest.java` (default + named)

**Interfaces:**
- Consumes: `org.eclipse.microprofile.config.Config` (build-time property-name enumeration).
- Produces: `JinferRecorder.ttsModel(String) : Function<SyntheticCreationalContext<TextToSpeechModel>, TextToSpeechModel>`.

- [ ] **Step 1: Failing test** — `JinferTtsBeanTest`: with `enable-integration=false`, inject the default `TextToSpeechModel` and a named one and assert both beans exist. Named bean obtained via a small `@ApplicationScoped` holder that injects `@ModelName("custom") TextToSpeechModel`. Config:

```java
.overrideConfigKey("quarkus.langchain4j.jinfer.enable-integration", "false")
.overrideConfigKey("quarkus.langchain4j.jinfer.tts-model.model", "x/y:Q8_0")
.overrideConfigKey("quarkus.langchain4j.jinfer.custom.tts-model.model", "x/y:Q8_0")
.overrideConfigKey("quarkus.langchain4j.jinfer.custom.enable-integration", "false")
```

Assert `synthesize(...)` throws an "integration disabled" `IllegalStateException` (TTS has no LangChain4j `Disabled*`).

- [ ] **Step 2: Run, verify fails.**
- [ ] **Step 3: Local DotName** — `JinferDotNames`:

```java
package io.quarkiverse.langchain4j.jinfer.deployment;

import org.jboss.jandex.DotName;
import dev.langchain4j.model.audio.TextToSpeechModel;

final class JinferDotNames {
    static final DotName TEXT_TO_SPEECH_MODEL = DotName.createSimple(TextToSpeechModel.class);
    private JinferDotNames() {}
}
```

- [ ] **Step 4: Recorder** — add `ttsModel`. Enabled: `JinferSpeechModel.builder()` with model ref + `speed` + `maxInputChars`, register for close. Disabled: anonymous `TextToSpeechModel` whose `synthesize(TextToSpeechRequest)` throws `new IllegalStateException("Jinfer integration is disabled")`.
- [ ] **Step 5: Processor TTS step** — no `Selected*` item exists, so enumerate config names at build time. Add a helper that reads `Config.getPropertyNames()` and extracts the set of distinct names, including the default:

```java
@BuildStep
@Record(ExecutionTime.RUNTIME_INIT)
void generateTtsBeans(JinferRecorder recorder,
        LangChain4jJinferBuildConfig buildConfig,
        BuildProducer<SyntheticBeanBuildItem> beanProducer) {
    if (buildConfig.ttsModel().enabled().isPresent() && !buildConfig.ttsModel().enabled().get()) {
        return;
    }
    for (String configName : discoverConfigNames()) {
        var tts = SyntheticBeanBuildItem.configure(JinferDotNames.TEXT_TO_SPEECH_MODEL)
                .setRuntimeInit()
                .defaultBean()
                .unremovable()
                .scope(ApplicationScoped.class)
                .createWith(recorder.ttsModel(configName));
        addQualifierIfNecessary(tts, configName);
        beanProducer.produce(tts.done());
    }
}

/** Default + every named config under quarkus.langchain4j.jinfer."<name>". */
private static java.util.Set<String> discoverConfigNames() {
    java.util.Set<String> names = new java.util.LinkedHashSet<>();
    names.add(NamedConfigUtil.DEFAULT_NAME);
    var config = org.eclipse.microprofile.config.ConfigProvider.getConfig();
    var pattern = java.util.regex.Pattern.compile("quarkus\\.langchain4j\\.jinfer\\.\"([^\"]+)\"\\..*");
    for (String name : config.getPropertyNames()) {
        var m = pattern.matcher(name);
        if (m.matches()) {
            names.add(m.group(1));
        }
    }
    return names;
}
```

(Confirm `NamedConfigUtil.DEFAULT_NAME` is the constant used for the default synthetic bean; grep the repo. The regex matches the quoted named-config form SmallRye uses for map keys.)

- [ ] **Step 6: Run, verify passes.**
- [ ] **Step 7: Commit** — `feat(jinfer): text-to-speech model (self-contained bean)`.

---

## Task 8: JVM flags (dev/test + production manifest + native)

**Files:**
- Modify: `model-providers/jinfer/runtime/pom.xml` (`<devMode><jvmOptions>`)
- Modify: `JinferProcessor.java` (native-access + native-image build items)

**Interfaces:**
- Produces: `ModuleEnableNativeAccessBuildItem`, `NativeImageEnableModule`, native-image flags.

- [ ] **Step 1: Dev/test flags** — in the runtime pom's `quarkus-extension-maven-plugin` config add (note: **no `<enable-preview/>`**, unlike the old llama3 module):

```xml
<devMode>
    <jvmOptions>
        <add-modules>jdk.incubator.vector</add-modules>
        <enable-native-access>ALL-UNNAMED</enable-native-access>
    </jvmOptions>
</devMode>
```

- [ ] **Step 2: Production-jar native access + native-image module** — add to `JinferProcessor`:

```java
@BuildStep
void nativeAccess(BuildProducer<io.quarkus.deployment.builditem.ModuleEnableNativeAccessBuildItem> nativeAccess) {
    // Manifest always renders Enable-Native-Access: ALL-UNNAMED; the ctor rejects the literal "ALL-UNNAMED",
    // so pass the jam/jinfer module name that performs native access (runs from the unnamed module).
    nativeAccess.produce(new io.quarkus.deployment.builditem.ModuleEnableNativeAccessBuildItem("com.qxotic.jam"));
}

@BuildStep
void nativeImageModules(BuildProducer<io.quarkus.deployment.builditem.nativeimage.NativeImageEnableModule> modules) {
    modules.produce(new io.quarkus.deployment.builditem.nativeimage.NativeImageEnableModule("jdk.incubator.vector"));
}
```

(Pin the exact module name argument during implementation — read the Automatic-Module-Name of `jam-vector`/`jinfer`. If none resolves cleanly, any non-module string maps to the unnamed module and the manifest still emits `ALL-UNNAMED`.)

- [ ] **Step 3: Verify the manifest** — build an uber/fast-jar of the IT (Task 10) or a throwaway app and confirm `Enable-Native-Access: ALL-UNNAMED` appears in `quarkus-app/quarkus/quarkus-application.dat`-adjacent manifest (`quarkus-run.jar` `META-INF/MANIFEST.MF`). Expected: attribute present.

Run (after Task 10 exists): `JAVA_HOME=/home/gandrian/.sdkman/candidates/java/25.0.3-tem ./mvnw -q -pl integration-tests/jinfer -am package -DskipTests -Dno-format -ntp && unzip -p integration-tests/jinfer/target/quarkus-app/quarkus-run.jar META-INF/MANIFEST.MF | grep -i native-access`
Expected: `Enable-Native-Access: ALL-UNNAMED`.

- [ ] **Step 4: Commit** — `feat(jinfer): JVM flags for dev mode and production jar manifest`.

---

## Task 9: Remove the `llama3-java` extension

**Files (delete):**
- `model-providers/llama3-java/` (whole tree)
- `integration-tests/llama3-java/` (whole tree)
- `docs/modules/ROOT/pages/llama3-chat-model.adoc`
- `docs/modules/ROOT/pages/includes/quarkus-langchain4j-llama3-java.adoc`
- `docs/modules/ROOT/pages/includes/quarkus-langchain4j-llama3-java_quarkus.adoc`
- `docs/modules/ROOT/pages/includes/quarkus-langchain4j-llama3-java_quarkus.langchain4j.adoc`

**Files (edit):**
- `model-providers/pom.xml`, `integration-tests/pom.xml`, `docs/pom.xml`, `docs/modules/ROOT/nav.adoc`, `.github/workflows/build-pull-request.yml`

- [ ] **Step 1: Delete module + IT + docs**

```bash
git rm -r model-providers/llama3-java integration-tests/llama3-java
git rm docs/modules/ROOT/pages/llama3-chat-model.adoc \
       docs/modules/ROOT/pages/includes/quarkus-langchain4j-llama3-java.adoc \
       docs/modules/ROOT/pages/includes/quarkus-langchain4j-llama3-java_quarkus.adoc \
       docs/modules/ROOT/pages/includes/quarkus-langchain4j-llama3-java_quarkus.langchain4j.adoc
```

- [ ] **Step 2: `model-providers/pom.xml`** — remove `<module>llama3-java</module>` from the `jdk22-plus` profile. If that profile now has no modules, remove the profile. (Keep the new `jdk25-plus` profile from Task 1.)
- [ ] **Step 3: `integration-tests/pom.xml`** — remove the `quarkus-langchain4j-llama3-java` dependencyManagement entry and the commented-out `jdk22-plus` profile that referenced the module.
- [ ] **Step 4: `docs/pom.xml`** — remove the `jdk22-plus` profile block that adds `quarkus-langchain4j-llama3-java-deployment`.
- [ ] **Step 5: `docs/modules/ROOT/nav.adoc`** — remove the `*** xref:llama3-chat-model.adoc[Llama3.java]` line.
- [ ] **Step 6: CI** — in `.github/workflows/build-pull-request.yml` remove the "Build Llama3-java extension" step (`if: matrix.java >= 22`) and the `| grep -v llama3-java \` line; update the adjacent comment to drop "Llama3".
- [ ] **Step 7: Verify no stale references**

Run: `git grep -il "llama3-java" -- ':!docs/superpowers/*' ':!*/target/*'`
Expected: no output (the spec/plan under `docs/superpowers/` may still mention it — that's fine).

- [ ] **Step 8: Commit** — `refactor: remove llama3-java extension (replaced by jinfer)`.

---

## Task 10: Integration-test module

**Files:**
- Create: `integration-tests/jinfer/pom.xml`
- Create: `integration-tests/jinfer/src/main/java/org/acme/example/jinfer/ChatResource.java`
- Create: `integration-tests/jinfer/src/main/resources/application.properties`
- Modify: `integration-tests/pom.xml` (register module under `jdk25-plus`; add depMgmt)

- [ ] **Step 1: IT pom** — model on `integration-tests/ollama/pom.xml` (or the former llama3-java IT pom shape): artifact `quarkus-langchain4j-integration-test-jinfer`, depends on `quarkus-langchain4j-jinfer` + `-deployment`, `maven.compiler.release=25` with `--add-modules=jdk.incubator.vector`, `skipITs=true`, a `native-image` profile.
- [ ] **Step 2: Resource + properties** — a trivial `ChatResource` injecting `ChatModel`; `application.properties` sets a small model, e.g. `quarkus.langchain4j.jinfer.chat-model.model=LiquidAI/LFM2.5-350M-GGUF:Q8_0`. Keep heavy inference out of the default build (no `@QuarkusTest` that actually calls the model, or guard with an assumption).
- [ ] **Step 3: Register** — in `integration-tests/pom.xml` add a `jdk25-plus` profile with `<module>jinfer</module>` and a depMgmt entry `quarkus-langchain4j-jinfer` at `999-SNAPSHOT`.
- [ ] **Step 4: Verify it compiles on JDK 25**

Run: `JAVA_HOME=/home/gandrian/.sdkman/candidates/java/25.0.3-tem ./mvnw -q -pl integration-tests/jinfer -am install -DskipTests -Dno-format -ntp`
Expected: BUILD SUCCESS.

- [ ] **Step 5: Commit** — `test(jinfer): add integration-test module`.

---

## Task 11: Documentation page

**Files:**
- Create: `docs/modules/ROOT/pages/jinfer.adoc`
- Modify: `docs/modules/ROOT/nav.adoc` (add xref)
- Modify: `docs/pom.xml` (jdk25-plus profile to generate config includes)

- [ ] **Step 1: Doc page** — write `jinfer.adoc`: overview, the `quarkus.langchain4j.jinfer.*` config, the `model` HF-ref format, **the JDK 25 requirement**, and the **production-run JVM flag requirement** (`java --add-modules jdk.incubator.vector -jar quarkus-run.jar`, or `JAVA_OPTS_APPEND`, or container `ENV`), noting the Vector API falls back gracefully if absent. Reference the generated config include `include::includes/quarkus-langchain4j-jinfer.adoc[]`.
- [ ] **Step 2: Nav** — add `*** xref:jinfer.adoc[Jinfer (Qxotic)]` in the chat-models section of `nav.adoc`.
- [ ] **Step 3: docs/pom.xml** — add a `jdk25-plus` profile (activation `<jdk>[25,)</jdk>`) adding `quarkus-langchain4j-jinfer-deployment` (pom, test scope, all transitive deps excluded) so config-doc generation picks it up — mirror the `jdk22-plus` profile that previously did this for llama3-java.
- [ ] **Step 4: Commit** — `docs(jinfer): add jinfer provider documentation`.

---

## Task 12: CI build step + regenerate aggregated config docs + full verification

**Files:**
- Modify: `.github/workflows/build-pull-request.yml`
- Modify: `docs/modules/ROOT/pages/includes/quarkus-all-config.adoc` (regenerated)

- [ ] **Step 1: CI build step** — add a "Build Jinfer extension" step gated `if: ${{ matrix.java >= 25 }}` running `./mvnw -B clean install -DskipTests -Dno-format -ntp -f model-providers/jinfer/pom.xml`, mirroring the removed llama3-java step. Add `| grep -v jinfer \` to the IT-matrix filter (the module is JDK-25-gated, so keep it out of the default IT matrix) and update the comment.
- [ ] **Step 2: Regenerate aggregated config docs** — **full install on JDK 25** (not `-pl docs -am`, per the `docs-config-check-stale-targets` memory, to avoid stale `~/.m2` metadata):

Run: `JAVA_HOME=/home/gandrian/.sdkman/candidates/java/25.0.3-tem ./mvnw install -DskipTests -Dno-format -ntp`
Then verify:
- `grep -c "quarkus.langchain4j.llama3" docs/modules/ROOT/pages/includes/quarkus-all-config.adoc` → should drop to only llama3.2 Ollama example mentions (no `quarkus.langchain4j.llama3.*` config rows).
- `grep -c "quarkus.langchain4j.jinfer" docs/modules/ROOT/pages/includes/quarkus-all-config.adoc` → should be > 0 (jinfer section present).
- Confirm the generated `docs/modules/ROOT/pages/includes/quarkus-langchain4j-jinfer*.adoc` now exist.

- [ ] **Step 3: Commit** — `ci,docs(jinfer): add CI build step and regenerate aggregated config`.

---

## Self-review notes (for the implementer)

- Interfaces to pin from source during implementation (flagged, not placeholders — they are confirmations against real code the implementer has access to): `ShutdownContextBuildItem` usage for RUNTIME_INIT recorders; `NamedConfigUtil.DEFAULT_NAME`; existence of `dev.langchain4j.model.scoring.DisabledScoringModel`; the `EmbeddingModelListener` DotName (add a local one if `DotNames` lacks it); the exact `jam`/`jinfer` module name for `ModuleEnableNativeAccessBuildItem`.
- Every config key in Task 2 maps to a jinfer builder method verified in the spec's §3; embedding deliberately omits `dimensions` (no builder).
- Each model-type task reuses `applyModelRef` (Task 3) → the "no model configured" and "model-path wins" Review Focus items are enforced once, centrally.
