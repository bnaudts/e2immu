
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

package org.e2immu.analyser.parser.basics;

import org.e2immu.analyser.analyser.DV;
import org.e2immu.analyser.analyser.EvaluationResult;
import org.e2immu.analyser.analyser.VariableInfoContainer;
import org.e2immu.analyser.config.DebugConfiguration;
import org.e2immu.analyser.model.MultiLevel;
import org.e2immu.analyser.model.ParameterInfo;
import org.e2immu.analyser.model.variable.FieldReference;
import org.e2immu.analyser.model.variable.ReturnVariable;
import org.e2immu.analyser.model.variable.This;
import org.e2immu.analyser.parser.CommonTestRunner;
import org.e2immu.analyser.visitor.*;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.e2immu.analyser.analyser.Property.*;
import static org.e2immu.analyser.model.MultiLevel.NOT_INVOLVED_DV;
import static org.junit.jupiter.api.Assertions.*;

public class Test_00_Basics_7 extends CommonTestRunner {

    public static final String INSTANCE_TYPE_PRINT_STREAM = "instance type PrintStream";

    public Test_00_Basics_7() {
        super(true);
    }

    // more on statement time
    @Test
    public void test7() throws IOException {
        final String I = "org.e2immu.analyser.testexample.Basics_7.i";
        final String I0 = "i$0";
        final String I1 = "i$1";
        final String I0_FQN = I + "$0";
        final String I1_FQN = I + "$1";
        final String I101_FQN = I + "$1$1.0.1-E";
        final String INC3_RETURN_VAR = "org.e2immu.analyser.testexample.Basics_7.increment3()";
        final String I_DELAYED = "<f:i>";
        final String INSTANCE_TYPE_INT_IDENTITY = "instance type int/*@Identity*/";
        final String I0_2 = I + "$0$2-E";

        EvaluationResultVisitor evaluationResultVisitor = d -> {
            if ("increment".equals(d.methodInfo().name) && "3".equals(d.statementId())) {
                EvaluationResult.ChangeData cd = d.findValueChange(I);
                assertFalse(cd.getProperty(CONTEXT_NOT_NULL).isDelayed());
            }

            if ("increment".equals(d.methodInfo().name) && "4".equals(d.statementId()) && d.iteration() > 0) {
                assertEquals("true", d.evaluationResult().value().toString());
            }
            if ("increment3".equals(d.methodInfo().name) && "1.0.3".equals(d.statementId()) && d.iteration() > 0) {
                assertEquals("true", d.evaluationResult().value().toString());
            }
        };

        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if ("Basics_7".equals(d.methodInfo().name)) {
                if (d.variable() instanceof ParameterInfo p && "p".equals(p.name)) {
                    if ("0.0.0".equals(d.statementId())) {
                        assertEquals("0.0.0" + VariableInfoContainer.Level.EVALUATION, d.variableInfo().getReadId());
                        String expect = INSTANCE_TYPE_INT_IDENTITY;
                        assertEquals(expect, d.currentValue().toString());
                    }
                    if ("0.0.1".equals(d.statementId())) {
                        // READ IMPLICITLY via the variable 'i'
                        assertEquals("0.0.1" + VariableInfoContainer.Level.EVALUATION, d.variableInfo().getReadId());
                        String expect = d.iteration() == 0 ? "<p:p>" : INSTANCE_TYPE_INT_IDENTITY;
                        assertEquals(expect, d.currentValue().toString());
                        assertDv(d, 1, DV.TRUE_DV, IDENTITY);
                    }
                    if ("0".equals(d.statementId())) {
                        assertTrue(d.variableInfoContainer().hasMerge());
                        assertEquals("0" + VariableInfoContainer.Level.MERGE, d.variableInfo().getReadId());

                        String expect = d.iteration() == 0 ? "b?<p:p>:" + INSTANCE_TYPE_INT_IDENTITY : INSTANCE_TYPE_INT_IDENTITY;
                        assertEquals(expect, d.currentValue().toString());
                        assertDv(d, 1, DV.TRUE_DV, IDENTITY);
                    }

                    assertEquals(MultiLevel.EFFECTIVELY_NOT_NULL_DV, d.getProperty(CONTEXT_NOT_NULL),
                            "in statement " + d.statementId());

                }
                if (d.variable() instanceof This) {
                    assertEquals(NOT_INVOLVED_DV, d.getProperty(EXTERNAL_IMMUTABLE));
                    assertEquals(MultiLevel.EFFECTIVELY_NOT_NULL_DV, d.getProperty(CONTEXT_NOT_NULL));
                }
                if (d.variable() instanceof ParameterInfo pi && "b".equals(pi.name)) {
                    assertEquals(MultiLevel.EFFECTIVELY_NOT_NULL_DV, d.getProperty(CONTEXT_NOT_NULL));
                    assertDv(d, 1, MultiLevel.NOT_INVOLVED_DV, EXTERNAL_IMMUTABLE);
                }

                if (d.variable() instanceof FieldReference fr && "out".equals(fr.fieldInfo.name)) {
                    assertEquals(MultiLevel.EFFECTIVELY_NOT_NULL_DV, d.getProperty(CONTEXT_NOT_NULL));
                    if ("0.0.1".equals(d.statementId())) {
                        String expect = d.iteration() == 0 ? "<f:out>" : INSTANCE_TYPE_PRINT_STREAM;
                        assertEquals(expect, d.currentValue().toString());
                        assertDv(d, 1, MultiLevel.EFFECTIVELY_NOT_NULL_DV, NOT_NULL_EXPRESSION);
                        assertEquals(MultiLevel.EFFECTIVELY_NOT_NULL_DV, d.getProperty(EXTERNAL_NOT_NULL));
                    }
                    if ("1.0.0".equals(d.statementId())) {
                        String expect = d.iteration() == 0 ? "<f:out>" : INSTANCE_TYPE_PRINT_STREAM;
                        assertEquals(expect, d.currentValue().toString());
                        assertDv(d, 1, MultiLevel.EFFECTIVELY_NOT_NULL_DV, NOT_NULL_EXPRESSION);
                        assertEquals(MultiLevel.EFFECTIVELY_NOT_NULL_DV, d.getProperty(EXTERNAL_NOT_NULL));
                    }
                }

                if (d.variable() instanceof FieldReference fr && "i".equals(fr.fieldInfo.name)) {
                    if ("0.0.0".equals(d.statementId())) {
                        assertEquals("p", d.currentValue().toString());
                    }
                    if ("0.0.1".equals(d.statementId())) {
                        String expect = d.iteration() == 0 ? "<f:i>" : "p";
                        assertEquals(expect, d.currentValue().toString());
                    }
                    if ("0".equals(d.statementId())) {
                        String expectInitial = d.iteration() == 0 ? "<f:i>" : "0";
                        assertEquals(expectInitial, d.variableInfoContainer().getPreviousOrInitial()
                                .getValue().toString());
                        String expect = d.iteration() == 0 ? "b?<f:i>:<f:i>" : "b?p:0";
                        assertEquals(expect, d.currentValue().toString(),
                                "Delay: " + d.currentValue().causesOfDelay().toString());
                    }
                    if ("1.0.0".equals(d.statementId())) {
                        String expected = d.iteration() == 0 ? "1+(b?<f:i>:<f:i>)" : "1+(b?p:0)";
                        assertEquals(expected, d.currentValue().toString());
                    }
                }
            }
            if ("increment".equals(d.methodInfo().name)) {
                if (I.equals(d.variableName())) {
                    if ("2".equals(d.statementId()) && d.iteration() > 0) {
                        assertEquals(I0 + "+q", d.currentValue().toString());
                    }
                }
                if (I0_2.equals(d.variableName())) {
                    if ("3".equals(d.statementId())) {
                        assertEquals("i$0+q", d.currentValue().toString());
                        assertDv(d,1, NOT_INVOLVED_DV, EXTERNAL_NOT_NULL);
                        assertDv(d,1, NOT_INVOLVED_DV, EXTERNAL_IMMUTABLE);
                    }
                }
            }
            if ("increment3".equals(d.methodInfo().name)) {
                if (d.variable() instanceof ReturnVariable) {
                    assertEquals(INC3_RETURN_VAR, d.variableName());
                    String expected = d.iteration() == 0 ? "-1+<f:i>==<f:i>" : "true";
                    if ("1.0.3".equals(d.statementId())) {
                        assertEquals(expected, d.currentValue().toString());
                    }
                    if ("1".equals(d.statementId())) {
                        assertEquals(expected, d.currentValue().toString());
                    }
                }
                if ("j".equals(d.variableName())) {
                    if ("1.0.0".equals(d.statementId())) {
                        String expect = d.iteration() == 0 ? I_DELAYED : I1;
                        assertEquals(expect, d.currentValue().toString());
                        String expectLv = d.iteration() == 0 ? "j:0,this.i:0" : "i$1:1,j:0,this.i:0";
                        assertEquals(expectLv, d.variableInfo().getLinkedVariables().toString());
                    }
                    // at 1.0.1, i gets incremented, j should not be linked to this.i anymore
                    if ("1.0.1".equals(d.statementId())) {
                        String expectLv = d.iteration() == 0 ? "j:0" : "i$1:1,j:0";
                        assertEquals(expectLv, d.variableInfo().getLinkedVariables().toString());
                    }
                    if ("1.0.3".equals(d.statementId())) {
                        String expectLv = d.iteration() == 0 ? "j:0" : "i$1:1,j:0";
                        assertEquals(expectLv, d.variableInfo().getLinkedVariables().toString());
                    }
                }
                if (I0_FQN.equals(d.variableName())) {
                    if ("0".equals(d.statementId())) {
                        assertTrue(d.iteration() > 0); // does not exist earlier!

                        assertEquals("i$0:0,this.i:0", d.variableInfo().getLinkedVariables().toString());
                        assertEquals("instance type int", d.currentValue().toString());
                    }
                    if ("1.0.3".equals(d.statementId())) {
                        // NOTE: it is fine to have i$1 here, as long as it is not with a :0
                        // FIXME should this.i still be here?
                        assertEquals("i$0:0,this.i:0", d.variableInfo().getLinkedVariables().toString());
                    }
                }
                if (I1_FQN.equals(d.variableName())) {
                    // exists from 1.0.0 onwards
                    assertTrue(d.iteration() > 0); // does not exist earlier!

                    if ("1.0.0".equals(d.statementId())) {
                        // after the assignment, i becomes a different value
                        String expectLv = "i$1:0,j:0,this.i:0";
                        assertEquals(expectLv, d.variableInfo().getLinkedVariables().toString(), d.statementId());
                        assertEquals("instance type int", d.currentValue().toString());
                    }
                    if ("1.0.1".equals(d.statementId())) {
                        // after the assignment, i becomes a different value
                        assertEquals("i$1:0,j:0", d.variableInfo().getLinkedVariables().toString(), d.statementId());
                    }
                    if ("1.0.3".equals(d.statementId())) {
                        assertEquals("i$1:0,j:0", d.variableInfo().getLinkedVariables().toString(), d.statementId());
                    }
                    if ("1".equals(d.statementId())) {
                        assertEquals("i$1:0", d.variableInfo().getLinkedVariables().toString(), d.statementId());
                    }
                }
                if (I101_FQN.equals(d.variableName())) {
                    if ("1".equals(d.statementId())) {
                        assertFalse(d.variableInfoContainer().hasEvaluation());
                        assertEquals("i$1$1.0.1-E:0,this.i:0", d.variableInfo().getLinkedVariables().toString());
                    }
                    // is primitive
                    if ("1.0.0".equals(d.statementId()) || "1.0.1".equals(d.statementId())) {
                        fail("Should not follow the path 102-103-1M-new it-1E-100");
                    }
                    if ("1.0.2".equals(d.statementId())) {
                        assertEquals("1+" + I1, d.currentValue().toString());
                        assertEquals("1.0.2-C", d.variableInfo().getReadId());
                        // FIXME why is this not like in "1" ?? with itself included?
                        assertEquals("this.i:0", d.variableInfo().getLinkedVariables().toString());
                    }
                    if ("1.0.3".equals(d.statementId())) {
                        assertEquals("1+" + I1, d.currentValue().toString());
                        assertEquals("1.0.2-C", d.variableInfo().getReadId());
                        assertEquals("this.i:0", d.variableInfo().getLinkedVariables().toString());
                    }
                }
                if (I.equals(d.variableName())) {
                    if ("0".equals(d.statementId())) {
                        String expect = d.iteration() == 0 ? I_DELAYED : "instance type int";
                        assertEquals(expect, d.currentValue().toString());
                        int expectStatementTime = d.iteration() == 0 ? VariableInfoContainer.VARIABLE_FIELD_DELAY : 0;
                        assertEquals(expectStatementTime, d.variableInfo().getStatementTime());
                        assertEquals("[0]", d.variableInfo().getReadAtStatementTimes().toString());

                        assertEquals("this.i:0", d.variableInfo().getLinkedVariables().toString());
                        assertDv(d, 1, MultiLevel.EFFECTIVELY_NOT_NULL_DV, EXTERNAL_NOT_NULL);
                    }
                    if ("1.0.0".equals(d.statementId())) {
                        String expect = d.iteration() == 0 ? I_DELAYED : "instance type int";
                        assertEquals(expect, d.currentValue().toString());
                        int expectStatementTime = d.iteration() == 0 ? VariableInfoContainer.VARIABLE_FIELD_DELAY : 1;
                        assertEquals(expectStatementTime, d.variableInfo().getStatementTime());
                        assertEquals("[1]", d.variableInfo().getReadAtStatementTimes().toString());
                        assertDv(d, 1, MultiLevel.EFFECTIVELY_NOT_NULL_DV, EXTERNAL_NOT_NULL);
                        String linked = d.iteration() == 0 ? "j:0,this.i:0" : "i$1:1,j:0,this.i:0";
                        assertEquals(linked, d.variableInfo().getLinkedVariables().toString());
                    }
                    if ("1.0.1".equals(d.statementId())) {
                        // we switch to NOT_INVOLVED, given that the field has been assigned; its external value is of no use
                        assertEquals(MultiLevel.NOT_INVOLVED_DV, d.getProperty(EXTERNAL_NOT_NULL));
                        assertEquals("this.i:0", d.variableInfo().getLinkedVariables().toString());
                    }
                    if ("1.0.2".equals(d.statementId())) {
                        String expect = d.iteration() == 0 ? "1+<f:i>" : "1+" + I1;
                        assertEquals(expect, d.currentValue().toString());
                        assertEquals("1.0.2-E", d.variableInfo().getReadId());
                        assertEquals(MultiLevel.NOT_INVOLVED_DV, d.getProperty(EXTERNAL_NOT_NULL));
                        String expectLv = "this.i:0";
                        assertEquals(expectLv, d.variableInfo().getLinkedVariables().toString());
                    }
                    if ("1.0.3".equals(d.statementId())) {
                        assertEquals("this.i:0", d.variableInfo().getLinkedVariables().toString());
                        assertEquals(MultiLevel.NOT_INVOLVED_DV, d.getProperty(EXTERNAL_NOT_NULL));
                    }
                    if ("1".equals(d.statementId())) {
                        String expect = d.iteration() == 0 ? "1+<f:i>" : "1+" + I1;
                        assertEquals(expect, d.currentValue().toString());
                        assertEquals("this.i:0", d.variableInfo().getLinkedVariables().toString());
                        assertEquals(MultiLevel.NOT_INVOLVED_DV, d.getProperty(EXTERNAL_NOT_NULL));
                    }
                }
            }
        };

