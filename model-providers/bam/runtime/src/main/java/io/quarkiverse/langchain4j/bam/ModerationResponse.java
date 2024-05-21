package io.quarkiverse.langchain4j.bam;

import java.util.List;
import java.util.Optional;

public record ModerationResponse(List<Results> results) {

    public record ModerateScore(Float score, boolean flagged, boolean success, Position position) {
    };

    public record Position(int start, int end) {
    };

    public record Results(List<ModerateScore> hap, List<ModerateScore> socialBias) {

        public Optional<Position> isHap() {
            return Optional.ofNullable(toModerate(hap));
        }

        public Optional<Position> isSocialBias() {
            return Optional.ofNullable(toModerate(socialBias));
        }

        private Position toModerate(List<ModerateScore> list) {

            if (list == null || list.size() == 0)
                return null;

            for (ModerateScore score : list) {
                if (score.flagged)
                    return score.position;
            }

            return null;
        }
    };
}
