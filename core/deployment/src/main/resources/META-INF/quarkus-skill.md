## Evaluation baselines

A failing `isNotWorseThanBaseline()` has one of three causes, named in
its message: the application got worse than the committed baseline,
there is no baseline yet, or the baseline can no longer be compared,
for instance after the trials or strategies changed. Fix a regression
or report it to the user; a missing or incompatible baseline waits for
the user's review. Never accept a baseline, delete a baseline file, or
raise a tolerance to make a test pass unless the user asked for it in
this conversation.

Run `quarkus langchain4j-eval review --json --output target/evaluation-review.json`
and read that file to see which samples moved and why. Standard output can also
carry the launcher's own messages. When `errors` is above 0, the evaluations
failed to run, for instance because a model provider refused the call; report
that instead of judging the scores.
Only pass `--yes` to `quarkus langchain4j-eval accept` or `reject` when the user told you to.
