package org.e2immu.analyser.parser.kvstore;

import ch.qos.logback.classic.Level;
import org.e2immu.analyser.config.Configuration;
import org.e2immu.analyser.config.DebugConfiguration;
import org.e2immu.analyser.config.InputConfiguration;
import org.e2immu.analyser.parser.*;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.List;

import static org.e2immu.analyser.util.Logger.LogTarget.*;

public class TestKVStore {
    private static final Logger LOGGER = LoggerFactory.getLogger(TestKVStore.class);

    @BeforeClass
    public static void beforeClass() {
        org.e2immu.analyser.util.Logger.configure(Level.INFO);
        org.e2immu.analyser.util.Logger.activate(ANALYSER, INSPECT, RESOLVE, METHOD_CALL);
    }

    @Test
    public void test() throws IOException {
        Configuration configuration = new Configuration.Builder()
                .setInputConfiguration(new InputConfiguration.Builder()
                        .addSources("../annotation-store/src/main/java")
                        .addRestrictSourceToPackages("org.e2immu.kvstore")
                        .addClassPath(InputConfiguration.DEFAULT_CLASSPATH)
                        .addClassPath(Input.JAR_WITH_PATH_PREFIX + "com/google/common/collect")
                        .addClassPath(Input.JAR_WITH_PATH_PREFIX + "org/junit")
                        .addClassPath(Input.JAR_WITH_PATH_PREFIX + "org/slf4j")
                        .addClassPath(Input.JAR_WITH_PATH_PREFIX + "ch/qos/logback/core/spi")
                        .addClassPath(Input.JAR_WITH_PATH_PREFIX + "io/vertx/core")
                        .addClassPath(Input.JAR_WITH_PATH_PREFIX + "io/vertx/config")
                        .addClassPath(Input.JAR_WITH_PATH_PREFIX + "io/vertx/ext")
                        .build())
                .build();

        Parser parser = new Parser(configuration);
        List<SortedType> types = parser.run();
        for (SortedType sortedType : types) {
            LOGGER.info("Stream:\n{}", sortedType.typeInfo.stream());
        }
        parser.getMessages().forEach(message -> {
            LOGGER.info(message.toString());
        });
    }

}
