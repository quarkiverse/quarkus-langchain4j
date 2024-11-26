///usr/bin/env jbang "$0" "$@" ; exit $?

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.io.IOException;

class updateReadme {

    public static void main(String[] args) throws IOException {
        if (args.length != 1) {
            System.err.println("Usage: 'jbang updateReadme.java newVersion'");
            System.exit(1);
        } else {
            String newVersion = args[0];

            Path readmePath = Path.of("README.md");

            String oldContent = Files.readString(readmePath, StandardCharsets.UTF_8);
            String newContent = oldContent.replaceAll("(?s)<version[^>]*>.*?</version>", "<version>" + newVersion + "</version>");
            Files.write(readmePath, newContent.getBytes(StandardCharsets.UTF_8));
        }
    }
}
