package io.quarkiverse.langchain4j.agentic;

import io.quarkus.picocli.runtime.annotations.TopCommand;
import picocli.CommandLine;

@TopCommand
@CommandLine.Command(mixinStandardHelpOptions = true, subcommands = {WriteCommand.class})
public class EntryCommand {
}
