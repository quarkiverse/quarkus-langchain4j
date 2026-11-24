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

## Evaluating the profiles

The UI shows one conversation; `CompactionEvaluationTest` measures the two profiles on two other recorded conversations,
in `src/test/resources/conversations`: a trip planning session (~9k tokens) and a production incident investigation
(~13k tokens, with large log and metric outputs). Each comes with recall questions and the facts expected in the
answers.

```shell
export QUARKUS_LANGCHAIN4J_OPENAI_API_KEY=<your-openai-api-key>
mvn test
```

For each conversation, the test replays it through the `default` and `window` profiles, then asks the questions to the
assistants' model (`gpt-4.1`) with each compacted view, with the full history and with no history. A separate `judge`
model grades each compacted answer against the full-history answer:

```java
@Inject
@AnswerModel            // the model of the assistants
ChatModel assistant;

@Inject
@ModelName("judge")     // %test.quarkus.langchain4j.openai.judge.* in application.properties
@JudgeModel
ChatModel judge;

@CompactionEvaluation(conversation = "conversations/incident.jsonl",
        questions = "conversations/incident.questions.json",
        profiles = { "default", "window" }, minSavings = 0.25)
void incident(CompactionEvaluationReport report) {
    assertTrue(report.recall("window") < report.recall("default"), "window recall");
}
```

The reports (recall, prompt tokens and savings, compactions, and every missed answer) are written to
`target/compaction-evaluation`. The test is skipped when no API key is set; a run makes up to about a hundred model calls (answers, judge
calls when the expected facts are not found verbatim, and summaries).

## Comparing the strategies

`StrategyEvaluationTest` evaluates each strategy on its own, plus the default pipeline, on a recorded production
outage (`src/test/resources/conversations/outage.jsonl`, 81 messages, ~8.6k tokens, 73% tool outputs, from 14:12 to
16:45). Each strategy has its own test-only profile in `application.properties` (`%test...profiles.<strategy>`), all
with the same 6k-token window, so the conversation is compacted several times. The questions probe facts placed on
purpose: in the first message (incident id, commander, the eu-west-1 freeze), only in tool outputs (commit, previous
pool limit, the card vault on-call, read twice), in the middle (rollback decision, impact) and at the end (follow-up
ticket).

It makes a few hundred model calls, so it only runs on request:

```shell
export QUARKUS_LANGCHAIN4J_OPENAI_API_KEY=<your-openai-api-key>
mvn test -Dtest=StrategyEvaluationTest -Devaluate-strategies=true
```

`target/compaction-evaluation/StrategyEvaluationTest-summary.md` compares token savings, recall and the time spent
compacting (including summarizer and embedding calls); `StrategyEvaluationTest-outage.md` lists every answer and miss.

`duplicate-collapse` is not evaluated alone: it only removes identical tool calls and cannot bring the conversation
under the window by itself. `relevance` selects old turns by similarity to the latest user message when compaction
runs, not to the recall question, so this evaluation underestimates it.

## Things to try

* Lower `quarkus.langchain4j.compaction.budget.context-window` to `5000`: pruning is no longer enough, and the
  default profile summarizes the old turns with `gpt-4.1-mini`. Check whether the summary kept the answers.
* Replace `token-window` with `anchored-summary` in the `window` profile.
* Ask about a tool output detail (e.g. *what were the tracking events of ORD-1001?*): pruned outputs are gone from the
  view of both assistants.
