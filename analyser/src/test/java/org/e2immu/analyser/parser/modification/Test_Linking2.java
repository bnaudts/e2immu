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

package org.e2immu.analyser.parser.modification;

import org.e2immu.analyser.analyser.DV;
import org.e2immu.analyser.analyser.Property;
import org.e2immu.analyser.analyser.Stage;
import org.e2immu.analyser.analyser.VariableInfo;
import org.e2immu.analyser.analysis.MethodAnalysis;
import org.e2immu.analyser.analysis.ParameterAnalysis;
import org.e2immu.analyser.config.DebugConfiguration;
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.model.expression.ConstructorCall;
import org.e2immu.analyser.model.variable.ReturnVariable;
import org.e2immu.analyser.parser.CommonTestRunner;
import org.e2immu.analyser.visitor.EvaluationResultVisitor;
import org.e2immu.analyser.visitor.MethodAnalyserVisitor;
import org.e2immu.analyser.visitor.StatementAnalyserVariableVisitor;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.e2immu.analyser.parser.VisitorTestSupport.IterationInfo.*;
import static org.junit.jupiter.api.Assertions.*;

public class Test_Linking2 extends CommonTestRunner {

    public Test_Linking2() {
        super(true);
    }

    @Test
    public void test_0() throws IOException {
        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            switch (d.methodInfo().name) {
                case "m1" -> {
                    if ("2".equals(d.statementId()) && d.variable() instanceof ReturnVariable) {
                        assertLinked(d, it(0, "selection:0"));
                    }
                }
                case "m2", "m2b" -> {
                    if ("0".equals(d.statementId())) {
                        if (d.variable() instanceof ParameterInfo pi && "selector".equals(pi.name)) {
                            assertDv(d, MultiLevel.EFFECTIVELY_IMMUTABLE_HC_DV, Property.IMMUTABLE);
                            assertDv(d, MultiLevel.INDEPENDENT_HC_DV, Property.INDEPENDENT);
                        }
                    }
                    if ("1.0.0".equals(d.statementId()) && "x".equals(d.variableName())) {
                        assertLinked(d, it(0, 1, "selection:-1,selector:-1,xs:-1"),
                                it(2, "selection:4,selector:4,xs:4"));
                        assertSingleLv(d, 2, 0, "*-4-0");
                        assertSingleLv(d, 2, 1, "*-4-0"); // selector is not @Independent!
                        assertSingleLv(d, 2, 2, "*-4-0");
                    }
                    if ("2".equals(d.statementId()) && d.variable() instanceof ReturnVariable) {
                        assertLinked(d, it(0, 1, "selection:0,selector:-1,xs:-1"),
                                it(2, "selection:0,selector:4,xs:4"));
                    }
                }
                case "m2c" -> {
                    if ("0".equals(d.statementId())) {
                        if (d.variable() instanceof ParameterInfo pi && "selector".equals(pi.name)) {
                            assertDv(d, MultiLevel.EFFECTIVELY_IMMUTABLE_HC_DV, Property.IMMUTABLE);
                            assertDv(d, MultiLevel.INDEPENDENT_HC_DV, Property.INDEPENDENT);
                        }
                        if ("independentSelector".equals(d.variableName())) {
                            assertEquals("new $1(){public boolean test(@Independent(contract=true) X x){return selector.test(x);}}",
                                    d.currentValue().toString());
                            ParameterizedType pt = d.currentValue().returnType();
                            assertEquals("Type org.e2immu.analyser.parser.modification.testexample.Linking_2.$1",
                                    pt.toString());
                            assertNotNull(pt.typeInfo);
                            TypeInspection typeInspection = pt.typeInfo.typeInspection.get();
                            assertEquals("Type java.util.function.Predicate<X>", typeInspection.interfacesImplemented().get(0).toString());
                            assertLinked(d, it(0, ""));

                            MethodInfo test = typeInspection.methods().get(0);
                            assertEquals("test", test.name);
                            MethodAnalysis testAna = d.context().getAnalyserContext().getMethodAnalysis(test);
                            assertEquals(DV.FALSE_DV, testAna.getProperty(Property.MODIFIED_METHOD));
                            ParameterAnalysis p0Ana = testAna.getParameterAnalyses().get(0);
                            assertEquals(MultiLevel.INDEPENDENT_DV, p0Ana.getProperty(Property.INDEPENDENT));
                        }
                    }
                    if ("2.0.0".equals(d.statementId())) {
                        if ("x".equals(d.variableName())) {
                          /*  assertLinked(d, it0("independentSelector:-1,selection:-1,selector:-1,xs:-1"),
                                    it1("independentSelector:-1,selection:-1,xs:-1"),
                                    it(2, "selection:4,selector:4,xs:4"));
                            assertSingleLv(d, 2, 0, "*-4-0");
                            assertSingleLv(d, 2, 1, "*-4-0"); // selector is not @Independent!
                            assertSingleLv(d, 2, 2, "*-4-0");*/
                        }
                    }
                    if ("independentSelector".equals(d.variableName())) {
                        VariableInfo vii = d.variableInfoContainer().getPreviousOrInitial();
                        DV cmi = vii.getProperty(Property.CONTEXT_MODIFIED);
                        VariableInfo vie = d.variableInfoContainer().best(Stage.EVALUATION);
                        DV cme = vie.getProperty(Property.CONTEXT_MODIFIED);
                        VariableInfo vim = d.variableInfoContainer().best(Stage.MERGE);
                        DV cmm = vim.getProperty(Property.CONTEXT_MODIFIED);

                        if ("2".equals(d.statementId())) {
                            assertInstanceOf(ConstructorCall.class, vii.getValue());
                            assertDv(d, 0, DV.FALSE_DV, cmi);
                            assertTrue(d.variableInfoContainer().hasEvaluation());
                            assertDv(d, 0, DV.FALSE_DV, cme);
                            assertTrue(d.variableInfoContainer().hasMerge());
                            assertDv(d, 1, DV.TRUE_DV, cmm);
                        }
                        if ("2.0.0".equals(d.statementId())) {
                            assertEquals(DV.FALSE_DV, cmi);
                            assertEquals("<vl:independentSelector>", vii.getValue().toString());
                            assertTrue(d.variableInfoContainer().hasEvaluation());
                            assertEquals("<vl:independentSelector>", vie.getValue().toString());
                            assertDv(d, 1, DV.TRUE_DV, cme);
                            assertTrue(d.variableInfoContainer().hasMerge());
                            assertDv(d, 1, DV.TRUE_DV, cmm);
                        }
                        if ("2.0.0.0.0".equals(d.statementId())) {
                            assertDv(d, 1, DV.TRUE_DV, cmi);
                            assertTrue(d.variableInfoContainer().hasEvaluation());
                            assertDv(d, 1, DV.TRUE_DV, cme);
                            assertFalse(d.variableInfoContainer().hasMerge());
                        }
                    }
                    if ("3".equals(d.statementId()) && d.variable() instanceof ReturnVariable) {
                        assertLinked(d, it0("independentSelector:-1,selection:0,selector:-1,xs:-1"),
                                it1("independentSelector:-1,selection:0,xs:-1"),
                                it(2, "selection:0,selector:4,xs:4"));
                    }
                }
                case "m3" -> {
                    if ("1.0.0".equals(d.statementId()) && "m".equals(d.variableName())) {
                        assertLinked(d, it(0, 1, "ms:-1,selection:-1,selector:-1"),
                                it(2, "ms:4,selection:4,selector:4"));
                        assertSingleLv(d, 2, 0, "*M-4-0M");
                    }
                    if ("2".equals(d.statementId()) && d.variable() instanceof ReturnVariable) {
                        assertCurrentValue(d, 2,
                                "ms.isEmpty()?new ArrayList<>()/*0==this.size()*/:selector.test(nullable instance 1 type M)?instance 1.0.0.0.0 type List<M>:instance 1 type List<M>");
                        assertLinked(d, it(0, 1, "ms:-1,selection:0,selector:-1"),
                                it(2, "ms:4,selection:0,selector:4"));
                    }
                }
            }
        };

        MethodAnalyserVisitor methodAnalyserVisitor = d -> {
            if ("test".equals(d.methodInfo().name) && "m2c".equals(d.enclosingMethod().name)) {
                assertEquals("$1", d.methodInfo().typeInfo.name());
                assertDv(d.p(0), MultiLevel.INDEPENDENT_DV, Property.INDEPENDENT);
                assertDv(d, DV.FALSE_DV, Property.MODIFIED_METHOD);
                assertTrue(d.methodAnalysis().preventInlining());
            }
        };

        testClass("Linking_2", 0, 0, new DebugConfiguration.Builder()
          //      .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                .build());
    }

    private void assertDv(StatementAnalyserVariableVisitor.Data d, int iteration, DV expected, DV dv) {
        if (d.iteration() < iteration) {
            assertTrue(dv.isDelayed(), "Expected a delay in iteration " + d.iteration() + ", got " + dv);
        } else {
            assertEquals(expected, dv, "Expected " + expected + ", got " + dv + " in iteration " + d.iteration());
        }
    }
}
