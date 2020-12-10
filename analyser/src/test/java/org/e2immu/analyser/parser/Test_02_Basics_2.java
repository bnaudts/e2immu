
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

import org.e2immu.analyser.analyser.AnalysisStatus;
import org.e2immu.analyser.analyser.EvaluationResult;
import org.e2immu.analyser.analyser.FieldAnalyser;
import org.e2immu.analyser.analyser.VariableProperty;
import org.e2immu.analyser.config.*;
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.model.expression.EmptyExpression;
import org.e2immu.analyser.model.expression.NewObject;
import org.junit.Assert;
import org.junit.Test;

import java.io.IOException;
import java.util.Collection;
import java.util.Map;
import java.util.Set;

public class Test_02_Basics_2 extends CommonTestRunner {

    private static final String TYPE = "org.e2immu.analyser.testexample.Basics_2";
    private static final String STRING_PARAMETER = TYPE + ".setString(String):0:string";
    private static final String STRING_FIELD = TYPE + ".string";
    private static final String THIS = TYPE + ".this";
    private static final String COLLECTION = TYPE + ".add(Collection<String>):0:collection";
    private static final String METHOD_VALUE_ADD = "collection.add(string)";
    private static final String RETURN_GET_STRING = TYPE + ".getString()";

    public Test_02_Basics_2() {
        super(true);
    }

    FieldAnalyserVisitor fieldAnalyserVisitor = d -> {
        if (d.iteration() == 0) {
            Map<AnalysisStatus, Set<String>> expect = Map.of(AnalysisStatus.DONE, Set.of(
                    FieldAnalyser.EVALUATE_INITIALISER,
                    FieldAnalyser.ANALYSE_NOT_MODIFIED_1));
            assertSubMap(expect, d.statuses());
        }
        if (d.iteration() == 1) {
            Map<AnalysisStatus, Set<String>> expect = Map.of(AnalysisStatus.DONE, Set.of(
                    FieldAnalyser.EVALUATE_INITIALISER,
                    FieldAnalyser.ANALYSE_NOT_MODIFIED_1,
                    FieldAnalyser.ANALYSE_FINAL,
                    FieldAnalyser.ANALYSE_FINAL_VALUE,
                    FieldAnalyser.ANALYSE_NOT_NULL));
            assertSubMap(expect, d.statuses());
        }
        if ("string".equals(d.fieldInfo().name)) {
            int expectFinal = d.iteration() == 0 ? Level.DELAY : Level.FALSE;
            Assert.assertEquals(expectFinal, d.fieldAnalysis().getProperty(VariableProperty.FINAL));
            int expectNotNull = d.iteration() == 0 ? Level.DELAY : MultiLevel.NULLABLE;
            Assert.assertEquals(expectNotNull, d.fieldAnalysis().getProperty(VariableProperty.NOT_NULL));
        }
    };

    StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
        if (COLLECTION.equals(d.variableName()) && "add".equals(d.methodInfo().name) && "0".equals(d.statementId())) {
            Assert.assertTrue("Class is " + d.currentValue().getClass(), d.currentValue() instanceof NewObject);
            Assert.assertEquals(MultiLevel.EFFECTIVELY_NOT_NULL, d.getProperty(VariableProperty.NOT_NULL));
            Assert.assertEquals(Level.TRUE, d.getProperty(VariableProperty.MODIFIED));
        }
        if (STRING_FIELD.equals(d.variableName()) && "setString".equals(d.methodInfo().name)) {
            Assert.assertEquals(Level.TRUE, d.getProperty(VariableProperty.ASSIGNED));
        }
        if (THIS.equals(d.variableName()) && "getString".equals(d.methodInfo().name)) {
            Assert.assertEquals(MultiLevel.EFFECTIVELY_NOT_NULL, d.getProperty(VariableProperty.NOT_NULL));
        }
        if (STRING_FIELD.equals(d.variableName()) && "getString".equals(d.methodInfo().name)) {
            Assert.assertEquals(Level.TRUE, d.getProperty(VariableProperty.READ));
            int expectNotNull = d.iteration() == 0 ? Level.DELAY : MultiLevel.NULLABLE;
            Assert.assertEquals(expectNotNull, d.getProperty(VariableProperty.NOT_NULL));
            String expectValue = d.iteration() == 0 ? EmptyExpression.NO_VALUE.toString() : "instance type String";
            Assert.assertEquals(expectValue, d.currentValue().toString());
        }
        if (RETURN_GET_STRING.equals(d.variableName())) {
            int expectNotNull = d.iteration() == 0 ? Level.DELAY : MultiLevel.NULLABLE;
            Assert.assertEquals(expectNotNull, d.getProperty(VariableProperty.NOT_NULL));
        }
    };

    MethodAnalyserVisitor methodAnalyserVisitor = d -> {
        if (TYPE.equals(d.methodInfo().typeInfo.fullyQualifiedName)) {
            FieldInfo string = d.methodInfo().typeInfo.getFieldByName("string", true);
            int fieldModified = d.getFieldAsVariable(string).getProperty(VariableProperty.MODIFIED);

            if ("getString".equals(d.methodInfo().name)) {
                int expectNotNull = d.iteration() == 0 ? Level.DELAY : MultiLevel.NULLABLE;
                Assert.assertEquals(expectNotNull, d.methodAnalysis().getProperty(VariableProperty.NOT_NULL));
                Assert.assertEquals(Level.TRUE, d.getFieldAsVariable(string).getProperty(VariableProperty.READ));
                int expectModified = d.iteration() == 0 ? Level.DELAY : Level.FALSE;
                Assert.assertEquals(expectModified, d.methodAnalysis().getProperty(VariableProperty.MODIFIED));

                // property of the field as variable info in the method
                Assert.assertEquals(Level.FALSE, fieldModified);
            }
            if ("setString".equals(d.methodInfo().name)) {
                Assert.assertEquals(Level.TRUE, d.getFieldAsVariable(string).getProperty(VariableProperty.ASSIGNED));
                Assert.assertEquals(Level.FALSE, fieldModified); // Assigned, but not modified
            }
            if ("add".equals(d.methodInfo().name)) {
                ParameterAnalysis parameterAnalysis = d.parameterAnalyses().get(0);
                Assert.assertEquals(MultiLevel.EFFECTIVELY_NOT_NULL, parameterAnalysis.getProperty(VariableProperty.NOT_NULL));
            }
        }
    };

    EvaluationResultVisitor evaluationResultVisitor = d -> {
        if (d.methodInfo().name.equals("setString") && "0".equals(d.statementId())) {
            Assert.assertEquals(d.evaluationResult().toString(), 2L, d.evaluationResult().getModificationStream().count());
            Assert.assertTrue(d.evaluationResult().toString(), d.haveMarkRead(STRING_PARAMETER));
            Assert.assertTrue(d.evaluationResult().toString(), d.haveMarkRead(THIS));

            EvaluationResult.ExpressionChangeData expressionChange = d.findValueChange(STRING_FIELD);
            Assert.assertEquals("string", expressionChange.value().debugOutput());

            // link to empty set, because String is E2Immutable
            Assert.assertTrue(d.haveLinkVariable(STRING_FIELD, Set.of()));
            Assert.assertEquals("string", d.evaluationResult().value.debugOutput());
        }
        if (d.methodInfo().name.equals("getString") && "0".equals(d.statementId()) && d.iteration() == 0) {
            Assert.assertEquals(d.evaluationResult().toString(), 2L, d.evaluationResult().getModificationStream().count());
            Assert.assertTrue(d.evaluationResult().toString(), d.haveMarkRead(STRING_FIELD));
            Assert.assertTrue(d.evaluationResult().toString(), d.haveMarkRead(THIS));
        }
        if (d.methodInfo().name.equals("add") && "0".equals(d.statementId())) {
            String expectEvalString = d.iteration() == 0 ? EmptyExpression.NO_VALUE.toString() : METHOD_VALUE_ADD;
            Assert.assertEquals(d.evaluationResult().toString(), expectEvalString, d.evaluationResult().value.toString());
        }
    };

    TypeMapVisitor typeMapVisitor = typeMap -> {
        // check that the XML annotations have been read properly, and copied into the correct place
        TypeInfo stringType = typeMap.getPrimitives().stringTypeInfo;
        Assert.assertEquals(MultiLevel.EFFECTIVELY_E2IMMUTABLE, stringType.typeAnalysis.get().getProperty(VariableProperty.IMMUTABLE));

        TypeInfo collection = typeMap.get(Collection.class);
        MethodInfo add = collection.findUniqueMethod("add", 1);
        ParameterInfo p0 = add.methodInspection.get().getParameters().get(0);
        Assert.assertEquals(MultiLevel.EFFECTIVELY_NOT_NULL, p0.parameterAnalysis.get().getProperty(VariableProperty.NOT_NULL));
    };

    @Test
    public void test() throws IOException {
        testClass("Basics_2", 0, 1, new DebugConfiguration.Builder()
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                .addAfterFieldAnalyserVisitor(fieldAnalyserVisitor)
                .addEvaluationResultVisitor(evaluationResultVisitor)
                .addTypeMapVisitor(typeMapVisitor)
                .build());
    }

}
