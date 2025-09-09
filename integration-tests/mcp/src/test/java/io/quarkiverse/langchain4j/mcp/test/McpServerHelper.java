package io.quarkiverse.langchain4j.mcp.test;

import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.apache.commons.io.FileUtils;
import org.assertj.core.util.Files;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.ext.web.client.HttpResponse;
import io.vertx.ext.web.client.WebClient;

public class McpServerHelper {

    private static final Logger log = LoggerFactory.getLogger(McpServerHelper.class);

    public static Process startServerHttp(String scriptName) throws Exception {
        return startServerHttp(scriptName, 8082);
    }

    public static Process startServerHttp(String scriptName, int port) throws Exception {
        return startServerHttp(scriptName, port, port + 1000, new String[] {});
    }

    public static Process startServerHttp(String scriptName, int port, int sslPort, String[] extraArgs) throws Exception {
        skipTestsIfJbangNotAvailable();
        String path = getPathToScript(scriptName);
        List<String> command = new ArrayList<>();
        command.add(getJBangCommand());
        command.add("--quiet");
        command.add("--fresh");
        command.addAll(Arrays.asList(extraArgs));
        command.add("-Dquarkus.http.ssl-port=" + sslPort);
        command.add("-Dquarkus.http.port=" + port);
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
            // the script path points at a regular file,
            // so just return its full path
            return ClassLoader.getSystemResource(script)
                    .getFile()
                    .substring(isWindows() ? 1 : 0)
                    .replace("/", File.separator);
        } else {
            // the script path points at a file that is inside a JAR
            // so we unzip it into a temporary file
            File folder = Files.newTemporaryFolder();
            folder.deleteOnExit();
            Path tmpFilePath = Path.of(folder.getAbsolutePath(), script);
            try {
                log.info("Temporarily copying " + ClassLoader.getSystemResource(script) + " to " + tmpFilePath);
                java.nio.file.Files.copy(scriptAsStream, tmpFilePath);
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
                    + "', MCP integration tests will be skipped. "
                    + "The command may be overridden via the system property 'jbang.command'";
            log.warn(message, e);
            assumeTrue(false, message);
        }
    }

    /**
     * This is a hacky way to get the tests working in the Platform CI where the JBang scripts
     * are not in 'src/test/resources' as the test expects them, but rather inside the test suite's
     * test-jar artifact, so this method temporarily copies them to src/test/resources.
     */
    static void copyMcpServerScriptToSrcTestResourcesIfItsNotThereAlready(String script) {
        Path path = Path.of("src", "test", "resources", script);
        if (!java.nio.file.Files.exists(path)) {
            InputStream scriptAsStream = ClassLoader.getSystemResourceAsStream(script);
            if (scriptAsStream == null) {
                throw new RuntimeException("Unable to find script " + script);
            }
            try {
                log.info("Temporarily copying " + ClassLoader.getSystemResource(script) + " to " + path);
                FileUtils.forceMkdirParent(path.toFile());
                java.nio.file.Files.copy(scriptAsStream, path);
                path.toFile().deleteOnExit();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }

    private static void waitForPort(int port, int timeoutSeconds) throws Exception {
        Vertx vertx = Vertx.vertx();
        try {
            WebClient webClient = WebClient.create(vertx);
            try {
                long start = System.currentTimeMillis();
                while (System.currentTimeMillis() - start < timeoutSeconds * 1000) {
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
}
