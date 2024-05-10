package org.acme.example.bam.chat;

import jakarta.inject.Inject;

import org.acme.example.bam.AiService;
import org.acme.example.bam.BAMAiService;
import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;

@QuarkusTest
public class DeploymentTest {

    @Inject
    BAMAiService bamService;

    @Inject
    AiService watsonxService;

    @Test
    void firstNamedModel() {
    }
}
