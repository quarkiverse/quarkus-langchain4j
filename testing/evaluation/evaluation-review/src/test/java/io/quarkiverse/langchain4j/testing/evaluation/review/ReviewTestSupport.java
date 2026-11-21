package io.quarkiverse.langchain4j.testing.evaluation.review;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;

final class ReviewTestSupport {

    static final String REPORT = """
            {"trials":1,"strategies":["s"],"evaluations":[
              {"name":"A","passed":true,"advisory":false,"expectedOutput":"yes","actualOutput":"yep"},
              {"name":"A","passed":false,"advisory":true,"expectedOutput":"ignored","actualOutput":"nope"},
              {"name":"B","passed":false,"advisory":false,"expectedOutput":"no","actualOutput":"maybe"}]}
            """;

    private ReviewTestSupport() {
    }

    static Comparison comparison(ResultStatus status, List<SampleChange> samples, List<String> added,
            List<String> removed) {
        return new Comparison(status, 5.0, OptionalDouble.of(-10), OptionalDouble.of(1), OptionalDouble.of(-12),
                OptionalDouble.of(-8), samples, added, removed, Optional.empty(), OptionalDouble.empty());
    }

    static Path pending(Path module, String buildDir, String id, String report, Comparison comparison) {
        Path file = module.resolve(buildDir).resolve("evaluation-baselines").resolve(id + ".json");
        PendingFile.write(file, id, "run", "src/test/resources/evaluation-baselines/" + id + ".json", report,
                comparison);
        return file;
    }

    static void touch(Path file) throws Exception {
        Files.createDirectories(file.getParent());
        Files.writeString(file, "{}");
    }
}
