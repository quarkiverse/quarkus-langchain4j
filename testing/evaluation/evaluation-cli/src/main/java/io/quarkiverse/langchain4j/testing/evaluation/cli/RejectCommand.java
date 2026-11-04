package io.quarkiverse.langchain4j.testing.evaluation.cli;

import java.nio.file.Path;

import io.quarkiverse.langchain4j.testing.evaluation.review.PendingResult;
import io.quarkiverse.langchain4j.testing.evaluation.review.Review;
import picocli.CommandLine.Command;

@Command(name = "reject", description = "Discard pending results, keeping the baselines as they are", mixinStandardHelpOptions = true)
final class RejectCommand extends DecisionCommand {

    RejectCommand(Path root, Terminal terminal) {
        super(root, terminal, "Reject", "Rejected", "rejecting");
    }

    @Override
    void apply(PendingResult result) {
        Review.reject(result);
    }

    @Override
    String detail(PendingResult result) {
        return null;
    }
}
