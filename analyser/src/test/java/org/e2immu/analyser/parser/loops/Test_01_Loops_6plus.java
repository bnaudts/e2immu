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

package org.e2immu.analyser.parser.loops;

import org.e2immu.analyser.analyser.ConditionManager;
import org.e2immu.analyser.analyser.DV;
import org.e2immu.analyser.analyser.Stage;
import org.e2immu.analyser.analyser.VariableInfo;
import org.e2immu.analyser.analysis.FlowData;
import org.e2immu.analyser.analysis.ParameterAnalysis;
import org.e2immu.analyser.analysis.impl.StatementAnalysisImpl;
import org.e2immu.analyser.config.DebugConfiguration;
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.model.variable.ReturnVariable;
import org.e2immu.analyser.model.variable.Variable;
import org.e2immu.analyser.model.variable.VariableNature;
import org.e2immu.analyser.parser.CommonTestRunner;
import org.e2immu.analyser.visitor.*;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.chrono.ChronoLocalDateTime;
import java.util.stream.Collectors;

import static org.e2immu.analyser.analyser.Property.*;
import static org.e2immu.analyser.analysis.FlowData.ALWAYS;
import static org.e2immu.analyser.analysis.FlowData.CONDITIONALLY;
import static org.junit.jupiter.api.Assertions.*;

public class Test_01_Loops_6plus extends CommonTestRunner {

    public Test_01_Loops_6plus() {
        super(true);
    }

    @Test
    public void test_6() throws IOException {
        // unused local variable
        testClass("Loops_6", 1, 0, new DebugConfiguration.Builder()
                .build());
    }

