package io.quarkiverse.langchain4j.sample.codereviewer;

import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import jakarta.enterprise.context.control.ActivateRequestContext;
import jakarta.inject.Inject;

import org.aesh.command.Command;
import org.aesh.command.CommandDefinition;
import org.aesh.command.CommandResult;
import org.aesh.command.invocation.CommandInvocation;
import org.aesh.command.option.Argument;
import org.aesh.command.option.Option;
import org.jboss.logging.Logger;
import org.kohsuke.github.GHFileNotFoundException;
import org.kohsuke.github.HttpException;

import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.output.TokenUsage;
import io.quarkiverse.langchain4j.runtime.aiservice.ChatEvent;
import io.quarkiverse.langchain4j.sample.codereviewer.GitHubPullRequests.PullRequest;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.subscription.Cancellable;

@CommandDefinition(name = "gh-pr-code-reviewer", generateHelp = true,
        description = "Review a GitHub pull request with an LLM: JitLLM on the local GPU by default.")
public class ReviewCommand implements Command<CommandInvocation> {

    private static final Logger LOG = Logger.getLogger(ReviewCommand.class);

    @Argument(description = "Pull request link, e.g. https://github.com/owner/repo/pull/123", required = true)
    String link;

    @Option(name = "max-diff-chars",
            description = "Diff budget in characters. Defaults to gh-pr-code-reviewer.max-diff-chars (60000).")
    Integer maxDiffChars;

    @Inject
    GitHubPullRequests github;

    @Inject
    CodeReviewer reviewer;

    @Inject
    CodeReviewerConfig config;

    @Override
    @ActivateRequestContext
    public CommandResult execute(CommandInvocation invocation) {
        PullRequestLink pr;
        try {
            pr = PullRequestLink.parse(link);
        } catch (IllegalArgumentException e) {
            invocation.println(e.getMessage());
            return CommandResult.FAILURE;
        }

        LOG.infof("Fetching %s from the GitHub API", pr);
        PullRequest info;
        try {
            info = github.fetch(pr);
        } catch (IOException e) {
            invocation.println(describe(pr, e));
            return CommandResult.FAILURE;
        }

        int budget = maxDiffChars != null ? maxDiffChars : config.maxDiffChars();
        DiffFormatter.Result formatted = DiffFormatter.format(info.files(), budget);
        LOG.infof("Prepared %,d characters of annotated diff from %d of %d file(s) (budget %,d)",
                formatted.text().length(), formatted.filesIncluded(), info.changedFiles(), budget);

        invocation.println(pr + " — " + info.title());
        invocation.println(info.url() + " by @" + info.author() + ", " + info.head() + " into " + info.base());
        invocation.println(String.format("%d file(s) changed, +%d -%d; reviewing %d file(s)",
                info.changedFiles(), info.additions(), info.deletions(), formatted.filesIncluded()));
        printList(invocation, "Skipped (lock, generated, binary or too large)", formatted.skipped());
        printList(invocation, "Not reviewed (over the diff budget)", formatted.overBudget());
        invocation.println("");

        if (formatted.filesIncluded() == 0) {
            invocation.println("Nothing to review.");
            return CommandResult.SUCCESS;
        }
        return review(invocation, pr, info, formatted);
    }

    private CommandResult review(CommandInvocation invocation, PullRequestLink pr, PullRequest info,
            DiffFormatter.Result formatted) {
        LOG.info("Starting the review. The model reads the whole prompt before its first token; with a model"
                + " that runs locally, the first request also loads it.");
        ReviewPrinter printer = new ReviewPrinter(invocation::print, System.console() != null);
        long start = System.nanoTime();
        AtomicLong firstToken = new AtomicLong();

        // Loading the model and reading a long prompt take a while with nothing to show: report progress
        Duration interval = config.progressInterval();
        Cancellable progress = Multi.createFrom().ticks().startingAfter(interval).every(interval)
                .subscribe().with(tick -> {
                    if (firstToken.get() == 0) {
                        LOG.infof("... %.0f s, no output yet", seconds(start, System.nanoTime()));
                    }
                });

        ChatResponse response;
        try {
            response = reviewer.review(
                    info.title(),
                    pr.repository(),
                    info.author(),
                    info.head(),
                    info.base(),
                    description(info.body()),
                    coverage(formatted),
                    formatted.text())
                    .onItem().invoke(event -> {
                        if (event instanceof ChatEvent.PartialThinkingEvent thinking) {
                            firstToken.compareAndSet(0, System.nanoTime());
                            printer.thinking(thinking.getText());
                        } else if (event instanceof ChatEvent.PartialResponseEvent partial) {
                            firstToken.compareAndSet(0, System.nanoTime());
                            printer.response(partial.getChunk());
                        }
                    })
                    .filter(ChatEvent.ChatCompletedEvent.class::isInstance)
                    .map(event -> ((ChatEvent.ChatCompletedEvent) event).getChatResponse())
                    .collect().last()
                    .await().indefinitely();
        } catch (RuntimeException e) {
            LOG.error("The review failed", e);
            return CommandResult.FAILURE;
        } finally {
            progress.cancel();
        }

        long end = System.nanoTime();
        long first = firstToken.get() == 0 ? end : firstToken.get();
        invocation.println(String.format("%n%n--- Review completed in %.1f s ---", seconds(start, end)));
        TokenUsage usage = response == null ? null : response.tokenUsage();
        if (usage != null && usage.inputTokenCount() != null && usage.outputTokenCount() != null) {
            // Times, not a rate: a provider that does not stream the reasoning prints nothing until the
            // review starts, so the first output says nothing about when generation began.
            LOG.infof("Prompt: %,d tokens. Generated: %,d tokens (reasoning and review). First output after"
                    + " %.1f s, review completed in %.1f s", usage.inputTokenCount(), usage.outputTokenCount(),
                    seconds(start, first), seconds(start, end));
        }
        return CommandResult.SUCCESS;
    }

    private String description(String body) {
        if (body == null || body.isBlank()) {
            return "(none)";
        }
        int max = config.maxDescriptionChars();
        return body.length() <= max ? body : body.substring(0, max) + "\n[truncated]";
    }

    private static String coverage(DiffFormatter.Result formatted) {
        if (formatted.skipped().isEmpty() && formatted.overBudget().isEmpty()) {
            return "The diff below is complete.";
        }
        StringBuilder note = new StringBuilder("The diff below is partial.");
        if (!formatted.overBudget().isEmpty()) {
            note.append(" Not included, too large to fit: ").append(String.join(", ", formatted.overBudget())).append('.');
        }
        if (!formatted.skipped().isEmpty()) {
            note.append(" Not included, lock/generated/binary or too large for GitHub to show: ")
                    .append(String.join(", ", formatted.skipped())).append('.');
        }
        return note.toString();
    }

    private static void printList(CommandInvocation invocation, String label, List<String> paths) {
        if (!paths.isEmpty()) {
            invocation.println(label + ": " + String.join(", ", paths));
        }
    }

    private static double seconds(long from, long to) {
        return (to - from) / 1e9;
    }

    private static String describe(PullRequestLink pr, IOException e) {
        if (e instanceof GHFileNotFoundException) {
            return "Pull request " + pr + " not found. For a private repository, set GITHUB_TOKEN.";
        }
        if (e instanceof HttpException http && (http.getResponseCode() == 401 || http.getResponseCode() == 403
                || http.getResponseCode() == 429)) {
            return "GitHub refused the request (HTTP " + http.getResponseCode()
                    + "). If you hit the rate limit, set GITHUB_TOKEN.";
        }
        return "Could not read " + pr + " from GitHub: " + e.getMessage();
    }
}
