/*
 * e2immu-analyser: code analyser for effective and eventual immutability
 * Copyright 2020, Bart Naudts, https://www.e2immu.org
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 */

package org.e2immu.analyser.parser;

import ch.qos.logback.classic.Level;
import org.e2immu.analyser.model.MethodInfo;
import org.e2immu.analyser.model.TypeInfo;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;

public class TestMethodOverloadAndSuperTypes {
    private static final Logger LOGGER = LoggerFactory.getLogger(TestMethodOverloadAndSuperTypes.class);
    public static final String SRC_TEST_JAVA_ORG_E2IMMU_ANALYSER = "src/test/java/org/e2immu/analyser/";

    @BeforeClass
    public static void beforeClass() {
        org.e2immu.analyser.util.Logger.configure(Level.INFO);
    }

    @Test
    public void test() throws IOException {
        Parser parser = new Parser();
        List<SortedType> types = parser.parseJavaFiles(new File(SRC_TEST_JAVA_ORG_E2IMMU_ANALYSER + "testexample/MethodOverload.java"));
        LOGGER.info("Have {} types", types.size());

        // method: hashCode
        TypeInfo methodOverload = types.get(0).typeInfo;
        MethodInfo hashCode = methodOverload.typeInspection.get().methods
                .stream().filter(m -> m.name.equals("hashCode")).findFirst().orElseThrow();
        List<MethodInfo> overloadsOfHashCode = methodOverload.overloads(hashCode, parser.getTypeContext());
        LOGGER.info("Overloads of hashCode: {}", overloadsOfHashCode);
        Assert.assertEquals("[java.lang.Object.hashCode()]", overloadsOfHashCode.toString());

        // method: C1.method(int)
        TypeInfo c1 = methodOverload.typeInspection.get().subTypes.stream().filter(t -> t.simpleName.equals("C1")).findFirst().orElseThrow();

        List<TypeInfo> superTypesC1 = c1.superTypes(parser.getTypeContext());
        Assert.assertEquals("[java.lang.Object, org.e2immu.analyser.testexample.MethodOverload.I1]", superTypesC1.toString());
        List<TypeInfo> directSuperTypesC1 = c1.directSuperTypes(parser.getTypeContext());
        Assert.assertEquals("[java.lang.Object, org.e2immu.analyser.testexample.MethodOverload.I1]", directSuperTypesC1.toString());


        LOGGER.info("Distinguishing names of C1 methods: " +
                c1.typeInspection.get().methods.stream().map(MethodInfo::distinguishingName).collect(Collectors.joining(", ")));
        MethodInfo m1 = c1.typeInspection.get().methods.stream().filter(m -> m.distinguishingName()
                .equals("org.e2immu.analyser.testexample.MethodOverload.C1.method(int)")).findFirst().orElseThrow();
        List<MethodInfo> overloadsOfM1 = c1.overloads(m1, parser.getTypeContext());
        LOGGER.info("Overloads of m1: {}", overloadsOfM1);
        Assert.assertEquals("[org.e2immu.analyser.testexample.MethodOverload.I1.method(int)]", overloadsOfM1.toString());

        // method C2.toString()
        TypeInfo c2 = methodOverload.typeInspection.get().subTypes.stream().filter(t -> t.simpleName.equals("C2")).findFirst().orElseThrow();
        LOGGER.info("Distinguishing names of C2 methods: " +
                c2.typeInspection.get().methods.stream().map(MethodInfo::distinguishingName).collect(Collectors.joining(", ")));

        List<TypeInfo> superTypesC2 = c2.superTypes(parser.getTypeContext());
        Assert.assertEquals("[org.e2immu.analyser.testexample.MethodOverload.C1, java.lang.Object, org.e2immu.analyser.testexample.MethodOverload.I1]", superTypesC2.toString());
        List<TypeInfo> directSuperTypesC2 = c2.directSuperTypes(parser.getTypeContext());
        Assert.assertEquals("[org.e2immu.analyser.testexample.MethodOverload.C1]", directSuperTypesC2.toString());

        MethodInfo toString = c2.typeInspection.get().methods.stream().filter(m -> m.name.equals("toString")).findFirst().orElseThrow();
        List<MethodInfo> overloadsOfToString = c2.overloads(toString, parser.getTypeContext());
        LOGGER.info("Overloads of toString: {}", overloadsOfToString);
        Assert.assertEquals("[org.e2immu.analyser.testexample.MethodOverload.C1.toString(), java.lang.Object.toString()]",
                overloadsOfToString.toString());
    }

}
