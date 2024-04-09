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

package org.e2immu.analyser.parser.conditional;

import org.e2immu.analyser.analyser.DV;
import org.e2immu.analyser.analyser.Property;
import org.e2immu.analyser.config.AnalyserConfiguration;
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

import static org.junit.jupiter.api.Assertions.*;

public class Test_04_Assert extends CommonTestRunner {
    public Test_04_Assert() {
        super(false);
    }

    @Test
    public void test_0() throws IOException {
        EvaluationResultVisitor evaluationResultVisitor = d -> {
            if ("combine".equals(d.methodInfo().name)) {
                if ("1".equals(d.statementId())) {
                    String expected = "other.isDelayed()";
                    assertEquals(expected, d.evaluationResult().value().toString());
                }
                if ("2".equals(d.statementId())) {
                    String expected = switch (d.iteration()) {
                        case 0, 1 -> "<m:isDelayed>";
                        default -> "this.isDelayed()";
                    };
                    assertEquals(expected, d.evaluationResult().value().toString());
                }
            }
        };
        StatementAnalyserVisitor statementAnalyserVisitor = d -> {
            if ("combine".equals(d.methodInfo().name)) {
                if ("1".equals(d.statementId())) {
                    // not delayed, even in iteration 0: call to interface method
                    String expected = "Precondition[expression=other.isDelayed(), causes=[escape]]";
                    assertEquals(expected, d.statementAnalysis().stateData().getPrecondition().toString());
                }
                if ("2".equals(d.statementId())) {
                    String expected = switch (d.iteration()) {
                        case 0, 1 -> "Precondition[expression=<precondition>, causes=[escape]]";
                        default -> "Precondition[expression=this.isDelayed(), causes=[escape]]";
                    };
                    assertEquals(expected, d.statementAnalysis().stateData().getPrecondition().toString());
                }
            }
        };
        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
//            assertFalse(d.allowBreakDelay());

            if ("causesOfDelay".equals(d.methodInfo().name) && "NotDelayed".equals(d.methodInfo().typeInfo.simpleName)) {
                if (d.variable() instanceof ReturnVariable) {
                    assertEquals("SimpleSet.EMPTY:0", d.variableInfo().getLinkedVariables().toString());
                    assertDv(d, 5, MultiLevel.EFFECTIVELY_NOT_NULL_DV, Property.EXTERNAL_NOT_NULL);
                }
                if (d.variable() instanceof FieldReference fr && "EMPTY".equals(fr.fieldInfo().name)) {
                    assertCurrentValue(d, 5, "instance type SimpleSet/*new SimpleSet(Set.of())*/");
                    // NOT_CONTAINER because of isMyself
                    assertDv(d, 5, MultiLevel.NOT_CONTAINER_DV, Property.CONTAINER);
                }
            }
            if ("combine".equals(d.methodInfo().name)) {
                if (d.variable() instanceof This) {
                    if ("1".equals(d.statementId()) || "2".equals(d.statementId()) || "3".equals(d.statementId())) {
                        assertEquals("", d.variableInfo().getLinkedVariables().toString());
                    }
                    if ("4.0.0".equals(d.statementId())) {
                        assertEquals("merge:0", d.variableInfo().getLinkedVariables().toString());
                    }
                }
                if (d.variable() instanceof ReturnVariable) {
                    if ("3".equals(d.statementId())) {
                        assertEquals("other instanceof NotDelayed?this$0:<return value>",
                                d.currentValue().toString());
                        assertEquals("this:0", d.variableInfo().getLinkedVariables().toString());
                    }
                    if ("4.0.0".equals(d.statementId())) {
                        assertEquals("", d.variableInfo().getLinkedVariables().toString());
                    }
                    if ("4.1.0".equals(d.statementId())) {
                        assertEquals("<return value>", d.currentValue().toString());
                        assertEquals("", d.variableInfo().getLinkedVariables().toString());
                    }
                    if ("4".equals(d.statementId())) {
                        // because of 4.0.0, merge, this and the return value are linked at static level
                        assertEquals("this:0", d.variableInfo().getLinkedVariables().toString());
                    }
                    if ("5".equals(d.statementId())) {
                        String value = switch (d.iteration()) {
                            case 0, 1 -> "<instanceOf:NotDelayed>?this$0:<m:addProgress>";
                            default -> "other instanceof NotDelayed?this$0:null";
                        };
                        assertEquals(value, d.currentValue().toString());
                        // there should not be a STATICALLY_ASSIGNED here: it is the result of a method call
                        // however, the previous linking is taken into account, and only the linking to "other"
                        // remains to be solved.
                        String linked = switch (d.iteration()) {
                            case 0 -> "CausesOfDelay.LIMIT:-1,limit:-1,merge:0,other:-1,this:0";
                            case 1 -> "merge:0,other:-1,this:0";
                            default -> "merge:0,this:0";
                        };
                        assertEquals(linked, d.variableInfo().getLinkedVariables().toString());
                    }
                }
                if ("merge".equals(d.variableName())) {
                    if ("4".equals(d.statementId())) {
                        String linked = switch (d.iteration()) {
                            case 0 -> "CausesOfDelay.LIMIT:-1,limit:-1,other:-1,this:0";
                            case 1 -> "other:-1,this:0";
                            default -> "this:0";
                        };
                        assertEquals(linked, d.variableInfo().getLinkedVariables().toString());
                    }
                    if ("4.1.0".equals(d.statementId())) {
                        String linked = d.iteration() < 2 ? "other:-1,this:-1" : "this:1";
                        /*
                        The result should definitely not conclude other:3, because other is not linked to the return value!
                        This value cannot be known until we know of the independence of "merge"
                         */
                        assertEquals(linked, d.variableInfo().getLinkedVariables().toString());
                    }
                    if ("5".equals(d.statementId())) {
                        String value = d.iteration() < 2
                                ? "limit&&(-1+other.numberOfDelays()>=<f:LIMIT>||-1-<f:LIMIT>+<m:numberOfDelays>>=0)?<s:SimpleSet>:<s:CausesOfDelay>"
                                : "this$0";
                        assertEquals(value, d.currentValue().toString());
                        String linked = switch (d.iteration()) {
                            case 0 -> "CausesOfDelay.LIMIT:-1,limit:-1,other:-1,this:0";
                            case 1 -> "other:-1,this:0";
                            default -> "this:0";
                        };
                        assertEquals(linked, d.variableInfo().getLinkedVariables().toString());
                    }
                }
                if (d.variable() instanceof ParameterInfo pi && "other".equals(pi.name)) {
                    if ("0".equals(d.statementId())) {
                        assertDv(d, DV.FALSE_DV, Property.CONTEXT_MODIFIED);
                    }
                    if ("4".equals(d.statementId())) {
                        assertDv(d, 2, DV.FALSE_DV, Property.CONTEXT_MODIFIED);
                    }
                }
            }
        };

