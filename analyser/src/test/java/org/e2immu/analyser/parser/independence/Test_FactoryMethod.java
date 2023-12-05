
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

package org.e2immu.analyser.parser.independence;


import org.e2immu.analyser.analyser.DV;
import org.e2immu.analyser.analyser.Property;
import org.e2immu.analyser.config.DebugConfiguration;
import org.e2immu.analyser.model.MultiLevel;
import org.e2immu.analyser.model.ParameterInfo;
import org.e2immu.analyser.model.variable.FieldReference;
import org.e2immu.analyser.model.variable.ReturnVariable;
import org.e2immu.analyser.parser.CommonTestRunner;
import org.e2immu.analyser.visitor.BreakDelayVisitor;
import org.e2immu.analyser.visitor.EvaluationResultVisitor;
import org.e2immu.analyser.visitor.MethodAnalyserVisitor;
import org.e2immu.analyser.visitor.StatementAnalyserVariableVisitor;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.e2immu.analyser.parser.VisitorTestSupport.IterationInfo.it;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class Test_FactoryMethod extends CommonTestRunner {

    public Test_FactoryMethod() {
        super(true);
    }

    @Test
    public void test_0() throws IOException {
        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if ("of2".equals(d.methodInfo().name)) {
                if (d.variable() instanceof ParameterInfo pi && "t".equals(pi.name)) {
                    if ("3".equals(d.statementId())) {
                        assertLinked(d, it(0, 1, "f:-1,tt:-1"), it(2, "f:3"));
                    }
                }
                if (d.variable() instanceof ReturnVariable) {
                    if ("3".equals(d.statementId())) {
                        assertLinked(d, it(0, 1, "f:0,t:-1,tt:-1"), it(2, "f:0"));
                    }
                }
                if ("f".equals(d.variableName())) {
                    if ("3".equals(d.statementId())) {
                        assertLinked(d, it(0, 1, "t:-1,tt:-1"), it(2, ""));
                    }
                }
            }
            if ("of".equals(d.methodInfo().name)) {
                if (d.variable() instanceof ParameterInfo pi && "ts".equals(pi.name)) {
                    if ("2".equals(d.statementId())) {
                        assertLinked(d, it(0, 1, "f:-1"), it(2, "f:4"));
                    }
                }
                if (d.variable() instanceof ReturnVariable) {
                    if ("2".equals(d.statementId())) {
                        assertLinked(d, it(0, 1, "f:0,ts:-1"), it(2, "f:0,ts:4"));
                    }
                }
                if ("f".equals(d.variableName())) {
                    if ("2".equals(d.statementId())) {
                        assertLinked(d, it(0, 1, "ts:-1"), it(2, "ts:4"));
                    }
                }
            }
            if ("getStream".equals(d.methodInfo().name)) {
                if (d.variable() instanceof ReturnVariable) {
                    String linked = d.iteration() == 0 ? "this.list:-1,this:-1" : "this.list:4,this:4";
                    assertEquals(linked, d.variableInfo().getLinkedVariables().toString());
                }
            }
            if ("copy".equals(d.methodInfo().name)) {
                if (d.variable() instanceof ReturnVariable) {
                    String linked = d.iteration() == 0 ? "this.list:-1,this:-1" : "this.list:4,this:4";
                    assertEquals(linked, d.variableInfo().getLinkedVariables().toString());
                }
            }
            if ("copy2".equals(d.methodInfo().name)) {
                if ("result".equals(d.variableName())) {
                    if ("1".equals(d.statementId())) {
                        String linked = d.iteration() == 0 ? "this.list:-1,this:-1" : "this.list:4,this:4";
                        assertEquals(linked, d.variableInfo().getLinkedVariables().toString());
                    }
                }
                if (d.variable() instanceof ReturnVariable) {
                    if ("2".equals(d.statementId())) {
                        String linked = d.iteration() == 0 ? "result:0,this.list:-1,this:-1" : "result:0,this.list:4,this:4";
                        assertEquals(linked, d.variableInfo().getLinkedVariables().toString());
                    }
                }
                if (d.variable() instanceof FieldReference fr && "list".equals(fr.fieldInfo().name)) {
                    if ("1".equals(d.statementId())) {
                        String linked = d.iteration() == 0 ? "result:-1,this:-1" : "result:4,this:4";
                        assertEquals(linked, d.variableInfo().getLinkedVariables().toString());
                    }
                }
            }
        };
        MethodAnalyserVisitor methodAnalyserVisitor = d -> {
            if ("of".equals(d.methodInfo().name)) {
                assertDv(d, 5, MultiLevel.INDEPENDENT_HC_DV, Property.INDEPENDENT);
            }
        };

        BreakDelayVisitor breakDelayVisitor = d -> assertEquals("-----", d.delaySequence());

        testClass("FactoryMethod_0", 0, 0, new DebugConfiguration.Builder()
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                .addBreakDelayVisitor(breakDelayVisitor)
                .build());
    }


    @Test
    public void test_1() throws IOException {

        EvaluationResultVisitor evaluationResultVisitor = d -> {
            if ("of".equals(d.methodInfo().name)) {
                if ("0".equals(d.statementId())) {
                    assertEquals("new FactoryMethod_1<>()", d.evaluationResult().value().toString());
                }
            }
        };

        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if ("of".equals(d.methodInfo().name)) {
                if ("f".equals(d.variableName())) {
                    if ("0".equals(d.statementId())) {
                        assertLinked(d, it(0, ""));
                        assertCurrentValue(d, 0, "new FactoryMethod_1<>()");
                    }
                    if ("1".equals(d.statementId())) {
                        assertLinked(d, it(0, 1, "t:-1"), it(2, ""));
                        assertCurrentValue(d, 1, "instance 1 type FactoryMethod_1<T>");
                    }
                }
            }
        };

        MethodAnalyserVisitor methodAnalyserVisitor = d -> {
            if ("add".equals(d.methodInfo().name)) {
                assertDv(d, 2, DV.TRUE_DV, Property.FLUENT);
                assertDv(d, 1, DV.TRUE_DV, Property.MODIFIED_METHOD);
                assertDv(d.p(0), 2, MultiLevel.INDEPENDENT_HC_DV, Property.INDEPENDENT);
                assertDv(d.p(0), 2, DV.FALSE_DV, Property.MODIFIED_VARIABLE);
            }

            if ("of".equals(d.methodInfo().name)) {
                String expected = d.iteration() < 2 ? "<m:of>" : "f$1";
                assertEquals(expected, d.methodAnalysis().getSingleReturnValue().toString());
            }
        };

        BreakDelayVisitor breakDelayVisitor = d -> assertEquals("-----", d.delaySequence());

        testClass("FactoryMethod_1", 0, 0, new DebugConfiguration.Builder()
                .addEvaluationResultVisitor(evaluationResultVisitor)
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                .addBreakDelayVisitor(breakDelayVisitor)
                .build());
    }

}
