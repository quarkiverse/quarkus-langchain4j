package io.quarkiverse.langchain4j.testing.evaluation.cli;

import java.nio.file.Path;

import io.quarkiverse.langchain4j.testing.evaluation.review.PendingResult;
import io.quarkiverse.langchain4j.testing.evaluation.review.Review;
import picocli.CommandLine.Command;

@Command(name = "accept", description = "Accept pending results as the new baselines", mixinStandardHelpOptions = true)
final class AcceptCommand extends DecisionCommand {

    AcceptCommand(Path root, Terminal terminal) {
        super(root, terminal, "Accept", "Accepted", "accepting");
    }

    @Override
    void apply(PendingResult result) {
        Review.accept(result);
    }

    @Override
    String detail(PendingResult result) {
        return relative(result.baselineFile());
    }
}
