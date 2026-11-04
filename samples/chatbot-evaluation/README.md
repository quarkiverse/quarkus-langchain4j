# Chatbot Evaluation Testing Sample

This sample demonstrates comprehensive evaluation testing for AI-powered chatbots using the Quarkus LangChain4j evaluation framework.

## Overview

The sample includes a simple customer support chatbot and showcases various evaluation approaches:

- **Imperative Testing** - Traditional programmatic evaluation using `Scorer`
- **Fluent Builder API** - Readable, chainable evaluation definitions
- **Declarative Testing** - Annotation-driven evaluations with `@EvaluationTest`
- **AI as Judge** - Using an LLM to evaluate response quality
- **Semantic Similarity** - Embedding-based similarity comparison
- **Test Suite-Level Reporting** - Aggregated reports across multiple tests
- **Baseline Comparison** - Detect score drops beyond a chosen tolerance against a committed baseline

## Trying out baseline comparison

`BaselineEvaluationTest` ends with `assertThat(report).isNotWorseThanBaseline()`. It compares each run with the
baseline committed under `src/test/resources/evaluation-baselines/`, and writes the result of every run under
`target/evaluation-baselines/` as a pending result. The test never changes a committed file; the
`quarkus langchain4j-eval` command does, once you accept a pending result. Alternatively, use the
[manual workflow](../../docs/modules/ROOT/pages/testing.adoc#reviewing-results-manually).
There is no baseline when you first check out the sample, so the walkthrough starts by creating one.
Run the commands below from this directory; the sample uses the Maven wrapper of the repository root.
The tests call OpenAI, so they are skipped by default; pass `-DskipTests=false`.
Set `OPENAI_API_KEY` in your environment or in a local `.env` file, and check the model settings in
`src/main/resources/application.properties` against the models available to your account. Both the chatbot and
the judge call OpenAI, so this walkthrough requires API credits. Do not commit your key.
Model output varies between runs, so the scores, warnings and samples named below may differ from yours.

1. Run the test. It fails and tells you what to do:

   ```
   ../../mvnw test -DskipTests=false -Dtest=BaselineEvaluationTest
   ```
   ```
   No baseline yet for io.quarkiverse.langchain4j.sample.chatbot.BaselineEvaluationTest/customerSupportIsNotWorseThanBaseline, 6 samples pending review.

   Pending result: target/evaluation-baselines/io.quarkiverse.langchain4j.sample.chatbot.BaselineEvaluationTest/customerSupportIsNotWorseThanBaseline.json
   Baseline: src/test/resources/evaluation-baselines/io.quarkiverse.langchain4j.sample.chatbot.BaselineEvaluationTest/customerSupportIsNotWorseThanBaseline.json
   Run `quarkus langchain4j-eval review` to accept or reject it.
   Manual review (no CLI): https://docs.quarkiverse.io/quarkus-langchain4j/dev/testing.html#reviewing-results-manually
   ```

2. Review it. The command is an optional Quarkus CLI plugin; until a release ships it in the extension's metadata, add it once
   (the plugin is built from `testing/evaluation/evaluation-cli`, so install that module first):

   ```
   quarkus plugin add io.quarkiverse.langchain4j:quarkus-langchain4j-eval-cli:runner:jar:999-SNAPSHOT
   ```

   Without the Quarkus CLI, `jbang io.quarkiverse.langchain4j:quarkus-langchain4j-eval-cli:999-SNAPSHOT:runner review`
   runs the same command; the Quarkus CLI runs the plugin through JBang too, and installs JBang when it is missing.
   Use the released version instead of `999-SNAPSHOT` once there is one.

   ```
   quarkus langchain4j-eval review
   ```

   For each pending result, `review` prints the test id and a table with one row per sample, with the score in the
   baseline and the received score (a first run has no baseline, so the samples are listed as `new`). Then it asks what
   to do with the keys:

   ```
   [a] accept  [r] reject  [d] show responses  [s] skip  [q] quit
   ```

   Press `d` to see the expected output and each trial's response, and check the numbers look right. Press `a`: it
   prints `Accepted <id>` and writes the baseline.
   If the result reports provider errors, such as authentication or exhausted credits, resolve those and rerun
   before accepting. Errors do not measure the chatbot's answer quality.

3. The baseline is now `src/test/resources/evaluation-baselines/io.quarkiverse.langchain4j.sample.chatbot.BaselineEvaluationTest/customerSupportIsNotWorseThanBaseline.json`.
   Open it to see what it records (a summary of the scores per sample), and commit it so the diff in your pull request
   shows which samples moved.
   Run the test again. It passes if the mean per-sample score drop is no more than 5 percentage points.
   If it fails, inspect the responses to distinguish variation between runs from a change in behavior.

4. Make the bot worse on purpose. In `CustomerSupportBot`, delete the whole `Company Information:` block from the
   system message, as a careless prompt refactoring might. Run the test again. If the mean score drop exceeds the
   tolerance, it fails with this message, where
   `<...>` stands for values that depend on the model's answers:

   ```
   Expected the score not to drop by more than 5.00 points against the baseline, but the mean per-sample difference is <n> points (95% CI: <low> to <high>) over 6 paired samples

   Regressed samples:
     - <sample name>: <baseline>% -> <received>%
   ```

   The test may also log `The advisory evaluations dropped by <n> points per sample against the baseline. They do not
   fail the test, but are worth a look in the report.` The confidence interval describes the variation between
   paired sample differences. Only the mean difference controls pass/fail, so a run can fail even if the interval
   includes zero.

   Run `quarkus langchain4j-eval review` again. The table now shows each sample's baseline and received score, and `d`
   shows the expected output and each trial's response. Press `r` to reject: it prints `Rejected <id>` and
   deletes the pending result. The baseline is untouched. Then restore the system message.

   Also inspect answers that passed. Semantic similarity can approve a plausible but factually wrong answer, such
   as one that changes the return policy from 30 to 10 days. The AI judge may notice, but it is advisory in this
   sample and cannot fail the test. When particular facts are requirements, add a blocking strategy that checks
   them explicitly.

5. Add a sample to `customer-support-samples.yaml` and run the test. The existing samples are compared as usual, so
   the test passes if their mean score drop stays within the tolerance, and logs `1 new samples pending review: <sample name>`. Run
   `quarkus langchain4j-eval review`: the new sample is listed with `new` as its baseline. Press `a`: accepting makes the baseline
   this run, so the new sample is added and the existing samples take the scores of this run. A test run alone never
   changes the baseline.

6. For an intended change that moves many scores, accept it the same way, or run
   `quarkus langchain4j-eval accept --all`, which asks for confirmation. `reject` works the same way.

### Coding agents

An agent runs the tests, then `quarkus langchain4j-eval review --json --output target/evaluation-review.json` to
read the pending results from that file. Without a
terminal, `review` prints one line per result (id and status) instead of the interactive table, `accept` refuses
with `Not accepting: no terminal to confirm with. Run this yourself, or pass --yes if a person asked for it.`, and
`reject` with `Not rejecting: ...`. Pass `--yes` only when a person asked for it.

Things to try from there:

- Change `trials` in the test. The baseline was produced with a different count and the comparison refuses it until
  you accept a new one.
- Switch `quarkus.langchain4j.openai.chat-model.model-name` in `application.properties`. The baseline then compares
  the new model with the old one, sample by sample.

