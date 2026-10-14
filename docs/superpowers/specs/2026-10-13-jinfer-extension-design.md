# Replace the `llama3-java` extension with a Qxotic `jinfer` extension

Date: 2026-10-13
Status: Draft for review

## 1. Context and goal

The `model-providers/llama3-java` Quarkus extension vendors a verbatim copy of
mukel/llama3.java (package `io.quarkiverse.langchain4j.llama3.copy`, 13 files) to
provide pure-Java, in-process LLM inference. That upstream single-file engine has
effectively been superseded by **Qxotic** (a.k.a. Quixotic AI), whose `jinfer`
inference engine ships a maintained LangChain4j integration on Maven Central.

Goal: **remove `llama3-java` entirely and introduce a new `jinfer` Quarkus
extension** that delegates to `com.qxotic:jinfer-langchain4j` instead of
vendoring inference code. The new extension targets **JDK 25** (jinfer's floor),
which is accepted.

## 2. Locked decisions

1. Build a **new Quarkus extension** (runtime + deployment) wrapping
   `com.qxotic:jinfer-langchain4j`. Qxotic ships no Quarkus support, so we author
   the Quarkus glue; it delegates to jinfer's builders rather than embedding an
   engine.
2. Name the extension **`jinfer`**; config root **`quarkus.langchain4j.jinfer.*`**
   (clean break from `quarkus.langchain4j.llama3.*` — the engine is no longer
   Llama3-specific).
3. Expose **all six** model types the upstream integration provides.
4. **JDK 25** baseline; the module is gated behind a `jdk25-plus` Maven profile so
   the main build on lower JDKs skips it.

## 3. Upstream facts (verified)

- Artifacts (groupId `com.qxotic`, version **0.3.1**, published 2026-10-07):
  - `jinfer-langchain4j` — the LangChain4j integration (the artifact we wrap).
  - `jinfer-bom` — BOM (import scope) governing all `com.qxotic` versions.
  - Inference core/support: `jinfer-core`, `jinfer-chat`, `jinfer-cache`,
    `jinfer-codecs`, `jinfer-kernels`, `jinfer-hub`, `jinfer-jinja`, etc.
  - Model-family **ports** (at least one required, or no model can load):
    `jinfer-llama`, `jinfer-qwen3`, `jinfer-gemma4`, … and umbrella
    `jinfer-models-all`.
  - Compute backends (optional, `runtime` scope): `jam-vector` (Panama Vector
    API, pure Java — no C toolchain), `jam-native` (SIMD, needs prebuilt native
    lib), `jam-scalar`. With none present, jinfer uses built-in Java kernels.
- Built against **LangChain4j 1.21.0** — exactly this repo's `langchain4j.version`.
  No version mismatch.
- Requires **JDK 25**, the incubator **Vector API** (`--add-modules
  jdk.incubator.vector`) and FFM native access (`--enable-native-access=ALL-UNNAMED`).
  **No `--enable-preview`.**
- Integration API — package `com.qxotic.jinfer.langchain4j`, builder-based, all
  models `AutoCloseable`:
  - `JinferChatModel implements dev.langchain4j.model.chat.ChatModel`
  - `JinferStreamingChatModel implements StreamingChatModel`
  - `JinferEmbeddingModel implements EmbeddingModel`
  - `JinferScoringModel implements ScoringModel`
  - `JinferTranscriptionModel implements AudioTranscriptionModel`
  - `JinferSpeechModel implements TextToSpeechModel`
  - Builder knobs: `.model("owner/repo:quant")` / `.modelPath(Path)`,
    `.contextCapacity(int)`, `.temperature(double)`, `.topP(double)`,
    `.maxOutputTokens(int)`, `.maxReasoningTokens(int)`, `.seed(long)`,
    `.dimensions(int)` (embeddings), `.promptCache(Path)`, `.companion(cap, ref)`.
- Model acquisition: builders download GGUF from HuggingFace at `build()` time
  into jinfer's own resumable, sha256-verified cache (`JINFER_OFFLINE=1` to
  forbid network). `.modelPath(Path)` uses a local file with no download.

## 4. Core-framework support (verified)

quarkus-langchain4j core auto-wires providers via `*ProviderCandidateBuildItem`
→ `BeansProcessor.handleProviders` → `Selected*` build items → provider
`generateBeans`. Supported model types with existing SPI:

