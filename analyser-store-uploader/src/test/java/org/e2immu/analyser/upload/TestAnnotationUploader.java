/*
 * e2immu: a static code analyser for effective and eventual immutability
 * Copyright 2020-2021, Bart Naudts, https://www.e2immu.org
 *
 * This program is free software: you can redistribute it and/or modify it under the
 * terms of the GNU Lesser General Public License as published by the Free Software
 * Foundation, either version 3 of the License, or (at your option) any later version.
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE.  See the GNU Lesser General Public License for
 * more details. You should have received a copy of the GNU Lesser General Public
 * License along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.e2immu.analyser.upload;

import org.e2immu.analyser.analyser.VariableProperty;
import org.e2immu.analyser.config.AnnotatedAPIConfiguration;
import org.e2immu.analyser.config.Configuration;
import org.e2immu.analyser.config.DebugConfiguration;
import org.e2immu.analyser.config.InputConfiguration;
import org.e2immu.analyser.inspector.TypeContext;
import org.e2immu.analyser.model.Level;
import org.e2immu.analyser.model.TypeInfo;
import org.e2immu.analyser.output.Formatter;
import org.e2immu.analyser.output.FormattingOptions;
import org.e2immu.analyser.output.OutputBuilder;
import org.e2immu.analyser.parser.Input;
import org.e2immu.analyser.parser.Parser;
import org.e2immu.analyser.resolver.SortedType;
import org.e2immu.analyser.util.UpgradableBooleanMap;
import org.e2immu.analyser.visitor.TypeMapVisitor;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

public class TestAnnotationUploader {
    private static final Logger LOGGER = LoggerFactory.getLogger(TestAnnotationUploader.class);

    public static final String BASICS_0 = "org.e2immu.analyser.upload.example.Basics_0";
    public static final String[] CLASSPATH_WITHOUT_ANNOTATED_APIS = {"build/classes/java/main",
            "jmods/java.base.jmod", "../analyser/src/main/resources/annotations/minimal"};

    @Test
    public void test() throws IOException {
        TypeMapVisitor typeMapVisitor = typeMap -> {
            TypeInfo string = typeMap.get(String.class);
            assertEquals(Level.TRUE, string.typeAnalysis.get().getProperty(VariableProperty.CONTAINER));
        };

        Configuration configuration = new Configuration.Builder()
                .setDebugConfiguration(new DebugConfiguration.Builder()
                        .addTypeMapVisitor(typeMapVisitor).build())
                .addDebugLogTargets("ANALYSER,INSPECT,RESOLVE,DELAYED")
                .setAnnotatedAPIConfiguration(new AnnotatedAPIConfiguration.Builder()
                        .addAnnotatedAPISourceDirs("../annotatedAPIs/src/main/java")
                        .build())
                .setInputConfiguration(new InputConfiguration.Builder()
                        .addSources("src/test/java")
                        .addRestrictSourceToPackages(BASICS_0)
                        .addClassPath(CLASSPATH_WITHOUT_ANNOTATED_APIS)
                        .addClassPath(Input.JAR_WITH_PATH_PREFIX + "org/slf4j")
                        .addClassPath(Input.JAR_WITH_PATH_PREFIX + "org/junit/jupiter/api")
                        .addClassPath(Input.JAR_WITH_PATH_PREFIX + "ch/qos/logback/core/spi")
                        .build())
                .build();
        configuration.initializeLoggers();
        Parser parser = new Parser(configuration);
        Parser.RunResult runResult = parser.run();
        for (SortedType sortedType : runResult.sourceSortedTypes()) {
            OutputBuilder outputBuilder = sortedType.primaryType().output();
            Formatter formatter = new Formatter(FormattingOptions.DEFAULT);
            LOGGER.info("Stream:\n{}\n", formatter.write(outputBuilder));
        }

        TypeContext typeContext = parser.getTypeContext();

        TypeInfo basics = typeContext.typeMapBuilder.get(BASICS_0);
        UpgradableBooleanMap<TypeInfo> typesReferredTo = basics.typesReferenced();
        assertTrue(typesReferredTo.get(typeContext.getPrimitives().stringTypeInfo));

        AnnotationUploader annotationUploader = new AnnotationUploader(configuration.uploadConfiguration(),
                parser.getTypeContext().typeMapBuilder.getE2ImmuAnnotationExpressions());
        Map<String, String> map = annotationUploader.createMap(Set.of(basics));
        map.forEach((k, v) -> System.out.println(k + " --> " + v));

        assertEquals("notmodified-m", map.get(BASICS_0 + ".getExplicitlyFinal()"));
        assertEquals("modified-m", map.get(BASICS_0 + ".add(java.lang.String)"));
        assertEquals("dependent-m", map.get(BASICS_0 + ".Basics_0(java.util.Set<java.lang.String>)"));
        assertEquals("modified-f", map.get(BASICS_0 + ":strings"));
     //   assertNull(map.get(BASICS_0 + ":strings java.util.Set")); // container, but that's not a dynamic type

        assertEquals("final-f", map.get(BASICS_0 + ":explicitlyFinal"));
     //   assertEquals("e2container-tf", map.get(BASICS_0 + ":explicitlyFinal java.lang.String"));

        assertEquals("modified-p", map.get(BASICS_0 + ".Basics_0(java.util.Set<java.lang.String>)#0"));

        assertEquals("e1immutable-t", map.get(BASICS_0));
        annotationUploader.writeMap(map);
    }
}