        MethodAnalyserVisitor methodAnalyserVisitor = d -> {
            if ("addProgress".equals(d.methodInfo().name) && "CausesOfDelay".equals(d.methodInfo().typeInfo.simpleName)) {
                assertDv(d, DV.FALSE_DV, Property.MODIFIED_METHOD);
            }
            if ("causesOfDelay".equals(d.methodInfo().name)) {
                if ("SimpleSet".equals(d.methodInfo().typeInfo.simpleName)) {
                    assertDv(d, DV.FALSE_DV, Property.MODIFIED_METHOD);
                    assertDv(d, MultiLevel.EFFECTIVELY_NOT_NULL_DV, Property.NOT_NULL_EXPRESSION);
                    assertDv(d, MultiLevel.EFFECTIVELY_IMMUTABLE_HC_DV, Property.IMMUTABLE);
                    assertDv(d, 1, MultiLevel.INDEPENDENT_DV, Property.INDEPENDENT);
                    assertDv(d, MultiLevel.CONTAINER_DV, Property.CONTAINER);
                }
                if ("AnalysisStatus".equals(d.methodInfo().typeInfo.simpleName)) {
                    assertDv(d, DV.FALSE_DV, Property.MODIFIED_METHOD);
                    assertDv(d, MultiLevel.NULLABLE_DV, Property.NOT_NULL_EXPRESSION);
                    assertDv(d, MultiLevel.EFFECTIVELY_IMMUTABLE_HC_DV, Property.IMMUTABLE);
                    assertDv(d, MultiLevel.INDEPENDENT_HC_DV, Property.INDEPENDENT);
                    assertDv(d, MultiLevel.CONTAINER_DV, Property.CONTAINER);
                }
                if ("NotDelayed".equals(d.methodInfo().typeInfo.simpleName)) {
                    assertDv(d, DV.FALSE_DV, Property.MODIFIED_METHOD);
                    assertDv(d, 5, MultiLevel.EFFECTIVELY_NOT_NULL_DV, Property.NOT_NULL_EXPRESSION);
                    assertDv(d, 5, MultiLevel.EFFECTIVELY_IMMUTABLE_HC_DV, Property.IMMUTABLE);
                    assertDv(d, 5, MultiLevel.INDEPENDENT_DV, Property.INDEPENDENT);
                    assertDv(d, MultiLevel.CONTAINER_DV, Property.CONTAINER);
                }
            }
            if ("isDelayed".equals(d.methodInfo().name)) {
                if ("SimpleSet".equals(d.methodInfo().typeInfo.simpleName)) {
                    assertDv(d, DV.FALSE_DV, Property.MODIFIED_METHOD);
                    assertDv(d, 1, MultiLevel.EFFECTIVELY_NOT_NULL_DV, Property.NOT_NULL_EXPRESSION);
                    assertDv(d, MultiLevel.EFFECTIVELY_IMMUTABLE_DV, Property.IMMUTABLE);
                    assertDv(d, MultiLevel.INDEPENDENT_DV, Property.INDEPENDENT);
                    assertDv(d, MultiLevel.CONTAINER_DV, Property.CONTAINER);
                }
                if ("AnalysisStatus".equals(d.methodInfo().typeInfo.simpleName)) {
                    assertDv(d, DV.FALSE_DV, Property.MODIFIED_METHOD);
                    assertDv(d, MultiLevel.EFFECTIVELY_NOT_NULL_DV, Property.NOT_NULL_EXPRESSION);
                    assertDv(d, MultiLevel.EFFECTIVELY_IMMUTABLE_DV, Property.IMMUTABLE);
                    assertDv(d, MultiLevel.INDEPENDENT_DV, Property.INDEPENDENT);
                    assertDv(d, MultiLevel.CONTAINER_DV, Property.CONTAINER);
                }
                if ("NotDelayed".equals(d.methodInfo().typeInfo.simpleName)) {
                    assertDv(d, DV.FALSE_DV, Property.MODIFIED_METHOD);
                    assertDv(d, MultiLevel.EFFECTIVELY_NOT_NULL_DV, Property.NOT_NULL_EXPRESSION);
                    assertDv(d, MultiLevel.EFFECTIVELY_IMMUTABLE_DV, Property.IMMUTABLE);
                    assertDv(d, MultiLevel.INDEPENDENT_DV, Property.INDEPENDENT);
                    assertDv(d, MultiLevel.CONTAINER_DV, Property.CONTAINER);
                }
            }
            if ("merge".equals(d.methodInfo().name)) {
                assertEquals("SimpleSet", d.methodInfo().typeInfo.simpleName);
                assertDv(d, 1, DV.TRUE_DV, Property.FLUENT);
                assertDv(d, DV.FALSE_DV, Property.MODIFIED_METHOD);
                // returns self, so independent
                assertDv(d, 1, MultiLevel.INDEPENDENT_DV, Property.INDEPENDENT);
            }
        };

