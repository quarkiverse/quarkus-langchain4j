package io.quarkiverse.langchain4j.sample.codereviewer;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The pull request a GitHub link points to.
 */
public record PullRequestLink(String owner, String repo, int number) {

    /** {@code https://github.com/owner/repo/pull/123}, optionally followed by {@code /files}, a query or an anchor. */
    private static final Pattern URL = Pattern.compile(
            "^(?:https?://)?(?:www\\.)?github\\.com/([\\w.-]+)/([\\w.-]+)/pull/(\\d+)(?:[/?#].*)?$");

    /**
     * @throws IllegalArgumentException when {@code link} is not a GitHub pull request link
     */
    public static PullRequestLink parse(String link) {
        Matcher m = URL.matcher(link.strip());
        if (!m.matches()) {
            throw new IllegalArgumentException("Not a GitHub pull request link: " + link
                    + " (expected https://github.com/<owner>/<repo>/pull/<number>)");
        }
        return new PullRequestLink(m.group(1), m.group(2), Integer.parseInt(m.group(3)));
    }

    public String repository() {
        return owner + "/" + repo;
    }

    @Override
    public String toString() {
        return owner + "/" + repo + "#" + number;
    }
}
