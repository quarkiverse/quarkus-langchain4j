package io.quarkiverse.langchain4j.sample.codereviewer;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import jakarta.enterprise.context.ApplicationScoped;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.kohsuke.github.GHPullRequest;
import org.kohsuke.github.GHPullRequestFileDetail;
import org.kohsuke.github.GitHub;
import org.kohsuke.github.GitHubBuilder;

/**
 * Reads a pull request and its changed files through the GitHub API client
 * (<a href="https://github.com/quarkiverse/quarkus-github-api">quarkus-github-api</a>).
 * <p>
 * Public repositories need no credentials. Setting {@code GITHUB_TOKEN} gives access to private ones
 * and lifts the unauthenticated rate limit.
 */
@ApplicationScoped
public class GitHubPullRequests {

    /** A pull request, and each changed file with its patch. */
    public record PullRequest(String title, String body, String author, String head, String base,
            int additions, int deletions, int changedFiles, String url, List<FileChange> files) {
    }

    /**
     * One changed file. {@code patch} is the unified diff of its hunks, or {@code null} when GitHub
     * gives none: a binary file, or one too large to show.
     */
    public record FileChange(String path, String status, String previousPath, String patch) {
    }

    private final GitHub github;

    GitHubPullRequests(@ConfigProperty(name = "github.token") Optional<String> token) throws IOException {
        GitHubBuilder builder = new GitHubBuilder();
        token.filter(t -> !t.isBlank()).ifPresent(builder::withOAuthToken);
        this.github = builder.build();
    }

    public PullRequest fetch(PullRequestLink link) throws IOException {
        GHPullRequest pr = github.getRepository(link.repository()).getPullRequest(link.number());
        List<FileChange> files = new ArrayList<>();
        for (GHPullRequestFileDetail file : pr.listFiles()) {
            files.add(new FileChange(file.getFilename(), file.getStatus(), file.getPreviousFilename(), file.getPatch()));
        }
        return new PullRequest(pr.getTitle(), pr.getBody(), pr.getUser().getLogin(), pr.getHead().getLabel(),
                pr.getBase().getRef(), pr.getAdditions(), pr.getDeletions(), pr.getChangedFiles(),
                pr.getHtmlUrl().toString(), files);
    }
}