        TypeAnalyserVisitor typeAnalyserVisitor = d -> {
            if ("AnalysisStatus".equals(d.typeInfo().simpleName)) {
                 assertNoHc(d);
                assertDv(d, MultiLevel.INDEPENDENT_HC_DV, Property.INDEPENDENT);
            }
            if ("CauseOfDelay".equals(d.typeInfo().simpleName)) {
                 assertNoHc(d);
                assertDv(d, MultiLevel.INDEPENDENT_HC_DV, Property.INDEPENDENT);
            }
            if ("CausesOfDelay".equals(d.typeInfo().simpleName)) {
                assertDv(d, MultiLevel.EFFECTIVELY_IMMUTABLE_HC_DV, Property.IMMUTABLE);
                assertDv(d, MultiLevel.CONTAINER_DV, Property.CONTAINER);
                assertDv(d, MultiLevel.INDEPENDENT_HC_DV, Property.INDEPENDENT);
                assertTrue(d.typeInspection().isExtensible());
                 assertNoHc(d);
            }
            if ("NotDelayed".equals(d.typeInfo().simpleName)) {
                assertFalse(d.typeInspection().isExtensible());
                 assertNoHc(d);
                assertDv(d, 5, MultiLevel.INDEPENDENT_DV, Property.INDEPENDENT);
                assertDv(d, 5, MultiLevel.EFFECTIVELY_IMMUTABLE_DV, Property.IMMUTABLE);
            }
            if ("SimpleSet".equals(d.typeInfo().simpleName)) {
                assertTrue(d.typeInspection().isExtensible());
                 assertNoHc(d);
                assertDv(d, 3, MultiLevel.CONTAINER_DV, Property.CONTAINER);
            }
        };

