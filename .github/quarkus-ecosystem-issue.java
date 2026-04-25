//usr/bin/env jbang "$0" "$@" ; exit $?

//DEPS org.kohsuke:github-api:1.326
//DEPS info.picocli:picocli:4.7.6
//DEPS com.fasterxml.jackson:jackson-bom:2.19.2@pom
//DEPS com.fasterxml.jackson.core:jackson-databind
//DEPS com.fasterxml.jackson.datatype:jackson-datatype-jsr310
//DEPS com.fasterxml.jackson.dataformat:jackson-dataformat-yaml

import org.kohsuke.github.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Instant;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Command(name = "report", mixinStandardHelpOptions = true,
		description = "Takes care of updating an issue depending on the status of the build")
class Report implements Runnable {

	private static final String STATUS_MARKER = "<!-- status.quarkus.io/status:";
	private static final String END_OF_MARKER = "-->";
	private static final Pattern STATUS_PATTERN = Pattern.compile(STATUS_MARKER + "(.*?)" + END_OF_MARKER, Pattern.DOTALL);

	private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper(new YAMLFactory());

	static {
		OBJECT_MAPPER.registerModule(new JavaTimeModule());
		OBJECT_MAPPER.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
	}

	@Option(names = "token", description = "Github token to use when calling the Github API")
	private String token;

	@Option(names = "status", description = "The status of the CI run")
	private String status;

	@Option(names = "issueRepo", description = "The repository where the issue resides (i.e. quarkusio/quarkus)")
	private String issueRepo;

	@Option(names = "issueNumber", description = "The issue to update")
	private Integer issueNumber;

	@Option(names = "thisRepo", description = "The repository for which we are reporting the CI status")
	private String thisRepo;

	@Option(names = "runId", description = "The ID of the Github Action run for which we are reporting the CI status")
	private Long runId;

	@Option(names = "quarkusSha", description = "The Git sha of the Quarkus build")
	private String quarkusSha;

	@Option(names = "projectSha", description = "The Git sha of the current project under test")
	private String projectSha;

	@Option(names = "builtFromSource", description = "Whether Quarkus was built from source instead of using a pre-built snapshot")
	private boolean builtFromSource;

	@Option(names = "quarkusBranch", description = "The Quarkus branch used for the build")
	private String quarkusBranch;

	@Option(names = "quarkusVersion", description = "The Quarkus version used for the build")
	private String quarkusVersion;

