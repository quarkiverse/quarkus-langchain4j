# Context compaction example

This example shows how the `quarkus-langchain4j-context-compaction` extension keeps long conversations within a token
budget, and how two AI services of the same application can use different compaction profiles.

The application replays a recorded support conversation (126 messages, about 14k tokens, with large tool outputs) into
two assistants that use the same model and the same 8k-token budget, but different compaction strategies. You then
ask both about facts from the beginning of the conversation and compare their answers, and the tokens they received.

## Running the example

```shell
export QUARKUS_LANGCHAIN4J_OPENAI_API_KEY=<your-openai-api-key>
mvn quarkus:dev
```

Open http://localhost:8080, click **Replay**, then click the recall questions. Each one is sent to both assistants,
and the expected answer is shown below their answers.

## Two profiles

`SummarizingAssistant` uses the **default profile**, configured directly under `quarkus.langchain4j.compaction`:

```properties
quarkus.langchain4j.compaction.strategy=pipeline
quarkus.langchain4j.compaction.pipeline.steps=tool-output-pruning,duplicate-collapse,rolling-summary,token-window
quarkus.langchain4j.compaction.budget.context-window=8000
quarkus.langchain4j.compaction.summary.model-name=summarizer
```

`WindowAssistant` uses a **named profile**, selected with `@CompactionProfile("window")` and configured under
`quarkus.langchain4j.compaction.profiles.window`. A named profile does not inherit from the default one:

```properties
quarkus.langchain4j.compaction.profiles.window.strategy=token-window
quarkus.langchain4j.compaction.profiles.window.budget.context-window=8000
```

Neither interface knows anything else about compaction.

## What to expect

After the replay, both views fit the budget (about 14k → 4k tokens), but not in the same way:

* the **default profile** prunes the old tool outputs and collapses the repeated tool calls. That is enough to reach the
  target, so the pipeline stops there: the summary and the window steps do not run. Every message the customer and
  the agent wrote is still there, so the assistant answers the recall questions.
* the **window profile** keeps only the most recent 36 messages. The customer id, the tracking number, the refund...
  were given in the first 22 messages: the assistant no longer knows them.

Both save about the same number of tokens. Only one keeps what matters: measure recall, not only the gain.

The token panel of each assistant shows the last compaction (messages and tokens before and after) and, over the
questions you asked, the input tokens sent to the model compared with what the full history would have cost. These
numbers come from the `CompactionReport` CDI event fired by the extension (see `TokenGain`).

## Things to try

* Lower `quarkus.langchain4j.compaction.budget.context-window` to `5000`: pruning is no longer enough, and the
  default profile summarizes the old turns with `gpt-4.1-mini`. Check whether the summary kept the answers.
* Replace `token-window` with `anchored-summary` in the `window` profile.
* Ask about a tool output detail (e.g. *what were the tracking events of ORD-1001?*): pruned outputs are gone from the
  view of both assistants.
