# Code reviewer for GitHub pull requests

This sample reviews a real GitHub pull request with an LLM. Give it a pull request link: it fetches
the pull request and its diff, then streams the model's reasoning and a Markdown review with a
summary, findings (severity, `path:line`, problem and fix) and a verdict.

As configured, the model runs locally on the GPU through the
[JitLLM](https://github.com/beehive-lab/jitllm) provider. The code only uses the LangChain4j API, so
it runs just as well on [Ollama or OpenAI](#using-other-model-providers): one dependency and one block
of properties change.

It shows:

- a command-line application with `quarkus-aesh`: a single `@CommandDefinition` run once and exited ([`ReviewCommand`](src/main/java/io/quarkiverse/langchain4j/sample/codereviewer/ReviewCommand.java));
- the GitHub API through [quarkus-github-api](https://github.com/quarkiverse/quarkus-github-api) ([`GitHubPullRequests`](src/main/java/io/quarkiverse/langchain4j/sample/codereviewer/GitHubPullRequests.java));
- an AI service without chat memory that streams `ChatEvent`s, so the model's reasoning
  (`PartialThinkingEvent`) is printed as it is generated, before the review (`PartialResponseEvent`)
  ([`CodeReviewer`](src/main/java/io/quarkiverse/langchain4j/sample/codereviewer/CodeReviewer.java));
- application settings as a `@ConfigMapping` ([`CodeReviewerConfig`](src/main/java/io/quarkiverse/langchain4j/sample/codereviewer/CodeReviewerConfig.java)).

The diff is prepared before it reaches the model
([`DiffFormatter`](src/main/java/io/quarkiverse/langchain4j/sample/codereviewer/DiffFormatter.java)): each
added or unchanged line is prefixed with its line number in the new file, so findings can cite
`path:line`; lock files, binaries and files GitHub shows no patch for are dropped; and whole files are
included until the diff budget runs out. The model is told which files it cannot see.

## Prerequisites

- Optionally, `GITHUB_TOKEN`: public pull requests need no token, but GitHub allows only 60
  unauthenticated requests per hour, and a review makes two. With the GitHub CLI:
  `export GITHUB_TOKEN=$(gh auth token)`.

For JitLLM, the configured provider:

- A GPU with 24 GB of memory. The model, Qwen3.8 27B in 4-bit (`unsloth/Qwen3.8-27B-GGUF`, `Q4_0`), is a
  16 GB file; it is downloaded to `~/.langchain4j/models/` on first launch.
- JDK 21 or newer, and a TornadoVM SDK installed beforehand. JitLLM does not bundle TornadoVM: the
  application gets it at run time from the SDK that `TORNADOVM_HOME` points at, through the SDK's
  `tornado-argfile`. Use TornadoVM 7.0.1 in the line that matches your JDK (`jdk21` for JDK 21,
  `jdk22plus` for JDK 22 and newer) and a backend your GPU supports: `cuda` for NVIDIA, or `opencl`.

  With [SDKMAN!](https://sdkman.io), for JDK 25 and CUDA:

  ```shell
  sdk install java 25.0.2-open
  sdk install tornadovm 7.0.1-jdk22plus-cuda   # JDK 21: 7.0.1-jdk21-cuda; other GPUs: -opencl
  sdk use java 25.0.2-open
  sdk use tornadovm 7.0.1-jdk22plus-cuda       # sets TORNADOVM_HOME
  tornado --devices                            # lists the GPU TornadoVM will use
  ```

  Without SDKMAN, unpack an SDK from the [TornadoVM releases](https://github.com/beehive-lab/TornadoVM/releases)
  and set `TORNADOVM_HOME` to it. More in
  [Java Version and TornadoVM SDK](https://docs.quarkiverse.io/quarkus-langchain4j/dev/jitllm-chat-model.html#_prerequisites).

Ollama and OpenAI need no TornadoVM; see
[Using other model providers](#using-other-model-providers).

## Running the sample

```shell
mvn package
java @$TORNADOVM_HOME/tornado-argfile --add-modules jdk.incubator.vector \
    -jar target/quarkus-app/quarkus-run.jar https://github.com/beehive-lab/TornadoVM/pull/1132
```

Or in dev mode, which passes the TornadoVM arguments itself (the `tornadovm` profile in the `pom.xml`,
active whenever `TORNADOVM_HOME` is set):

```shell
mvn quarkus:dev -Dquarkus.args="https://github.com/beehive-lab/TornadoVM/pull/1132"
```

Options: `--max-diff-chars N` overrides the diff budget, `--help` lists them. The exit code is `0` when
a review was printed, `1` when the link is not a pull request or GitHub or the model fails, and `2` when
the link is missing.

The log reports progress until the first token, and at the end the prompt and generated token counts
and how long the review took.

## Configuration

In `src/main/resources/application.properties`, for JitLLM:

- `quarkus.langchain4j.jitllm.chat-model.enable-thinking=true`: the model reasons before answering.
  This makes a review slower, but without it the model misreads diffs more often.
- `quarkus.langchain4j.jitllm.chat-model.max-tokens=32768`: also the context length. The prompt, the
  reasoning and the review must fit in it together.
- `quarkus.langchain4j.jitllm.chat-model.prefill-decode=true` and `prefill-batch-size=512`: the prompt is
  a whole diff, so the model reads it 512 tokens at a time.
- `quarkus.langchain4j.jitllm.chat-model.device-memory=22GB`: GPU memory for the weights and the
  key/value cache.

For any provider:

- `gh-pr-code-reviewer.max-diff-chars` (default `60000`), `gh-pr-code-reviewer.max-description-chars` (default
  `4000`) and `gh-pr-code-reviewer.progress-interval` (default `15s`).

With JitLLM, the engine can log its own weight loading and TornadoVM initialization: add
`-Djitllm.EnableTimingForTornadoVMInit=true` to the `java` command.

## Using other model providers

The code only uses the LangChain4j API, so the model provider is a matter of one dependency and a few
properties. Replace the `quarkus-langchain4j-jitllm` dependency, and `quarkus-langchain4j-jitllm-deployment`
below it, and replace the JitLLM block at the top of `application.properties` (the "Model provider"
section). Everything else stays. Without JitLLM there is no TornadoVM to set up: run the application
with `java -jar target/quarkus-app/quarkus-run.jar <link>`, and dev mode needs no extra arguments (the
`tornadovm` profile stays inactive while `TORNADOVM_HOME` is unset).

A review needs more of the provider than a short chat does: a context large enough for a whole diff,
and a request timeout of minutes rather than seconds. With a reasoning model, the reasoning is printed
if the provider streams it back.

### Ollama

Use `quarkus-langchain4j-ollama` (and `quarkus-langchain4j-ollama-deployment`), and:

```properties
quarkus.langchain4j.ollama.chat-model.model-id=<a model with reasoning, e.g. a Qwen3 model>
quarkus.langchain4j.ollama.chat-model.temperature=0.2
quarkus.langchain4j.ollama.chat-model.top-p=0.95
# Reason before answering, and stream the reasoning back
quarkus.langchain4j.ollama.chat-model.model-options.think=true
quarkus.langchain4j.ollama.chat-model.model-options.return-thinking=true
# Ollama's default context can be too small for a whole diff
quarkus.langchain4j.ollama.chat-model.model-options.num-ctx=32768
# A review takes minutes
quarkus.langchain4j.ollama.timeout=10m
```

> **Note:** with a streaming AI service, as here, the Ollama provider currently sends the
> `model-options.*` settings inside the request's `options` under their Java names (`numCtx`,
> `think`, `returnThinking`), where Ollama expects `num_ctx`, and `think` at the top level. Ollama
> ignores them, so for now `think`, `return-thinking` and `num-ctx` above have no effect: a model
> that reasons by default still does, and still streams its reasoning, and the context is the
> server's default. Set the context on the Ollama side until this is fixed, with
> `OLLAMA_CONTEXT_LENGTH=32768` for the server or `PARAMETER num_ctx 32768` in the model's Modelfile.

### OpenAI

Use `quarkus-langchain4j-openai` (and `quarkus-langchain4j-openai-deployment`), and:

```properties
quarkus.langchain4j.openai.api-key=${OPENAI_API_KEY}
quarkus.langchain4j.openai.chat-model.model-name=<a reasoning model, e.g. gpt-5-mini>
# A review takes minutes
quarkus.langchain4j.openai.timeout=10m
```

Leave `temperature` and `top-p` unset: reasoning models accept only their defaults. To trade depth
for speed, set `quarkus.langchain4j.openai.chat-model.reasoning-effort` (`minimal`, `low`, `medium`
or `high`). The OpenAI API does not stream the model's reasoning, so nothing is printed until the
review starts; the progress lines in the log show that it is working.

The same provider talks to any OpenAI-compatible server: set
`quarkus.langchain4j.openai.base-url` as well, e.g. `http://localhost:11434/v1` for Ollama's
compatible endpoint.

## Limitations

The model sees only the diff: the changed hunks, not the rest of each file or of the repository. It is
told not to report code outside the hunks as missing, but a finding can still be wrong, so treat the
review as a first pass. Pull requests larger than the diff budget are reviewed in part.
