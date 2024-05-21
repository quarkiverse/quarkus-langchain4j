package io.quarkiverse.langchain4j.bam;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Supplier;

import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.model.moderation.Moderation;
import dev.langchain4j.model.moderation.ModerationModel;
import dev.langchain4j.model.output.Response;
import io.quarkiverse.langchain4j.bam.ModerationResponse.Position;

public class BamModerationModel extends BamModel implements ModerationModel {

    public BamModerationModel(Builder config) {
        super(config);
    }

    @Override
    public Response<Moderation> moderate(String text) {

        if (Objects.isNull(hap) && Objects.isNull(socialBias))
            return Response.from(Moderation.notFlagged());

        var request = ModerationRequest.of(text, hap, socialBias);
        var response = client.moderations(request, token, version).results().get(0);

        Moderation moderation = response.isHap()
                .or(new Supplier<Optional<? extends ModerationResponse.Position>>() {

                    @Override
                    public Optional<? extends ModerationResponse.Position> get() {
                        return response.isSocialBias();
                    }
                }).map(new Function<ModerationResponse.Position, Moderation>() {

                    @Override
                    public Moderation apply(Position position) {
                        return Moderation.flagged(text.substring(position.start(), position.end() + 1));
                    }
                }).orElse(Moderation.notFlagged());

        return Response.from(moderation);
    }

    @Override
    public Response<Moderation> moderate(List<ChatMessage> messages) {

        if (Objects.isNull(hap) && Objects.isNull(socialBias))
            return Response.from(Moderation.notFlagged());

        for (ChatMessage message : messages) {

            if (!messagesToModerate.contains(message.type()))
                continue;

            Response<Moderation> response = moderate(message.text());
            if (response.content().flagged())
                return response;
        }

        return Response.from(Moderation.notFlagged());
    }
}