| jinfer type → LangChain4j interface | core SPI |
|---|---|
| ChatModel | ✅ `ChatModelProviderCandidateBuildItem` (also drives StreamingChatModel) |
| EmbeddingModel | ✅ |
| ScoringModel | ✅ |
| AudioTranscriptionModel | ✅ |
| **TextToSpeechModel** | ❌ **no SPI anywhere in core** |

TTS has no DotName, candidate/selected build items, or selection branch in core.
Per decision, TTS is produced as a **self-contained synthetic bean in the jinfer
extension** (default + `@ModelName` named beans), bypassing the core SPI. We add a
local `TEXT_TO_SPEECH_MODEL` DotName in the extension (interface
`dev.langchain4j.model.audio.TextToSpeechModel`, present in 1.21.0).

## 5. Module structure

Mirror `model-providers/ollama`:

```
model-providers/jinfer/
  pom.xml                      # parent, packaging pom, modules runtime+deployment
  runtime/
    pom.xml                    # artifact quarkus-langchain4j-jinfer
    src/main/java/io/quarkiverse/langchain4j/jinfer/
      JinferRecorder.java
      runtime/config/          # config interfaces (see §7)
      runtime/graal/           # native-image Feature + substitutions if needed
    src/main/resources/META-INF/
      quarkus-extension.yaml   # name "LangChain4j Jinfer", status preview
      beans.xml
  deployment/
    pom.xml                    # artifact quarkus-langchain4j-jinfer-deployment
    src/main/java/io/quarkiverse/langchain4j/jinfer/deployment/
      JinferProcessor.java
      ...BuildTimeConfig / ...BuildConfig
    src/test/java/...          # QuarkusUnitTest smoke tests
```

Constants: `PROVIDER = "jinfer"`, `FEATURE = "langchain4j-jinfer"`.

## 6. Dependencies and BOM

- Root `pom.xml`: add `<jinfer.version>0.3.1</jinfer.version>`; import
  `com.qxotic:jinfer-bom:${jinfer.version}` (scope import, type pom) into
  `<dependencyManagement>`; add a BOM entry for `quarkus-langchain4j-jinfer`
  (and `-jinfer` codestarts classifier if codestarts are added — none planned
  initially).
- `runtime/pom.xml` depends on:
  - `io.quarkiverse.langchain4j:quarkus-langchain4j-core`
  - `com.qxotic:jinfer-langchain4j`
  - `com.qxotic:jinfer-models-all` — **recommended**: bundle the umbrella so any
    GGUF model works out of the box. (Alternative if footprint matters: depend
    only on `jinfer-llama` and document adding other ports. Decision: start with
    `jinfer-models-all`.)
  - `com.qxotic:jam-vector` (`runtime` scope) — Panama acceleration, no native
    toolchain. `jam-native` left to the user.
  - `graal-sdk` (`provided`) only if native substitutions are added.
- `maven.compiler.release=25` in the jinfer parent pom.

## 7. Configuration model (`quarkus.langchain4j.jinfer.*`)

Follow the ollama pattern: a top-level config with a default section and named
sections (`@WithParentName` + `@WithDefaults` → `quarkus.langchain4j.jinfer."<name>".*`).
Split across build-time, build-and-run-time-fixed, and run-time roots as the
value's phase requires.

Common:
- `enable-integration` (boolean, default `true`) — when false, for chat/
  streaming/embedding/scoring the recorder returns LangChain4j's `Disabled*`
  model (jinfer itself ships **no** `Disabled*` classes; use
  `dev.langchain4j.model.chat.DisabledChatModel` etc. as ollama does). For
  transcription/TTS, which have no LangChain4j `Disabled*` variant, produce a
  model that throws a clear "integration disabled" message on use.
- per-type `<type>.enabled` (build-time `Optional<Boolean>`, default `true`) —
  gates whether the candidate/bean is produced.
- **No `log-requests`/`log-responses` config.** jinfer exposes no request/
  response logging toggle. Chat and embedding builders accept
  `listeners(List<ChatModelListener>)` / `listeners(List<EmbeddingModelListener>)`;
  wire these from the injected `Instance<...>` the core already provides
  (as ollama does). Scoring/transcription/TTS have no listener support.