        StatementAnalyserVisitor statementAnalyserVisitor = d -> {
            int timeI = d.statementAnalysis().statementTime(VariableInfoContainer.Level.INITIAL);
            int timeE = d.statementAnalysis().statementTime(VariableInfoContainer.Level.EVALUATION);
            int timeM = d.statementAnalysis().statementTime(VariableInfoContainer.Level.MERGE);

            // method itself is synchronised, so statement time stands still
            if ("increment".equals(d.methodInfo().name)) {
                assertEquals(0, timeI);
                assertEquals(0, timeE);
                assertEquals(0, timeM);
            }
            if ("increment2".equals(d.methodInfo().name)) {
                if ("0".equals(d.statementId())) {
                    assertEquals(0, timeI);
                    assertEquals(1, timeE);
                    assertEquals(1, timeM);
                }
                if ("1".equals(d.statementId())) {
                    assertEquals(1, timeI);
                    assertEquals(1, timeE);
                    assertEquals(1, timeM);
                }
            }
        };

        MethodAnalyserVisitor methodAnalyserVisitor = d -> {
            if ("increment".equals(d.methodInfo().name)) {
                assertTrue(d.methodInfo().isSynchronized());
            }
            if ("increment3".equals(d.methodInfo().name)) {
                String expected = d.iteration() == 0 ? "-1+<f:i>==<f:i>" : "true";
                assertEquals(expected, d.methodAnalysis().getLastStatement()
                        .getVariable(INC3_RETURN_VAR).current().getValue().toString());
                if (d.iteration() > 0) {
                    assertEquals("true", d.methodAnalysis().getSingleReturnValue().toString());
                }
            }
        };

        FieldAnalyserVisitor fieldAnalyserVisitor = d -> {
            if ("i".equals(d.fieldInfo().name)) {
                assertEquals("<variable value>", d.fieldAnalysis().getValue().toString());
                assertEquals(DV.FALSE_DV, d.fieldAnalysis().getProperty(FINAL));
                assertEquals(MultiLevel.EFFECTIVELY_NOT_NULL_DV, d.fieldAnalysis().getProperty(EXTERNAL_NOT_NULL));
            }
        };

        testClass("Basics_7", 0, 1, new DebugConfiguration.Builder()
                .addStatementAnalyserVisitor(statementAnalyserVisitor)
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .addEvaluationResultVisitor(evaluationResultVisitor)
                .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                .addAfterFieldAnalyserVisitor(fieldAnalyserVisitor)
                .build());
    }
}