	@Override
	public void run() {
		try {
			final boolean succeed = "success".equalsIgnoreCase(status);
			if ("cancelled".equalsIgnoreCase(status)) {
				System.out.println("Job status is `cancelled` - exiting");
				System.exit(0);
			}

			System.out.println(String.format("The CI build had status %s.", status));

			final String buildNote = builtFromSource
					? String.format("\n* **Note:** Quarkus was built from source (branch: `%s`, version: `%s`)", quarkusBranch, quarkusVersion)
					: "";

			final GitHub github = new GitHubBuilder().withOAuthToken(token).build();
			final GHRepository repository = github.getRepository(issueRepo);

			final GHIssue issue = repository.getIssue(issueNumber);
			if (issue == null) {
				System.out.println(String.format("Unable to find the issue %s in project %s", issueNumber, issueRepo));
				System.exit(-1);
			} else {
				System.out.println(String.format("Report issue found: %s - %s", issue.getTitle(), issue.getHtmlUrl().toString()));
				System.out.println(String.format("The issue is currently %s", issue.getState().toString()));
			}

			Status existingStatus = extractStatus(issue.getBody());
			State newState = new State(Instant.now(), quarkusSha, projectSha);

			final State firstFailure;
			final State lastFailure;
			final State lastSuccess;

			if (succeed) {
				firstFailure = null;
				lastFailure = null;
				lastSuccess = newState;

				if (isOpen(issue)) {
					// close issue with a comment
					final GHIssueComment comment = issue.comment(String.format("Build fixed:\n* Link to latest CI run: https://github.com/%s/actions/runs/%s%s", thisRepo, runId, buildNote));
					issue.close();
					System.out.println(String.format("Comment added on issue %s - %s, the issue has also been closed", issue.getHtmlUrl().toString(), comment.getHtmlUrl().toString()));
				} else {
					System.out.println("Nothing to do - the build passed and the issue is already closed");
				}
			} else {
				lastSuccess = State.KEEP_EXISTING;
				lastFailure = newState;

				if (isOpen(issue)) {
					final GHIssueComment comment = issue.comment(String.format("The build is still failing:\n* Link to latest CI run: https://github.com/%s/actions/runs/%s%s", thisRepo, runId, buildNote));
					System.out.println(String.format("Comment added on issue %s - %s", issue.getHtmlUrl().toString(), comment.getHtmlUrl().toString()));

					// for old reports, we won't have the first failure previously set so let's set it to the new state as an approximation
					firstFailure = existingStatus.firstFailure() != null ? State.KEEP_EXISTING : newState;
				} else {
					issue.reopen();
					final GHIssueComment comment = issue.comment(String.format("Unfortunately, the build failed:\n* Link to latest CI run: https://github.com/%s/actions/runs/%s%s", thisRepo, runId, buildNote));
					System.out.println(String.format("Comment added on issue %s - %s, the issue has been re-opened", issue.getHtmlUrl().toString(), comment.getHtmlUrl().toString()));

					firstFailure = newState;
				}
			}

			Status newStatus;
			if (existingStatus != null) {
				newStatus = new Status(Instant.now(), !succeed, thisRepo, runId, quarkusSha, projectSha,
						firstFailure == State.KEEP_EXISTING ? existingStatus.firstFailure() : firstFailure,
						lastFailure == State.KEEP_EXISTING ? existingStatus.lastFailure() : lastFailure,
						lastSuccess == State.KEEP_EXISTING ? existingStatus.lastSuccess() : lastSuccess);
			} else {
				newStatus = new Status(Instant.now(), !succeed, thisRepo, runId, quarkusSha, projectSha,
						firstFailure, lastFailure, lastSuccess);
			}

			issue.setBody(appendStatusInformation(issue.getBody(), newStatus));
		}
		catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	private static boolean isOpen(GHIssue issue) {
		return (issue.getState() == GHIssueState.OPEN);
	}

	public static void main(String... args) {
		int exitCode = new CommandLine(new Report()).execute(args);
		System.exit(exitCode);
	}

	public String appendStatusInformation(String body, Status status) {
		try {
			String descriptor = STATUS_MARKER + "\n" + OBJECT_MAPPER.writeValueAsString(status) + END_OF_MARKER;

			if (!body.contains(STATUS_MARKER)) {
				return body + "\n\n" + descriptor;
			}

			return STATUS_PATTERN.matcher(body).replaceFirst(descriptor);
		} catch (Exception e) {
			throw new IllegalStateException("Unable to update the status descriptor", e);
		}
	}

	public Status extractStatus(String body) {
		if (body == null || body.isBlank()) {
			return null;
		}

		Matcher matcher = STATUS_PATTERN.matcher(body);
		if (!matcher.find()) {
			return null;
		}

		try {
			return OBJECT_MAPPER.readValue(matcher.group(1), Status.class);
		} catch (Exception e) {
			System.out.println("Warning: unable to extract Status from issue body: " + e.getMessage());
			return null;
		}
	}

	public record Status(Instant updatedAt, boolean failure, String repository, Long runId,
		String quarkusSha, String projectSha, State firstFailure, State lastFailure, State lastSuccess) {
    }

	public record State(Instant date, String quarkusSha, String projectSha) {

		/**
		 * Sentinel value to keep the existing value.
		 */
		private static final State KEEP_EXISTING = new State(null, null, null);
	}
}