Per model type (keys map to jinfer builder methods — **boxed types where jinfer
uses them**):
- `chat-model` (shared by streaming): `model` (String), `model-path` (Path),
  `context-capacity` (int), `temperature` (Double, inherits
  `quarkus.langchain4j.temperature`), `top-p` (Double), `top-k` (Integer),
  `min-p` (Double), `max-output-tokens` (Integer), `max-reasoning-tokens` (int),
  `seed` (Long), `prompt-cache` (Path), `thinking` (boolean)
- `embedding-model`: `model`, `model-path`, `context-capacity` (int, default
  2048). **No `dimensions`** — jinfer has no `.dimensions()` builder; dimensions
  is a per-request LangChain4j parameter (Matryoshka ports only).
- `scoring-model`: `model`, `model-path`, `context-capacity` (int, default 2048),
  `instruction` (String, optional)
- `transcription-model`: `model`, `model-path` (no other knobs on the builder)
- `tts-model`: `model`, `model-path`, `speed` (double), `max-input-chars` (int,
  default 4096)

No default model name is hard-coded by jinfer. We require the user to set
`model` (or `model-path`); if neither is set for an enabled type, fail fast at
build/startup with a clear message. (Rationale: unlike the old extension there is
no single "Llama3" default that fits all six types.)

Model cache location is managed by jinfer's hub; we do not reimplement the old
`models-path` / fast-jar bundling. If jinfer exposes a cache-dir API we may map a
single optional `models-cache-path`. **Open item — confirm.**

## 8. Bean wiring

`JinferProcessor`:
- `@BuildStep providerCandidates(...)` — gated per type by `<type>.enabled`,
  produce `ChatModelProviderCandidateBuildItem(PROVIDER)`,
  `EmbeddingModelProviderCandidateBuildItem`, `ScoringModelProviderCandidateBuildItem`,
  `AudioTranscriptionModelProviderCandidateBuildItem`.
- `@BuildStep @Record(RUNTIME_INIT) generateBeans(JinferRecorder, List<Selected*>)`
  — for each selected item where `PROVIDER.equals(getProvider())`, produce
  `SyntheticBeanBuildItem` via `recorder.<type>Model(configName)`:
  - chat selection → both `CHAT_MODEL` and `STREAMING_CHAT_MODEL` beans
  - embedding / scoring / transcription → their beans
  - apply `@ModelName(configName)` qualifier for non-default names; default uses
    `.defaultBean()`.
- `@BuildStep @Record(RUNTIME_INIT) generateTtsBeans(...)` — **TTS special case**:
  no `Selected*` item exists, so iterate the configured names directly (default +
  named) from a build-time view of config and produce `TextToSpeechModel`
  synthetic beans via `recorder.ttsModel(configName)`, gated by
  `tts-model.enabled`.
- `@BuildStep featureAndJvm(...)` — `FeatureBuildItem(FEATURE)` plus the JVM-flag
  build items (see §10).

`JinferRecorder` — one method per type returning
`Function<SyntheticCreationalContext<X>, X>`, reading the runtime config for
`configName` and building via the jinfer builder (`.model(...)`,
`.temperature(...)`, etc.). See §9 for lifecycle.

## 9. Model lifecycle

jinfer models are `AutoCloseable` and hold large weights in memory. The recorder
registers each created model with Quarkus `ShutdownContext` (or produces
`@ApplicationScoped` beans with a disposer) so they are closed on application
shutdown. This must be explicit — leaking undisposed models would retain
gigabytes.

## 10. JVM flags strategy (the critical constraint)

Required flags: `--add-modules jdk.incubator.vector` and
`--enable-native-access=ALL-UNNAMED`. No `--enable-preview`.

Verified against the Quarkus checkout (main → 3.40.0). There is **no single JVM
option build item**; the relevant pieces:

| Flag | Dev mode / continuous test | Production fast-jar/uber-jar | Native image |
|---|---|---|---|
| `--enable-native-access=ALL-UNNAMED` | extension descriptor `<devMode><jvmOptions>` | **`ModuleEnableNativeAccessBuildItem`** → `Enable-Native-Access: ALL-UNNAMED` manifest attr (automatic) | n/a |
| `--add-modules jdk.incubator.vector` | extension descriptor `<devMode><jvmOptions>` | **not expressible** — JDK has no `Add-Modules` manifest attr; must be documented | `NativeImageEnableModule("jdk.incubator.vector")` |