        FieldAnalyserVisitor fieldAnalyserVisitor = d -> {
            if ("EMPTY".equals(d.fieldInfo().name)) {
                assertDv(d, 3, MultiLevel.EFFECTIVELY_FINAL_FIELDS_DV, Property.EXTERNAL_IMMUTABLE);
                // as a final field, it is not linked to the parameters of the constructor
                assertDv(d, 1, MultiLevel.INDEPENDENT_DV, Property.INDEPENDENT);
                assertDv(d, 4, MultiLevel.CONTAINER_DV, Property.CONTAINER);
                assertDv(d, 4, MultiLevel.NOT_CONTAINER_DV, Property.CONTAINER_RESTRICTION);
                assertDv(d, MultiLevel.NOT_IGNORE_MODS_DV, Property.EXTERNAL_IGNORE_MODIFICATIONS);
                assertDv(d, MultiLevel.EFFECTIVELY_NOT_NULL_DV, Property.EXTERNAL_NOT_NULL);
                assertEquals("new SimpleSet(Set.of())", d.fieldAnalysis().getValue().toString());
            }
            if ("LIMIT".equals(d.fieldInfo().name)) {
                assertDv(d, MultiLevel.NOT_IGNORE_MODS_DV, Property.EXTERNAL_IGNORE_MODIFICATIONS);
                assertDv(d, MultiLevel.EFFECTIVELY_NOT_NULL_DV, Property.EXTERNAL_NOT_NULL);
            }
        };

        BreakDelayVisitor breakDelayVisitor = d -> assertEquals("-------", d.delaySequence());

