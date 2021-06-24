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

package org.e2immu.analyser.parser;

import org.e2immu.analyser.analyser.LinkedVariables;
import org.e2immu.analyser.analyser.VariableProperty;
import org.e2immu.analyser.config.DebugConfiguration;
import org.e2immu.analyser.model.Level;
import org.e2immu.analyser.model.MultiLevel;
import org.e2immu.analyser.model.ParameterAnalysis;
import org.e2immu.analyser.model.ParameterInfo;
import org.e2immu.analyser.model.variable.FieldReference;
import org.e2immu.analyser.model.variable.ReturnVariable;
import org.e2immu.analyser.visitor.*;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class Test_61_OutputBuilderSimplified extends CommonTestRunner {

    public Test_61_OutputBuilderSimplified() {
        super(true);
    }

    @Test
    public void test_0() throws IOException {
        testClass("OutputBuilderSimplified_0", 0, 0, new DebugConfiguration.Builder()
                .build());
    }


    @Test
    public void test_1() throws IOException {
        testClass("OutputBuilderSimplified_1", 0, 0, new DebugConfiguration.Builder()
                .build());
    }

    @Test
    public void test_2() throws IOException {
        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if ("go".equals(d.methodInfo().name)) {
                if (d.variable() instanceof ReturnVariable) {
                    if ("0.0.0".equals(d.statementId())) {
                        int expectExtImm = MultiLevel.NOT_INVOLVED;
                        assertEquals(expectExtImm, d.getProperty(VariableProperty.EXTERNAL_IMMUTABLE));

                        int expectExtNN = MultiLevel.NOT_INVOLVED;
                        assertEquals(expectExtNN, d.getProperty(VariableProperty.EXTERNAL_NOT_NULL));
                    }
                }
                if (d.variable() instanceof ParameterInfo p && "o1".equals(p.name)) {
                    assertEquals(Level.DELAY, d.getProperty(VariableProperty.CONTEXT_IMMUTABLE_DELAY));

                    int cImm = d.getProperty(VariableProperty.CONTEXT_IMMUTABLE);
                    if ("0".equals(d.statementId())) {
                        String expectedValue = d.iteration() <= 1 ? "<p:o1>" : "nullable instance type OutputBuilderSimplified_2/*@Identity*/";
                        assertEquals(expectedValue, d.currentValue().toString());

                        String expectedLinked = d.iteration() <= 1 ? LinkedVariables.DELAY_STRING : "";
                        assertEquals(expectedLinked, d.variableInfo().getLinkedVariables().toString());

                        int expectContextMod = d.iteration() <= 1 ? Level.DELAY : Level.FALSE;
                        assertEquals(expectContextMod, d.getProperty(VariableProperty.CONTEXT_MODIFIED));

                        // links have not been established
                        int expectContextImm = d.iteration() <= 2 ? Level.DELAY : MultiLevel.MUTABLE;
                        assertEquals(expectContextImm, cImm);
                    }
                }
                if (d.variable() instanceof ParameterInfo p && "o2".equals(p.name)) {
                    assertEquals(Level.DELAY, d.getProperty(VariableProperty.CONTEXT_IMMUTABLE_DELAY));

                    int cImm = d.getProperty(VariableProperty.CONTEXT_IMMUTABLE);
                    if ("0".equals(d.statementId())) {
                        assertEquals(MultiLevel.MUTABLE, cImm);
                    }
                    if ("1".equals(d.statementId())) {
                        String expectedValue = d.iteration() <= 1 ? "<p:o2>" : "nullable instance type OutputBuilderSimplified_2";
                        assertEquals(expectedValue, d.currentValue().toString());

                        String expectedLinked = d.iteration() <= 1 ? LinkedVariables.DELAY_STRING : "";
                        assertEquals(expectedLinked, d.variableInfo().getLinkedVariables().toString());

                        int expectContextMod = d.iteration() <= 1 ? Level.DELAY : Level.FALSE;
                        assertEquals(expectContextMod, d.getProperty(VariableProperty.CONTEXT_MODIFIED));

                        // links have not been established
                        int expectContextImm = d.iteration() <= 2 ? Level.DELAY : MultiLevel.MUTABLE;
                        assertEquals(expectContextImm, cImm);
                    }
                    if ("0.0.0".equals(d.statementId())) {
                        int expectImm = d.iteration() <= 2 ? Level.DELAY : MultiLevel.EFFECTIVELY_E1IMMUTABLE_NOT_E2IMMUTABLE;
                        assertEquals(expectImm, d.getProperty(VariableProperty.IMMUTABLE));
                    }
                }
            }
        };

        TypeAnalyserVisitor typeAnalyserVisitor = d -> {
            if ("OutputBuilderSimplified_2".equals(d.typeInfo().simpleName)) {
                int expectImmutable = d.iteration() <= 1 ? Level.DELAY : MultiLevel.EFFECTIVELY_E1IMMUTABLE_NOT_E2IMMUTABLE;
                assertEquals(expectImmutable, d.typeAnalysis().getProperty(VariableProperty.IMMUTABLE));
            }
        };

        // SUMMARY: in iteration 4, o2 should have IMMUTABLE = @E1Immutable
        MethodAnalyserVisitor methodAnalyserVisitor = d -> {
            if ("go".equals(d.methodInfo().name)) {
                for (ParameterAnalysis param : d.parameterAnalyses()) {
                    // no direct link with a parameter which has to be/will be dynamically immutable
                    assertEquals(MultiLevel.NOT_INVOLVED, param.getProperty(VariableProperty.EXTERNAL_IMMUTABLE));

                    int expectContextImm = d.iteration() <= 3 ? Level.DELAY : MultiLevel.MUTABLE;
                    assertEquals(expectContextImm, param.getProperty(VariableProperty.CONTEXT_IMMUTABLE));

                    int expectImm = d.iteration() <= 3 ? Level.DELAY : MultiLevel.EFFECTIVELY_E1IMMUTABLE_NOT_E2IMMUTABLE;
                    assertEquals(expectImm, param.getProperty(VariableProperty.IMMUTABLE));
                }
            }
            if ("isEmpty".equals(d.methodInfo().name)) {
                int expectMm = d.iteration() == 0 ? Level.DELAY : Level.FALSE;
                assertEquals(expectMm, d.methodAnalysis().getProperty(VariableProperty.MODIFIED_METHOD));
            }
        };

        testClass("OutputBuilderSimplified_2", 0, 0, new DebugConfiguration.Builder()
                .addAfterTypePropertyComputationsVisitor(typeAnalyserVisitor)
                .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .build());
    }

    @Test
    public void test_3() throws IOException {
        EvaluationResultVisitor evaluationResultVisitor = d -> {
            if ("combiner".equals(d.methodInfo().name)) {
                if ("0".equals(d.statementId())) {
                    String expectValue = d.iteration() <= 1 ? "<m:isEmpty>" : "a.list.isEmpty()";
                    assertEquals(expectValue, d.evaluationResult().value().toString());
                }
            }
        };
        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if ("combiner".equals(d.methodInfo().name)) {
                if (d.variable() instanceof ParameterInfo p && "a".equals(p.name)) {
                    if ("0".equals(d.statementId()) || "1".equals(d.statementId())) {
                        String expected = d.iteration() <= 1 ? "<p:a>"
                                : "nullable instance type OutputBuilderSimplified_3/*@Identity*/";
                        assertEquals(expected, d.currentValue().toString(),
                                "Statement " + d.statementId() + " it " + d.iteration());
                    }
                    if ("2.0.0".equals(d.statementId())) {
                        String expected = d.iteration() <= 1 ? "<p:a>"
                                : "instance type OutputBuilderSimplified_3/*@Identity*/";
                        assertEquals(expected, d.currentValue().toString(),
                                "Statement " + d.statementId() + " it " + d.iteration());
                    }
                }
                if (d.variable() instanceof ReturnVariable) {
                    if ("0".equals(d.statementId())) {
                        String expectValue = d.iteration() <= 1
                                ? "<m:isEmpty>?b:<return value>"
                                : "a.list.isEmpty()?b:<return value>";
                        assertEquals(expectValue, d.currentValue().toString());
                    }
                    if ("1".equals(d.statementId()) || "2".equals(d.statementId())) {
                        String expectValue = d.iteration() <= 1
                                ? "<m:isEmpty>?<p:a>:<m:isEmpty>?b:<return value>"
                                : "b.list.isEmpty()?a:a.list.isEmpty()?b:<return value>";
                        assertEquals(expectValue, d.currentValue().toString());
                    }
                }
                if (d.variable() instanceof FieldReference fr && "list".equals(fr.fieldInfo.name)) {
                    if ("a.list".equals(d.variable().toString())) {
                        if ("0".equals(d.statementId())) {
                            assertTrue(d.iteration() > 0);
                            // question 1: why does a.list appear while the evaluation is still <m:isEmpty> ?
                            // because a.list has been identified, but aspects of .isEmpty() have not been cleared yet
                            assertEquals(MultiLevel.EFFECTIVELY_NOT_NULL, d.getProperty(VariableProperty.CONTEXT_NOT_NULL));
                        }
                    }
                }
            }
        };
        testClass("OutputBuilderSimplified_3", 0, 0, new DebugConfiguration.Builder()
                .addEvaluationResultVisitor(evaluationResultVisitor)
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .build());
    }

    /*
     There is no pressing need to turn the NewObject into a Lambda (that's another war to fight, plus, in OutputBuilder
     we have a Collector, which is not a functional interface).

     */
    @Test
    public void test_4() throws IOException {
        MethodAnalyserVisitor methodAnalyserVisitor = d -> {
            if ("j2".equals(d.methodInfo().name)) {
                assertEquals(Level.FALSE, d.methodAnalysis().getProperty(VariableProperty.MODIFIED_METHOD));
                assertEquals("instance type $1", d.methodAnalysis().getSingleReturnValue().toString());
                assertTrue(d.methodAnalysis().methodLevelData().linksHaveBeenEstablished.isSet());

                //  assertEquals(MultiLevel.EFFECTIVELY_E2IMMUTABLE, d.methodAnalysis().getProperty(VariableProperty.IMMUTABLE));
            }
            if ("j1".equals(d.methodInfo().name)) {
                // single statement: return j2(); links have not been established
                // assertTrue(d.methodAnalysis().methodLevelData().linksHaveBeenEstablished.isSet());

                //  assertEquals("instance type $1", d.methodAnalysis().getSingleReturnValue().toString());
                //   assertEquals(Level.FALSE, d.methodAnalysis().getProperty(VariableProperty.MODIFIED_METHOD));
            }
        };

        TypeAnalyserVisitor typeAnalyserVisitor = d -> {
            if ("$1".equals(d.typeInfo().simpleName)) {
                //   assertEquals(MultiLevel.EFFECTIVELY_E2IMMUTABLE, d.typeAnalysis().getProperty(VariableProperty.IMMUTABLE));
            }
        };
        testClass("OutputBuilderSimplified_4", 0, 0, new DebugConfiguration.Builder()
                .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                .addAfterTypePropertyComputationsVisitor(typeAnalyserVisitor)
                .build());
    }

    @Test
    public void test_5() throws IOException {
        MethodAnalyserVisitor methodAnalyserVisitor = d -> {
            if ("j2".equals(d.methodInfo().name)) {
                assertEquals(Level.FALSE, d.methodAnalysis().getProperty(VariableProperty.MODIFIED_METHOD));
                assertEquals("null", d.methodAnalysis().getSingleReturnValue().toString());
            }
            if ("j1".equals(d.methodInfo().name)) {
                assertEquals(Level.FALSE, d.methodAnalysis().getProperty(VariableProperty.MODIFIED_METHOD));
            }
        };
        testClass("OutputBuilderSimplified_5", 0, 0, new DebugConfiguration.Builder()
                .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                .build());
    }

    @Test
    public void test_6() throws IOException {
        // method should be marked static
        testClass("OutputBuilderSimplified_6", 1, 0, new DebugConfiguration.Builder()
                .build());
    }

    // delay, but not minimized; see 9
    @Test
    public void test_7() throws IOException {
        testClass("OutputBuilderSimplified_7", 0, 0, new DebugConfiguration.Builder()
                .build());
    }

    // picked up a bug in Identity computation
    @Test
    public void test_8() throws IOException {
        // unused parameter
        testClass("OutputBuilderSimplified_8", 0, 1, new DebugConfiguration.Builder()
                .build());
    }

    // again, simplifying to find the infinite loop; this time, there's a modifying method
    @Test
    public void test_9() throws IOException {
        FieldAnalyserVisitor fieldAnalyserVisitor = d -> {
            if ("countMid".equals(d.fieldInfo().name)) {
                int expectMom = d.iteration() == 0 ? Level.DELAY : Level.TRUE;
                assertEquals(expectMom, d.fieldAnalysis().getProperty(VariableProperty.MODIFIED_OUTSIDE_METHOD));
            }
        };
        testClass("OutputBuilderSimplified_9", 0, 0, new DebugConfiguration.Builder()
                .addAfterFieldAnalyserVisitor(fieldAnalyserVisitor)
                .build());
    }

    // function instead of consumer
    @Test
    public void test_10() throws IOException {
        testClass("OutputBuilderSimplified_10", 0, 0, new DebugConfiguration.Builder()
                .build());
    }

    // again, simplifying to find the infinite loop; this time, everything is immutable
    // but when/how do we reach that conclusion?
    @Test
    public void test_11() throws IOException {
        TypeAnalyserVisitor typeAnalyserVisitor = d -> {
            if ("OutputBuilderSimplified_11".equals(d.typeInfo().simpleName)) {
                assertEquals(MultiLevel.EFFECTIVELY_E2IMMUTABLE, d.typeAnalysis().getProperty(VariableProperty.IMMUTABLE));
            }
            if ("$1".equals(d.typeInfo().simpleName)) {
                int expectImm = d.iteration() == 0 ? Level.DELAY : MultiLevel.EFFECTIVELY_E2IMMUTABLE;
                assertEquals(expectImm, d.typeAnalysis().getProperty(VariableProperty.IMMUTABLE));
            }
            if ("$2".equals(d.typeInfo().simpleName)) {
                int expectImm = d.iteration() <= 1 ? Level.DELAY : MultiLevel.EFFECTIVELY_E2IMMUTABLE;
                assertEquals(expectImm, d.typeAnalysis().getProperty(VariableProperty.IMMUTABLE));
            }
        };

        FieldAnalyserVisitor fieldAnalyserVisitor = d -> {
            if ("countMid".equals(d.fieldInfo().name)) {
                int expectMom = d.iteration() == 0 ? Level.DELAY : Level.FALSE;
                assertEquals(expectMom, d.fieldAnalysis().getProperty(VariableProperty.MODIFIED_OUTSIDE_METHOD));
            }
        };

        MethodAnalyserVisitor methodAnalyserVisitor = d -> {
            if ("get".equals(d.methodInfo().name) && "$1".equals(d.methodInfo().typeInfo.simpleName)) {
                int expectMm = d.iteration() == 0 ? Level.DELAY : Level.FALSE;
                assertEquals(expectMm, d.methodAnalysis().getProperty(VariableProperty.MODIFIED_METHOD));
            }
        };

        // ignoring the result of a non-modifying method call
        testClass("OutputBuilderSimplified_11", 0, 1, new DebugConfiguration.Builder()
                .addAfterTypePropertyComputationsVisitor(typeAnalyserVisitor)
                .addAfterFieldAnalyserVisitor(fieldAnalyserVisitor)
                .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                .build());
    }
}
