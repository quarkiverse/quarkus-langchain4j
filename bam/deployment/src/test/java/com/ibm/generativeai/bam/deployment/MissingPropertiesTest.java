package com.ibm.generativeai.bam.deployment;

import static org.junit.jupiter.api.Assertions.fail;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkus.test.QuarkusUnitTest;
import io.smallrye.config.ConfigValidationException;

public class MissingPropertiesTest {

    @RegisterExtension
    static QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .setExpectedException(ConfigValidationException.class)
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class));

    @Test
    void test() {
        fail("Should not be called");
    }
}