Implementation:
1. **Deployment build steps:**
   - `produce(new ModuleEnableNativeAccessBuildItem(<module-name>))` → native
     access in the production jar manifest automatically. The ctor **rejects the
     literal `"ALL-UNNAMED"`**; pass the native-access library's module name
     (jinfer/jam runs from the classpath/unnamed module, so a non-real-module
     name maps to the unnamed module). The emitted manifest attribute is always
     `Enable-Native-Access: ALL-UNNAMED` regardless of the argument. Pin the
     exact value during implementation.
   - `produce(new NativeImageEnableModule("jdk.incubator.vector"))` → native
     build gets `--add-modules`.
2. **Extension descriptor** (`runtime/pom.xml`, `quarkus-extension-maven-plugin`
   `extension-descriptor` goal, `<devMode>`): declare
   `<jvmOptions>` with `add-modules=jdk.incubator.vector` and
   `enable-native-access=ALL-UNNAMED`. This injects both into the dev-mode launch
   command and continuous-testing JVM (confirmed via
   `DevModeCommandLineBuilder` / `MutableStandardJvmOption.toCliOptions`). Drop
   the old module's `--enable-preview`.
3. **Production run gap (documented):** `java -jar quarkus-run.jar` cannot
   receive `--add-modules jdk.incubator.vector` automatically. Document that
   users run `java --add-modules jdk.incubator.vector -jar quarkus-run.jar`, or
   set `JAVA_OPTS_APPEND`/`JAVA_OPTS` (honored by the Quarkus run wrapper /
   container image), or set it in a container `ENV`. Note the Vector API falls
   back gracefully if the module is absent (reduced performance, not failure) —
   so the app still runs; only speed degrades.

Minimum Quarkus version for these build items: `ModuleEnableNativeAccessBuildItem`
≥ 3.32.0, present in the target 3.40.0. `NativeImageEnableModule` present.

## 11. Native image support

Best-effort / experimental in the first cut (not a guaranteed, verified path):
- `JinferProcessor` registers `NativeImageEnableModule("jdk.incubator.vector")`,
  vector-API native flags (as the old module did:
  `-H:+VectorAPISupport -H:+ForeignAPISupport
  -Djdk.incubator.vector.VECTOR_ACCESS_OOB_CHECK=0`), and runtime-init where
  jinfer requires it.
- jinfer uses a ServiceLoader-based port architecture — ensure service files are
  preserved (reflection/resource registration; if any shading is involved,
  `ServicesResourceTransformer`). Register jinfer's service interfaces for native.
- We do **not** port the old AOT model-preload-into-native path; model loading is
  jinfer's responsibility at runtime.

The old `--enable-preview` native flag is dropped. Native correctness is not
verified in this spec and is flagged as a risk (§15).

## 12. Behavior changes from `llama3-java` (intentional)

- Model acquisition: drop `include-models-in-artifact`, fast-jar bundling, the
  `Llama3ModelRegistry` HTTP download, `ModelsPathConfigSource`, and AOT native
  preload. jinfer owns download + caching.
- Config root renamed `quarkus.langchain4j.llama3.*` → `quarkus.langchain4j.jinfer.*`;
  `model-name` + `quantization` collapse into a single `model` ref
  (`owner/repo:quant`).
- New capabilities: embedding, scoring, transcription, TTS (old module was chat +
  streaming only).

## 13. Remove `llama3-java` (inventory — mirror the earlier jlama removal)

Delete:
- `model-providers/llama3-java/` (parent, runtime, deployment, vendored `copy/`).
- `integration-tests/llama3-java/`.
- `docs/modules/ROOT/pages/llama3-chat-model.adoc`.
- `docs/modules/ROOT/pages/includes/quarkus-langchain4j-llama3-java*.adoc` (3
  generated includes).

Edit (remove llama3-java references):
- `model-providers/pom.xml` — the `jdk22-plus` profile's `<module>llama3-java</module>`
  (remove the module; the profile may be repurposed/removed if empty).
- `integration-tests/pom.xml` — the `quarkus-langchain4j-llama3-java` depMgmt
  entry and the commented-out `jdk22-plus` profile containing the module.
- `docs/pom.xml` — the `jdk22-plus` profile adding
  `quarkus-langchain4j-llama3-java-deployment`.