        testClass("Assert_0", 0, 3, new DebugConfiguration.Builder()
                .addEvaluationResultVisitor(evaluationResultVisitor)
                .addStatementAnalyserVisitor(statementAnalyserVisitor)
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                .addAfterFieldAnalyserVisitor(fieldAnalyserVisitor)
                .addAfterTypeAnalyserVisitor(typeAnalyserVisitor)
                .addBreakDelayVisitor(breakDelayVisitor)
                .build(), new AnalyserConfiguration.Builder().setForceAlphabeticAnalysisInPrimaryType(true).build());
    }

    @Test
    public void test_1() throws IOException {
        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if ("test".equals(d.methodInfo().name)) {
                if (d.variable() instanceof ParameterInfo pi && "strings".equals(pi.name)) {
                    if ("0".equals(d.statementId())) {
                        assertDv(d, 1, DV.FALSE_DV, Property.CONTEXT_MODIFIED);
                    }
                    if ("1".equals(d.statementId())) {
                        // no Annotated APIs...
                        assertDv(d, DV.TRUE_DV, Property.CONTEXT_MODIFIED);
                    }
                }
            }
            if ("containsA".equals(d.methodInfo().name)) {
                if (d.variable() instanceof ParameterInfo pi && "set".equals(pi.name)) {
                    if ("0".equals(d.statementId())) {
                        assertDv(d, DV.FALSE_DV, Property.CONTEXT_MODIFIED);
                    }
                    if ("1".equals(d.statementId())) {
                        assertDv(d, DV.FALSE_DV, Property.CONTEXT_MODIFIED);
                    }
                }
            }
        };
        StatementAnalyserVisitor statementAnalyserVisitor = d -> {
            String pc = d.statementAnalysis().stateData().getPrecondition().toString();
            if ("test".equals(d.methodInfo().name)) {
                if ("0".equals(d.statementId())) {
                    String expected = d.iteration() == 0
                            ? "Precondition[expression=<precondition>, causes=[methodCall:containsA]]"
                            : "Precondition[expression=!strings.contains(\"a\"), causes=[methodCall:containsA]]";
                    assertEquals(expected, pc);
                }
            }
            if ("containsA".equals(d.methodInfo().name)) {
                if ("0".equals(d.statementId())) {
                    assertEquals("Precondition[expression=!set.contains(\"a\"), causes=[escape]]", pc);
                }
                if ("1".equals(d.statementId())) {
                    assertEquals("Precondition[expression=true, causes=[]]", pc);
                }
            }
        };
        // ignoring result of method call -> 1 warning
        testClass("Assert_1", 0, 1, new DebugConfiguration.Builder()
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .addStatementAnalyserVisitor(statementAnalyserVisitor)
                .build());
    }


    @Test
    public void test_2() throws IOException {
        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if ("test".equals(d.methodInfo().name)) {
                if (d.variable() instanceof ParameterInfo pi && "strings".equals(pi.name)) {
                    if ("0".equals(d.statementId())) {
                        assertDv(d, 1, DV.FALSE_DV, Property.CONTEXT_MODIFIED);
                    }
                    if ("1".equals(d.statementId())) {
                        // no Annotated APIs...
                        assertDv(d, DV.TRUE_DV, Property.CONTEXT_MODIFIED);
                    }
                }
            }
            if ("containsA".equals(d.methodInfo().name)) {
                if (d.variable() instanceof ParameterInfo pi && "set".equals(pi.name)) {
                    if ("0".equals(d.statementId())) {
                        assertDv(d, DV.FALSE_DV, Property.CONTEXT_MODIFIED);
                    }
                    if ("1".equals(d.statementId())) {
                        assertDv(d, DV.FALSE_DV, Property.CONTEXT_MODIFIED);
                    }
                }
            }
        };
        StatementAnalyserVisitor statementAnalyserVisitor = d -> {
            String pc = d.statementAnalysis().stateData().getPrecondition().toString();
            if ("test".equals(d.methodInfo().name)) {
                if ("0".equals(d.statementId())) {
                    String expected = d.iteration() == 0
                            ? "Precondition[expression=<precondition>, causes=[escape, methodCall:containsA]]"
                            : "Precondition[expression=!strings.contains(\"a\"), causes=[escape, methodCall:containsA]]";
                    assertEquals(expected, pc);
                }
            }
            if ("containsA".equals(d.methodInfo().name)) {
                if ("0".equals(d.statementId())) {
                    assertEquals("Precondition[expression=!set.contains(\"a\"), causes=[escape]]", pc);
                }
                if ("1".equals(d.statementId())) {
                    assertEquals("Precondition[expression=true, causes=[]]", pc);
                }
            }
        };
        MethodAnalyserVisitor methodAnalyserVisitor = d -> {
            if ("test".equals(d.methodInfo().name)) {
                assertDv(d, 0, DV.FALSE_DV, Property.MODIFIED_METHOD);
                // no annotated APIs
                assertDv(d.p(0), 1, DV.TRUE_DV, Property.MODIFIED_VARIABLE);
            }
            if ("containsA".equals(d.methodInfo().name)) {
                assertDv(d.p(0), 1, DV.FALSE_DV, Property.MODIFIED_VARIABLE);
                assertDv(d, 0, DV.FALSE_DV, Property.MODIFIED_METHOD);
            }
        };
        // ignoring result of method call -> 1 warning
        testClass("Assert_2", 0, 1, new DebugConfiguration.Builder()
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .addStatementAnalyserVisitor(statementAnalyserVisitor)
                .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                .build());
    }

    @Test
    public void test_3() throws IOException {
        testClass("Assert_3", 0, 0, new DebugConfiguration.Builder()
                .build());
    }
}
