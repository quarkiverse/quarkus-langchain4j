package io.quarkiverse.langchain4j.mcp.test.apicurio;

import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.ext.web.client.HttpResponse;
import io.vertx.ext.web.client.WebClient;

public class McpServerHelper {

    private static final Logger log = LoggerFactory.getLogger(McpServerHelper.class);

    public static Process startServerHttp(String scriptName, int port) throws Exception {
        skipTestsIfJbangNotAvailable();
        String path = getPathToScript(scriptName);
        List<String> command = new ArrayList<>();
        command.add(getJBangCommand());
        command.add("--quiet");
        command.add("--fresh");
        command.add("-Dquarkus.http.port=" + port);
        command.add("-Dquarkus.http.ssl-port=" + (port + 1000));
        command.add(path);
        log.info("Starting the MCP server using command: " + command);
        Process process = new ProcessBuilder().command(command).inheritIO().start();
        waitForPort(port, 120);
        log.info("MCP server has started");
        return process;
    }

    static String getPathToScript(String script) {
        InputStream scriptAsStream = ClassLoader.getSystemResourceAsStream(script);
        if (scriptAsStream == null) {
            throw new RuntimeException("Unable to find script " + script);
        } else if (scriptAsStream instanceof BufferedInputStream) {
            return ClassLoader.getSystemResource(script)
                    .getFile()
                    .substring(isWindows() ? 1 : 0)
                    .replace("/", File.separator);
        } else {
            File folder = new File(System.getProperty("java.io.tmpdir"), "mcp-test-scripts");
            folder.mkdirs();
            folder.deleteOnExit();
            Path tmpFilePath = Path.of(folder.getAbsolutePath(), script);
            try {
                if (!java.nio.file.Files.exists(tmpFilePath)) {
                    log.info("Copying " + ClassLoader.getSystemResource(script) + " to " + tmpFilePath);
                    java.nio.file.Files.copy(scriptAsStream, tmpFilePath);
                }
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            return tmpFilePath.toString();
        }
    }

    static String getJBangCommand() {
        String command = System.getProperty("jbang.command");
        if (command == null || command.isEmpty()) {
            command = isWindows() ? "jbang.cmd" : "jbang";
        }
        return command;
    }

    public static void skipTestsIfJbangNotAvailable() {
        String command = getJBangCommand();
        try {
            new ProcessBuilder().command(command, "--version").start().waitFor();
        } catch (Exception e) {
            String message = "jbang is not available (could not execute command '" + command
                    + "'), MCP tests will be skipped.";
            log.warn(message, e);
            assumeTrue(false, message);
        }
    }

    private static void waitForPort(int port, int timeoutSeconds) throws Exception {
        Vertx vertx = Vertx.vertx();
        try {
            WebClient webClient = WebClient.create(vertx);
            try {
                long start = System.currentTimeMillis();
                while (System.currentTimeMillis() - start < timeoutSeconds * 1000L) {
                    try {
                        HttpResponse<Buffer> result = webClient.get(port, "localhost", "/").send().toCompletionStage()
                                .toCompletableFuture().get();
                        if (result != null) {
                            return;
                        }
                    } catch (Exception e) {
                        log.info("MCP server not started yet...");
                        TimeUnit.SECONDS.sleep(1);
                    }
                }
                throw new TimeoutException("Port " + port + " did not open within " + timeoutSeconds + " seconds");
            } finally {
                webClient.close();
            }
        } finally {
            vertx.close();
        }
    }

    private static boolean isWindows() {
        return System.getProperty("os.name").toLowerCase().contains("windows");
    }

    public static void destroyProcessTree(Process process) {
        if (isWindows()) {
            ProcessHandle handle = process.toHandle();
            handle.descendants().forEach(ProcessHandle::destroyForcibly);
            process.destroyForcibly();
        } else {
            process.destroyForcibly();
        }
    }
}