    @Test
    public void test_7() throws IOException {
        EvaluationResultVisitor evaluationResultVisitor = d -> {
            if ("method".equals(d.methodInfo().name)) {
                if ("1".equals(d.statementId())) {
                    String expect = d.iteration() == 0 ? "n><v:i>" : "n>i$1";
                    assertEquals(expect, d.evaluationResult().value().toString());
                }
                if ("1.0.0".equals(d.statementId())) {
                    String expect = d.iteration() == 0 ? "<v:i>" : "i$1";
                    assertEquals(expect, d.evaluationResult().value().toString());
                }
                if ("1.0.2".equals(d.statementId())) {
                    // in the first iteration (0), we compare 1+<v:i> against <v:i>, which should be false but because of
                    // the equality in DVE, we cannot tell
                    String expect = d.iteration() == 0 ? "<simplification>" : "true";
                    assertEquals(expect, d.evaluationResult().value().toString());
                }
            }
        };

        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if ("method".equals(d.methodInfo().name)) {
                if ("k".equals(d.variableName())) {
                    if ("1.0.0".equals(d.statementId()) || "1.0.1".equals(d.statementId())
                            || "1.0.2".equals(d.statementId())) {
                        String expect = d.iteration() == 0 ? "<v:i>" : "i$1";
                        assertEquals(expect, d.currentValue().toString());
                    }
                }
                if ("i".equals(d.variableName())) {
                    if ("1.0.0".equals(d.statementId())) {
                        String expect = d.iteration() == 0 ? "<vl:i>" : "instance type int";
                        assertEquals(expect, d.currentValue().toString());
                    }
                    if ("1.0.1".equals(d.statementId())) {
                        // it 1: 1+i$1 rather than n>i$1?1+i$1:i$1
                        String expect = d.iteration() == 0 ? "1+<v:i>" : "1+i$1";
                        assertEquals(expect, d.currentValue().toString());
                    }
                }
            }
        };
        StatementAnalyserVisitor statementAnalyserVisitor = d -> {
            if ("method".equals(d.methodInfo().name)) {
                if ("0".equals(d.statementId())) {
                    assertEquals(ALWAYS, d.statementAnalysis().flowData().getGuaranteedToBeReachedInMethod());
                    assertEquals(ALWAYS, d.statementAnalysis().flowData().getGuaranteedToBeReachedInCurrentBlock());
                }
                if ("1".equals(d.statementId())) {
                    assertEquals(ALWAYS, d.statementAnalysis().flowData().getGuaranteedToBeReachedInMethod());
                    assertEquals(ALWAYS, d.statementAnalysis().flowData().getGuaranteedToBeReachedInCurrentBlock());

                    assertTrue(((StatementAnalysisImpl) d.statementAnalysis()).localVariablesAssignedInThisLoop.isFrozen());
                    assertEquals("i", ((StatementAnalysisImpl) d.statementAnalysis()).localVariablesAssignedInThisLoop.stream().collect(Collectors.joining()));
                }
                if ("1.0.0".equals(d.statementId())) {
                    assertDv(d, 1, CONDITIONALLY, d.statementAnalysis().flowData().getGuaranteedToBeReachedInMethod());
                    assertEquals(ALWAYS, d.statementAnalysis().flowData().getGuaranteedToBeReachedInCurrentBlock());

                    String expect = d.iteration() == 0 ? "n><v:i>" : "n>i$1";
                    assertEquals(expect, d.absoluteState().toString());
                }
                if ("1.0.1".equals(d.statementId())) {
                    String expect = d.iteration() == 0 ? "n><v:i>" : "n>i$1";
                    assertEquals(expect, d.absoluteState().toString());
                    assertEquals(expect, d.condition().toString());
                    String expectInterrupt = "{}";
                    assertEquals(expectInterrupt, d.statementAnalysis().flowData().getInterruptsFlow().toString());
                }
                if ("1.0.2".equals(d.statementId())) {
                    String expect = d.iteration() == 0 ? "!<simplification>" : "false";
                    assertEquals(expect, d.state().toString());
                    if (d.iteration() == 0) {
                        assertFalse(d.statementAnalysis().flowData().interruptsFlowIsSet());
                    } else {
                        assertEquals("{break=CONDITIONALLY:1}", d.statementAnalysis().flowData().getInterruptsFlow().toString());
                    }
                }
                if ("1.0.2.0.0".equals(d.statementId())) {
                    String expect = "{break=ALWAYS:2}"; // ALWAYS=value 2, see FlowData
                    assertEquals(expect, d.statementAnalysis().flowData().getInterruptsFlow().toString());
                    if (d.iteration() == 0) {
                        assertFalse(d.statementAnalysis().flowData().blockExecution.isSet());
                    } else {
                        assertSame(CONDITIONALLY, d.statementAnalysis().flowData().blockExecution.get());
                    }
                }
                if ("1.0.3".equals(d.statementId())) {
                    assertDv(d, 1, FlowData.NEVER, d.statementAnalysis().flowData().getGuaranteedToBeReachedInMethod());
                }
            }
        };