- `docs/modules/ROOT/nav.adoc` — the `xref:llama3-chat-model.adoc` entry.
- `.github/workflows/build-pull-request.yml` — the "Build Llama3-java extension"
  step (`if: matrix.java >= 22`) and the `grep -v llama3-java` IT-matrix filter;
  update the adjacent comment.
- Regenerate `docs/modules/ROOT/pages/includes/quarkus-all-config.adoc` (remove
  the `quarkus.langchain4j.llama3.*` rows). **Regenerate via a full
  `./mvnw install -DskipTests`, not `-pl docs -am`**, to avoid the stale-`~/.m2`
  config-metadata pitfall (see repo memory `docs-config-check-stale-targets`).

## 14. New wiring for `jinfer`

- `model-providers/pom.xml` — register `<module>jinfer</module>` under a new
  `jdk25-plus` profile (`<jdk>[25,)</jdk>` activation), analogous to the existing
  `jdk21-plus`/`jdk22-plus` profiles.
- Root `pom.xml` — `jinfer.version`, `jinfer-bom` import, BOM entry.
- `integration-tests/jinfer/` — new IT module (gated `jdk25-plus`), `skipITs=true`
  by default (model downloads are heavy), plus depMgmt entry in
  `integration-tests/pom.xml`.
- `docs/pom.xml` — `jdk25-plus` profile adding `quarkus-langchain4j-jinfer-deployment`
  for config-doc generation.
- `docs/modules/ROOT/nav.adoc` — add `xref:jinfer.adoc` entries (chat + the new
  model types as appropriate).
- `.github/workflows/build-pull-request.yml` — add a "Build Jinfer extension"
  step gated `if: matrix.java >= 25` (matrix already includes 25), and add
  `grep -v jinfer` to the IT-matrix filter.

## 15. Testing strategy (TDD)

- Deployment `QuarkusUnitTest` smoke test: assert the expected beans
  (`ChatModel`, `StreamingChatModel`, `EmbeddingModel`, `ScoringModel`,
  `AudioTranscriptionModel`, `TextToSpeechModel`) are produced and injectable,
  without performing real inference — either with `enable-integration=false`
  (expect `Disabled*` where applicable) or pointed at a tiny local GGUF via
  `model-path` to avoid network.
- Named-config test: a `@ModelName("foo")`-qualified bean resolves from
  `quarkus.langchain4j.jinfer."foo".*`.
- Real inference: a manual / `skipITs` integration-test module exercising an
  actual small GGUF download on JDK 25 (not run in the default matrix).
- Each capability lands test-first: write the failing bean-wiring assertion,
  then implement the processor/recorder path to satisfy it.

## 16. Documentation

- New page `docs/modules/ROOT/pages/jinfer.adoc` (or per-type pages) documenting:
  the `quarkus.langchain4j.jinfer.*` config, the `model` HF-ref format, **the JDK
  25 requirement**, and the **production-run JVM flag requirement**
  (`--add-modules jdk.incubator.vector`) with the `JAVA_OPTS_APPEND`/container
  guidance from §10.
- Config includes are generated from the config classes during the docs build.

## 17. Risks / open items

- **Production `--add-modules` gap** (§10): unavoidable; documented, with graceful
  Vector-API fallback. Confirm fallback behavior is acceptable (perf only).
- **Native image** (§11): unverified end-to-end; treated as experimental.
- **jinfer API details to pin from source during implementation:** exact builder
  method names/signatures per type; whether request/response logging or
  `ChatModelListener` hookup is supported (affects `log-requests`/`log-responses`
  config); whether a cache-dir API exists (affects `models-cache-path`);
  `Disabled*` model availability for each type (esp. TTS).
- **`jinfer-models-all` footprint:** large; revisit if jar size is a concern
  (fallback: per-family ports).
- **CI runtime:** jinfer tests download models; keep heavy inference out of the
  default matrix (`skipITs`).

## 18. Out of scope

- Adding a generic TextToSpeech SPI to core (rejected in favor of the
  self-contained bean).
- The separate GPU extension `model-providers/gpu-llama3` (tracks
  beehive-lab/GPULlama3.java → `jllm`); unrelated to this change.
- Cosmetic cleanup of leftover "Jlama" wording elsewhere in the repo.
