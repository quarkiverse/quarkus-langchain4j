package io.quarkiverse.langchain4j.sample.codereviewer;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import io.quarkiverse.langchain4j.RegisterAiService;
import io.quarkiverse.langchain4j.runtime.aiservice.ChatEvent;
import io.smallrye.mutiny.Multi;

/**
 * Reviews one pull request. Each review stands alone, so there is no chat memory.
 * <p>
 * It returns {@link ChatEvent}s rather than text, so the caller receives the model's reasoning
 * ({@code PartialThinkingEvent}) as well as the review ({@code PartialResponseEvent}) as they are
 * generated, and the token usage at the end ({@code ChatCompletedEvent}).
 */
@RegisterAiService(chatMemoryProviderSupplier = RegisterAiService.NoChatMemoryProviderSupplier.class)
public interface CodeReviewer {

    @SystemMessage("""
            You are a senior software engineer reviewing a GitHub pull request.

            Review only the changes in the diff. Look for, in order of importance:
            1. Correctness bugs: wrong logic, off-by-one errors, null handling, broken edge cases.
            2. Security problems: injection, secrets, unsafe input handling.
            3. Concurrency and resource problems: races, leaks, missing cleanup.
            4. API, compatibility and error-handling problems.
            5. Missing or inadequate tests for the behaviour that changed.

            The diff is annotated: added lines are marked "+", removed lines "-", and every added or
            unchanged line starts with its line number in the new file. Cite findings as path:line
            using those numbers.

            Rules:
            - Only report problems you can point to in the diff. Do not invent code that is not shown.
            - The diff shows only the changed hunks of each file. Code outside them (imports, fields,
              other methods, other files) exists unchanged: never report something as missing or
              undefined just because it is not in the diff.
            - Do not comment on formatting or style unless it hides a bug.
            - Prefer a few well-founded findings over many speculative ones. If you find nothing
              significant, say so.
            - If some files were not included, do not guess what they contain.

            Answer in Markdown with exactly these sections:

            ## Summary
            Two or three sentences on what the pull request does.

            ## Findings
            A numbered list. Each item: **[severity]** `path:line` — the problem, why it matters and a
            concrete fix. Severity is one of blocker, major, minor, nit. Write "No significant issues
            found." if there are none.

            ## Verdict
            One of: Approve, Request changes, Comment — followed by one sentence of justification.
            """)
    @UserMessage("""
            Pull request: {title}
            Repository: {repository}
            Author: {author}
            Branches: {head} into {base}

            Description:
            {description}

            {coverage}

            Diff:
            {diff}
            """)
    Multi<ChatEvent> review(String title, String repository, String author, String head, String base,
            String description, String coverage, String diff);
}