        // expression in if statement always true; unreachable statement
        testClass("Loops_7", 2, 0, new DebugConfiguration.Builder()
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .addStatementAnalyserVisitor(statementAnalyserVisitor)
                .addEvaluationResultVisitor(evaluationResultVisitor)
                .build());
    }

    @Test
    public void test_8() throws IOException {
        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if ("method".equals(d.methodInfo().name)) {
                if ("set".equals(d.variableName())) {
                    if ("0".equals(d.statementId())) {
                        assertEquals("new HashSet<>()/*AnnotatedAPI.isKnown(true)&&0==this.size()*/", d.currentValue().toString());
                    }
                    if ("1.0.0".equals(d.statementId())) {
                        String expected = d.iteration() == 0 ? "<mmc:set>" : "instance type Set<String>";
                        assertEquals(expected, d.currentValue().toString());
                    }
                    if ("2".equals(d.statementId())) {
                        String expected = d.iteration() == 0
                                ? "<vl:set>"
                                : "list.isEmpty()?new HashSet<>()/*AnnotatedAPI.isKnown(true)&&0==this.size()*/:instance type Set<String>";
                        assertEquals(expected, d.currentValue().toString());
                    }
                }
            }
        };
        testClass("Loops_8", 0, 0, new DebugConfiguration.Builder()
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .build());
    }

    @Test
    public void test_9() throws IOException {
        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if ("add".equals(d.methodInfo().name)) {
                if ("node".equals(d.variableName())) {
                    if ("0".equals(d.statementId())) {
                        String expect = d.iteration() == 0 ? "<f:in>" : "\"abc\"";
                        assertEquals(expect, d.currentValue().toString());
                    }
                    if ("1".equals(d.statementId())) {
                        String expect = d.iteration() == 0
                                ? "strings.length>0?null:<vl:node>"
                                // note: we lose the "abc" because of SAApply.setValueForVariablesInLoopDefinedOutsideAssignedInside
                                : "strings.length>0?null:nullable instance type String";
                        assertEquals(expect, d.currentValue().toString());
                    }
                }
            }
        };
        testClass("Loops_9", 0, 1, new DebugConfiguration.Builder()
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .build());
    }

    @Test
    public void test_10() throws IOException {
        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if ("add".equals(d.methodInfo().name)) {
                if ("node".equals(d.variableName())) {
                    if ("0".equals(d.statementId())) {
                        String expect = d.iteration() == 0 ? "<f:in>" : "\"abc\"";
                        assertEquals(expect, d.currentValue().toString());
                    }
                    if ("1".equals(d.statementId())) {
                        String expect = d.iteration() == 0
                                ? "strings.isEmpty()?<vl:node>:null"
                                // note: we lose the "abc" because of SAApply.setValueForVariablesInLoopDefinedOutsideAssignedInside
                                : "strings.isEmpty()?nullable instance type String:null";
                        assertEquals(expect, d.currentValue().toString());
                    }
                }
            }
        };
        testClass("Loops_10", 0, 1, new DebugConfiguration.Builder()
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .build());
    }


    @Test
    public void test_11() throws IOException {
        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if ("method".equals(d.methodInfo().name)) {
                if ("java.time.ZoneOffset.UTC".equals(d.variableName())) {
                    if ("1".equals(d.statementId())) {
                        String expectValue = d.iteration() == 0 ? "<f:UTC>" : "nullable instance type ZoneOffset";
                        assertEquals(expectValue, d.currentValue().toString());
                        assertEquals("ZoneOffset.UTC:0", d.variableInfo().getLinkedVariables().toString());

                        assertEquals(DV.TRUE_DV, d.getProperty(CONTEXT_MODIFIED));
                    }
                }
                if ("result".equals(d.variableName())) {
                    if ("3.0.1.0.1.0.0".equals(d.statementId())) {
                        // important: because we're in a loop, we're not just adding one element; therefore,
                        // we cannot keep count, and erase all state
                        String expect = d.iteration() == 0 ? "<mmc:result>" : "instance type Map<String,String>";
                        assertEquals(expect, d.currentValue().toString());
                    }
                    if ("4".equals(d.statementId())) {
                        assertEquals(MultiLevel.EFFECTIVELY_NOT_NULL_DV, d.getProperty(CONTEXT_NOT_NULL));
                        assertEquals("result:0", d.variableInfo().getLinkedVariables().toString());
                    }
                    if ("5".equals(d.statementId())) {
                        assertEquals(MultiLevel.EFFECTIVELY_NOT_NULL_DV, d.getProperty(CONTEXT_NOT_NULL));
                    }
                }
                if (d.variable() instanceof ReturnVariable) {
                    if ("4".equals(d.statementId())) {
                        assertEquals("", d.variableInfo().getLinkedVariables().toString());
                        assertEquals(MultiLevel.NULLABLE_DV, d.getProperty(CONTEXT_NOT_NULL));
                    }
                    if ("5".equals(d.statementId())) {
                        String expectValue = d.iteration() == 0
                                ? "<vl:result>"
                                : "map.entrySet().isEmpty()||queried.contains((instance type Entry<String,String>).getKey())||(instance type Entry<String,String>).getValue().compareTo(now$3.toString())<=0?new HashMap<>()/*AnnotatedAPI.isKnown(true)&&0==this.size()*/:instance type Map<String,String>";
                        assertEquals(expectValue, d.currentValue().toString());
                        String expectLv = "result:0,return method:0";
                        assertEquals(expectLv, d.variableInfo().getLinkedVariables().toString());
                        assertEquals(MultiLevel.EFFECTIVELY_NOT_NULL_DV, d.getProperty(CONTEXT_NOT_NULL));
                        assertDv(d, 1, MultiLevel.EFFECTIVELY_NOT_NULL_DV, NOT_NULL_EXPRESSION);
                    }
                }
            }
        };
        StatementAnalyserVisitor statementAnalyserVisitor = d -> {
            if ("method".equals(d.methodInfo().name)) {
                if ("3".equals(d.statementId()) && d.iteration() > 0) {
                    assertEquals("count,entry",
                            ((StatementAnalysisImpl) d.statementAnalysis()).localVariablesAssignedInThisLoop.toImmutableSet()
                                    .stream().sorted().collect(Collectors.joining(",")));
                }
            }
        };
        TypeMapVisitor typeMapVisitor = typeMap -> {
            TypeInfo typeInfo = typeMap.get(LocalDateTime.class);
            MethodInfo now = typeInfo.findUniqueMethod("now", 0);
            assertTrue(now.methodInspection.get().isStatic());
            assertEquals(DV.FALSE_DV, now.methodAnalysis.get().getProperty(MODIFIED_METHOD));
            TypeInfo chrono = typeMap.get(ChronoLocalDateTime.class);
            MethodInfo toInstant = chrono.findUniqueMethod("toInstant", 1);
            assertFalse(toInstant.methodInspection.get().isStatic());
            assertEquals(DV.FALSE_DV, toInstant.methodAnalysis.get().getProperty(MODIFIED_METHOD));
            ParameterAnalysis utc = toInstant.parameterAnalysis(0);
            assertEquals(DV.TRUE_DV, utc.getProperty(MODIFIED_VARIABLE));
        };

        // potential null pointer exception
        testClass("Loops_11", 0, 1, new DebugConfiguration.Builder()
                .addStatementAnalyserVisitor(statementAnalyserVisitor)
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .addTypeMapVisitor(typeMapVisitor)
                .build());
    }


    @Test
    public void test_12() throws IOException {
        StatementAnalyserVisitor statementAnalyserVisitor = d -> {
            if ("method".equals(d.methodInfo().name)) {
                if ("2".equals(d.statementId()) && d.iteration() > 0) {
                    assertEquals("[i]", // and not RES!
                            ((StatementAnalysisImpl) d.statementAnalysis()).localVariablesAssignedInThisLoop.toImmutableSet().toString());
                }
            }
        };
        // errors:
        // 1- overwriting previous assignment: in condition
        // 2- empty loop
        // 3- unused local variable (in stmt 3, because the loop is empty, i is not used)
        // warning: parameter n not used
        testClass("Loops_12", 3, 1, new DebugConfiguration.Builder()
                .addStatementAnalyserVisitor(statementAnalyserVisitor)
                .build());
    }

    @Test
    public void test_13() throws IOException {
        StatementAnalyserVisitor statementAnalyserVisitor = d -> {
            if ("method".equals(d.methodInfo().name)) {
                if ("2".equals(d.statementId()) && d.iteration() > 0) {
                    assertEquals("i,res1",
                            ((StatementAnalysisImpl) d.statementAnalysis()).localVariablesAssignedInThisLoop.toImmutableSet()
                                    .stream().sorted().collect(Collectors.joining(",")));
                }
            }
        };
        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if ("method".equals(d.methodInfo().name)) {
                if ("i".equals(d.variableName())) {
                    if ("2.0.0".equals(d.statementId())) {
                        assertEquals("2-E", d.variableInfo().getAssignmentIds().toString());
                    }
                }
            }
        };

        // overwriting previous assignment, for i (in the condition) and res1
        // (even though the compiler forces us to assign a value, this is bad programming)
        testClass("Loops_13", 2, 0, new DebugConfiguration.Builder()
                .addStatementAnalyserVisitor(statementAnalyserVisitor)
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .build());
    }

    @Test
    public void test_14() throws IOException {
        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if ("method".equals(d.methodInfo().name)) {
                if ("i".equals(d.variableName())) {
                    if ("2.0.0".equals(d.statementId())) {
                        // the 0 is inaccessible, because inside loop 2, i$2 is read rather than i
                        // this you can see in the "j"-"2.0.0" value
                        String expect = d.iteration() == 0 ? "<vl:i>" : "instance type int";
                        assertEquals(expect, d.currentValue().toString());
                    }
                }
                if ("j".equals(d.variableName())) {
                    if ("2.0.0.0.0".equals(d.statementId())) {
                        assertEquals("10", d.currentValue().toString());
                    }
                    if ("2.0.0".equals(d.statementId())) {
                        String expect = d.iteration() == 0 ? "3==<v:i>?10:<vl:j>"
                                // note: we lose the 9 because of SAApply.setValueForVariablesInLoopDefinedOutsideAssignedInside
                                : "3==i$2?10:instance type int";
                        assertEquals(expect, d.currentValue().toString());
                    }
                    if ("2.0.1".equals(d.statementId()) || "2.0.2".equals(d.statementId())) {
                        String expect = d.iteration() == 0
                                ? "6==<v:i>?11:3==<v:i>?10:<vl:j>"
                                : "6==i$2?11:3==i$2?10:instance type int";
                        assertEquals(expect, d.currentValue().toString());
                    }
                }
            }
        };

        StatementAnalyserVisitor statementAnalyserVisitor = d -> {
            if ("method".equals(d.methodInfo().name)) {
                if ("2".equals(d.statementId())) {
                    assertTrue(((StatementAnalysisImpl) d.statementAnalysis()).localVariablesAssignedInThisLoop.isFrozen());
                    assertEquals("i,j", ((StatementAnalysisImpl) d.statementAnalysis()).localVariablesAssignedInThisLoop.toImmutableSet()
                            .stream().sorted().collect(Collectors.joining(",")));
                }
                if ("2.0.0".equals(d.statementId())) {
                    String expected = d.iteration() == 0 ? "n><v:i>" : "n>i$2";
                    assertEquals(expected, d.absoluteState().toString());
                }
            }
        };

        testClass("Loops_14", 0, 0, new DebugConfiguration.Builder()
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .addStatementAnalyserVisitor(statementAnalyserVisitor)
                .build());
    }

    // study a transfer of condition/state from loop variable to parameter
    @Test
    public void test_15() throws IOException {
        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if ("method".equals(d.methodInfo().name)) {
                if ("res".equals(d.variableName())) {
                    if ("1.0.0.0.0".equals(d.statementId())) {
                        assertEquals("4", d.currentValue().toString());
                    }
                    if ("1.0.0".equals(d.statementId())) {
                        // instead of in terms of i, we have an expression in terms of p: MergeHelper.rewriteConditionFromLoopVariableToParameter
                        String expected = d.iteration() == 0 ? "<c:boolean>?4:<vl:res>" : "p<=9&&p>=0&&i==p?4:instance type int";
                        assertEquals(expected, d.currentValue().toString());
                    }
                    if ("1".equals(d.statementId())) {
                        // value is not 4! p could be greater than 10, and then i can never reach p
                        String expected = d.iteration() == 0 ? "<loopIsNotEmptyCondition>&&<c:boolean>?4:<vl:res>"
                                : "instance type int<=9&&instance type int>=0&&p<=9&&p>=0&&instance type int==p?4:instance type int";
                        // TODO ugly, but solvable; all instances are equal to each other
                        assertEquals(expected, d.currentValue().toString());
                    }
                }
                if ("i".equals(d.variableName())) {
                    assertNotEquals("0", d.statementId());
                    assertNotEquals("2", d.statementId());
                    if ("1.0.0".equals(d.statementId())) {
                        if (d.variableInfoContainer().variableNature() instanceof VariableNature.LoopVariable lv) {
                            assertEquals("1", lv.statementIndex());
                        } else fail();
                    }
                }
            }
        };

        StatementAnalyserVisitor statementAnalyserVisitor = d -> {
            if ("method".equals(d.methodInfo().name)) {
                if ("1.0.0.0.0".equals(d.statementId())) {
                    String condition = d.iteration() == 0 ? "<c:boolean>" : "i==p";
                    assertEquals(condition, d.condition().toString());
                    String absState = d.iteration() == 0 ? "<c:boolean>&&<loopIsNotEmptyCondition>" : "i<=9&&i>=0&&i==p";
                    assertEquals(absState, d.absoluteState().toString());
                }
                if ("1.0.0".equals(d.statementId())) {
                    String condition = d.iteration() == 0 ? "<loopIsNotEmptyCondition>" : "i<=9&&i>=0";
                    assertEquals(condition, d.condition().toString());
                    String absState = d.iteration() == 0 ? "<loopIsNotEmptyCondition>&&!<c:boolean>" : "i<=9&&i>=0&&i!=p";
                    assertEquals(absState, d.absoluteState().toString());
                }
                if ("1".equals(d.statementId())) {
                    assertEquals("true", d.condition().toString());
                    assertEquals("true", d.absoluteState().toString());

                    // check states of interrupt
                    var list = d.statementAnalysis().stateData().statesOfInterruptsStream().toList();
                    assertEquals(1, list.size());
                    String breakStmtIndex = list.get(0).getKey();
                    assertEquals("1.0.0.0.1", breakStmtIndex);
                    Expression state = list.get(0).getValue().get();
                    String expected = d.iteration() == 0 ? "<c:boolean>&&<loopIsNotEmptyCondition>" : "i<=9&&i>=0&&i==p";
                    assertEquals(expected, state.toString());
                }
            }
        };
        testClass("Loops_15", 0, 0, new DebugConfiguration.Builder()
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .addStatementAnalyserVisitor(statementAnalyserVisitor)
                .build());
    }

    @Test
    public void test_16() throws IOException {
        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if ("method".equals(d.methodInfo().name)) {
                if ("res".equals(d.variableName())) {
                    if ("1.0.0".equals(d.statementId())) {
                        String expected = d.iteration() == 0 ? "9==entry.getValue()?4:<vl:res>" : "9==entry.getValue()?4:instance type int";
                        assertEquals(expected, d.currentValue().toString());
                    }
                    if ("1".equals(d.statementId())) {
                        VariableInfo eval = d.variableInfoContainer().best(Stage.EVALUATION);
                        String expectEval = d.iteration() == 0 ? "<vl:res>" : "instance type int";
                        assertEquals(expectEval, eval.getValue().toString());

                        String expected = d.iteration() == 0
                                ? "map.entrySet().isEmpty()||9!=(instance type Entry<String,Integer>).getValue()?<vl:res>:4"
                                : "map.entrySet().isEmpty()||9!=(instance type Entry<String,Integer>).getValue()?instance type int:4";
                        assertEquals(expected, d.currentValue().toString());
                    }
                }
            }
        };
        StatementAnalyserVisitor statementAnalyserVisitor = d -> {
            if ("method".equals(d.methodInfo().name)) {
                if ("1.0.0".equals(d.statementId())) {
                    assertEquals("!map.entrySet().isEmpty()", d.condition().toString());
                    assertEquals("!map.entrySet().isEmpty()&&9!=entry.getValue()", d.absoluteState().toString());
                }
                if ("1".equals(d.statementId())) {
                    assertEquals("true", d.condition().toString());
                    assertEquals("true", d.state().toString());
                }
            }
        };
        testClass("Loops_16", 0, 0, new DebugConfiguration.Builder()
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .addStatementAnalyserVisitor(statementAnalyserVisitor)
                .build());
    }

    @Test
    public void test_17() throws IOException {
        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if ("method".equals(d.methodInfo().name)) {
                if ("res".equals(d.variableName())) {
                    if ("1".equals(d.statementId())) {
                        String expected = d.iteration() == 0 ? "<loopIsNotEmptyCondition>&&9==<m:getValue>?4:<vl:res>"
                                : "map$0.entrySet().isEmpty()||9!=(instance type Entry<String,Integer>).getValue()?instance type int:4";
                        assertEquals(expected, d.currentValue().toString());
                    }
                }
            }
        };
        testClass("Loops_17", 0, 1, new DebugConfiguration.Builder()
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .build());
    }


    // looks very much like Project_0.recentlyReadAndUpdatedAfterwards, which has multiple problems
    // 20211015: there's a 3rd linked1Variables value for 1.0.1.0.0: empty

    // this is the "earliest" test with a DelayedVariableOutOfScope expression as part of the scope of a variable
    @Test
    public void test_18() throws IOException {

        EvaluationResultVisitor evaluationResultVisitor = d -> {
            if ("1".equals(d.statementId())) {
                String expectValue = d.iteration() == 0 ? "<m:entrySet>" : "kvStore$0.entrySet()";
                assertEquals(expectValue, d.evaluationResult().value().toString());
            }
            if ("1.0.1.0.0".equals(d.statementId())) {
                String expectValue = d.iteration() == 0 ? "<m:getValue>" : "entry.getValue()";
                assertEquals(expectValue, d.evaluationResult().value().toString());
            }
        };

        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if ("method".equals(d.methodInfo().name)) {
                if ("result".equals(d.variableName())) {
                    if ("1".equals(d.statementId())) {
                        String expected = switch (d.iteration()) {
                            case 0 -> "<vl:result>";
                            case 1 -> "kvStore$0.entrySet().isEmpty()||queried.contains((instance type Entry<String,Container>).getKey())||0==<f:<out of scope:container:1.0.1>.read>?new HashMap<>()/*AnnotatedAPI.isKnown(true)&&0==this.size()*/:<mmc:result>";
                            default -> "kvStore$0.entrySet().isEmpty()||queried.contains((instance type Entry<String,Container>).getKey())||0==(instance type Entry<String,Container>).getValue().read?new HashMap<>()/*AnnotatedAPI.isKnown(true)&&0==this.size()*/:instance type Map<String,String>";
                        };
                        assertEquals(expected, d.currentValue().toString());
                        if (d.iteration() > 1) {
                            String expectVars = "[(instance type Entry<String,Container>).getValue().read, kvStore, org.e2immu.analyser.parser.loops.testexample.Loops_18.method(java.util.Set<java.lang.String>):0:queried]";
                            assertEquals(expectVars, d.currentValue().variables(true).stream().map(Variable::toString).sorted().toList().toString());
                        }
                    }
                }
                if ("entry".equals(d.variableName())) {
                    if ("1.0.0".equals(d.statementId())) {
                        String expectLv = d.iteration() == 0 ? "entry:0,key:-1" : "entry:0";
                        assertEquals(expectLv, d.variableInfo().getLinkedVariables().toString());
                    }

                    if ("1.0.1.0.0".equals(d.statementId())) {
                        String expectL1 = switch (d.iteration()) {
                            case 0 -> "container:-1,entry:0,key:-1";
                            case 1 -> "container:-1,entry:0";
                            default -> "entry:0";
                        };
                        assertEquals(expectL1, d.variableInfo().getLinkedVariables().toString());
                    }
                }
                if ("org.e2immu.analyser.parser.loops.testexample.Loops_18.Container.read#<out of scope:container:1.0.1>".equals(d.variableName())) {
                    if ("1".equals(d.statementId())) {
                        String expected = switch (d.iteration()) {
                            case 0 -> "<f:read>";
                            case 1 -> "kvStore$0.entrySet().isEmpty()||queried.contains((instance type Entry<String,Container>).getKey())?instance type int:<f:read>";
                            default -> "instance type int";
                        };
                        assertEquals(expected, d.currentValue().toString());
                        assertDv(d, 2, MultiLevel.EFFECTIVELY_NOT_NULL_DV, NOT_NULL_EXPRESSION);
                        assertTrue(d.variableInfoContainer().variableNature() instanceof VariableNature.OutOfScopeVariable);
                    }
                    assertTrue(d.statementId().compareTo("2") < 0, "The variable should not exist beyond the merge!");
                }
            }
        };

        StatementAnalyserVisitor statementAnalyserVisitor = d -> {
            if ("method".equals(d.methodInfo().name)) {
                if ("1.0.1".equals(d.statementId())) {
                    if (d.iteration() == 0) {
                        ConditionManager cm = d.statementAnalysis().stateData().getConditionManagerForNextStatement();
                        assertEquals("CM{condition=<loopIsNotEmptyCondition>;parent=CM{condition=<loopIsNotEmptyCondition>;parent=CM{parent=CM{}}}}",
                                cm.toString());
                        assertTrue(cm.condition().isDelayed());
                        assertTrue(cm.isDelayed());
                    }
                }
            }
        };

        TypeAnalyserVisitor typeAnalyserVisitor = d -> {
            if ("Container".equals(d.typeInfo().simpleName)) {
                assertDv(d, 1, MultiLevel.EFFECTIVELY_RECURSIVELY_IMMUTABLE_DV, IMMUTABLE);
                assertDv(d, 1, MultiLevel.INDEPENDENT_DV, INDEPENDENT);
            }
        };

        testClass("Loops_18", 0, 1, new DebugConfiguration.Builder()
                .addEvaluationResultVisitor(evaluationResultVisitor)
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .addAfterTypeAnalyserVisitor(typeAnalyserVisitor)
                .addStatementAnalyserVisitor(statementAnalyserVisitor)
                .build());
    }


    // looks very much like Project_0.recentlyReadAndUpdatedAfterwards, which has multiple problems
    // Solution: DelayedExpression.translate()
    // Later, this test solved a delicate bug in SAI.mergeAction, where the wrong VIC was put in ignore,
    // causing a value to be overwritten (variable "key", overwrite in merge of statement 1, into value of 1.0.1).
    @Test
    public void test_19() throws IOException {
        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if ("method".equals(d.methodInfo().name)) {

                if (d.variable() instanceof ParameterInfo p && "readWithinMillis".equals(p.name)) {
                    if ("1.0.1".equals(d.statementId())) {
                        String expected = switch (d.iteration()) {
                            case 0 -> "<m:contains>?instance type long:<p:readWithinMillis>";
                            case 1, 2 -> "queried.contains(entry.getKey())?instance type long:<p:readWithinMillis>";
                            default -> "instance type long";
                        };
                        assertEquals(expected, d.currentValue().toString());
                        assertDv(d, 3, MultiLevel.EFFECTIVELY_NOT_NULL_DV, NOT_NULL_EXPRESSION);
                    }
                }

            }
        };

        testClass("Loops_19", 0, 4, new DebugConfiguration.Builder()
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .build());
    }
}
