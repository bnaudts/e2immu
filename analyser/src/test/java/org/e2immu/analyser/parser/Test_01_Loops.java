/*
 * e2immu: code analyser for effective and eventual immutability
 * Copyright 2020, Bart Naudts, https://www.e2immu.org
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.e2immu.analyser.parser;

import org.e2immu.analyser.analyser.*;
import org.e2immu.analyser.config.DebugConfiguration;
import org.e2immu.analyser.visitor.EvaluationResultVisitor;
import org.e2immu.analyser.visitor.StatementAnalyserVariableVisitor;
import org.e2immu.analyser.visitor.StatementAnalyserVisitor;
import org.e2immu.analyser.model.Expression;
import org.e2immu.analyser.model.Level;
import org.e2immu.analyser.model.MultiLevel;
import org.e2immu.analyser.model.expression.*;
import org.e2immu.analyser.model.expression.util.EvaluateInlineConditional;
import org.e2immu.analyser.model.statement.ForEachStatement;
import org.e2immu.analyser.model.statement.WhileStatement;
import org.e2immu.analyser.model.variable.LocalVariableReference;
import org.e2immu.analyser.model.variable.ReturnVariable;
import org.e2immu.analyser.objectflow.ObjectFlow;
import org.junit.Assert;
import org.junit.Test;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.e2immu.analyser.analyser.FlowData.Execution.*;

public class Test_01_Loops extends CommonTestRunner {

    public static final String DELAYED_BY_STATE = "<s:String>";

    public Test_01_Loops() {
        super(true);
    }

    @Test
    public void test0() throws IOException {
        EvaluationResultVisitor evaluationResultVisitor = d -> {
            if (!"method".equals(d.methodInfo().name)) return;
            if ("2".equals(d.statementId())) {
                Assert.assertEquals("true", d.evaluationResult().value().debugOutput());
            }
            if ("2.0.1".equals(d.statementId())) {
                // NOTE: is i$2, and not i$2+1 because the operation is i++, not ++i
                String expect = d.iteration() == 0 ? "<v:i>" : "i$2";
                Assert.assertEquals(expect, d.evaluationResult().value().toString());
            }
            if ("2.0.2".equals(d.statementId())) {
                String expect = d.iteration() == 0 ? "<v:i>>=n" : "1+i$2>=n";
                Assert.assertEquals(expect, d.evaluationResult().value().toString());
            }
        };
        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if (!"method".equals(d.methodInfo().name)) return;
            if ("res1".equals(d.variableName())) {
                Assert.assertTrue(d.variable() instanceof LocalVariableReference);
                boolean expect = d.statementId().startsWith("2");
                boolean inLoop = d.variableInfoContainer().isLocalVariableInLoopDefinedOutside();
                Assert.assertEquals("In " + d.statementId(), expect, inLoop);

                if ("2.0.0".equals(d.statementId())) {
                    String expectValue = d.iteration() == 0 ? DELAYED_BY_STATE : "\"abc\"";
                    Assert.assertEquals(expectValue, d.currentValue().toString());
                }
            }
            if ("org.e2immu.analyser.testexample.Loops_0.this".equals(d.variableName())) {
                if ("2.0.0".equals(d.statementId())) {
                    Assert.assertTrue(d.variableInfo().getLinkedVariables().isEmpty());
                }
            }
            if ("i$2".equals(d.variableName())) {
                if ("2.0.0".equals(d.statementId())) {
                    Assert.assertTrue(d.iteration() > 0);
                    Assert.assertEquals("instance type int", d.currentValue().debugOutput());
                }
            }
            if ("i".equals(d.variableName())) {
                if (d.variable() instanceof LocalVariableReference) {
                    boolean expect = d.statementId().startsWith("2");
                    boolean inLoop = d.variableInfoContainer().isLocalVariableInLoopDefinedOutside();
                    Assert.assertEquals("In " + d.statementId(), expect, inLoop);
                } else Assert.fail();
                if ("1".equals(d.statementId())) {
                    Assert.assertEquals("0", d.currentValue().toString());
                }
                if ("2".equals(d.statementId())) {
                    String expect = d.iteration() == 0 ? "1+<v:i>" : "1+instance type int";
                    Assert.assertEquals(expect, d.currentValue().toString());
                }
                if ("2.0.1".equals(d.statementId())) {
                    String expect = d.iteration() == 0 ? "1+<v:i>" : "1+i$2";
                    Assert.assertEquals(expect, d.currentValue().toString());
                }
                if ("3".equals(d.statementId())) {
                    String expect = d.iteration() == 0 ? "<v:i>" : "1+instance type int";
                    Assert.assertEquals(expect, d.currentValue().toString());
                }
            }
        };

        StatementAnalyserVisitor statementAnalyserVisitor = d -> {
            if (!"method".equals(d.methodInfo().name)) return;
            if ("2".equals(d.statementId())) {
                if (d.statementAnalysis().statement instanceof WhileStatement whileStatement) {
                    FlowData.Execution exec = whileStatement.structure.statementExecution()
                            .apply(new BooleanConstant(d.statementAnalysis().primitives, true),
                                    d.evaluationContext());
                    Assert.assertSame(ALWAYS, exec);
                } else Assert.fail();
                String expectState = d.iteration() == 0 ? "<v:i>>=n" : "1+instance type int>=n";
                Assert.assertEquals(expectState, d.state().toString());

                Assert.assertEquals(ALWAYS, d.statementAnalysis().flowData.getGuaranteedToBeReachedInMethod());
                Assert.assertEquals(ALWAYS, d.statementAnalysis().flowData.getGuaranteedToBeReachedInCurrentBlock());
            }
            if ("2.0.0".equals(d.statementId())) {
                Assert.assertEquals("true", d.condition().toString());
                Assert.assertEquals("true", d.state().toString());
                Assert.assertEquals("true", d.localConditionManager().precondition().toString());
                if (d.iteration() == 0) {
                    VariableInfoContainer vic = d.statementAnalysis().variables.get("i");
                    Assert.assertEquals("0", vic.current().getValue().toString());
                }
                Assert.assertEquals(ALWAYS, d.statementAnalysis().flowData.getGuaranteedToBeReachedInMethod());
                Assert.assertEquals(ALWAYS, d.statementAnalysis().flowData.getGuaranteedToBeReachedInCurrentBlock());
            }
            if ("2.0.1".equals(d.statementId())) {
                Assert.assertEquals("true", d.localConditionManager().precondition().toString());
            }
            if ("2.0.2".equals(d.statementId())) {
                Assert.assertEquals("true", d.condition().toString());
                String expectState = d.iteration() == 0 ? "n-<v:i>>=1" : "-1-i$2+n>=1";
                Assert.assertEquals(expectState, d.state().toString());
                Assert.assertEquals(d.iteration() == 0, d.statementAnalysis()
                        .stateData.conditionManagerForNextStatement.isVariable());

                Assert.assertEquals(ALWAYS, d.statementAnalysis().flowData.getGuaranteedToBeReachedInMethod());
                Assert.assertEquals(ALWAYS, d.statementAnalysis().flowData.getGuaranteedToBeReachedInCurrentBlock());
            }
            // shows that the BREAK statement, always executed in its own block, is dependent on a valid condition
            if ("2.0.2.0.0".equals(d.statementId())) {
                String expect = d.iteration() == 0 ? "<v:i>>=n" : "1+i$2>=n";
                Assert.assertEquals(expect, d.condition().toString());
                FlowData.Execution expectExec = d.iteration() == 0 ? DELAYED_EXECUTION : CONDITIONALLY;
                Assert.assertEquals(expectExec, d.statementAnalysis().flowData.getGuaranteedToBeReachedInMethod());
                Assert.assertEquals(ALWAYS, d.statementAnalysis().flowData.getGuaranteedToBeReachedInCurrentBlock());
            }
        };
        testClass("Loops_0", 0, 0, new DebugConfiguration.Builder()
                .addStatementAnalyserVisitor(statementAnalyserVisitor)
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .addEvaluationResultVisitor(evaluationResultVisitor)
                .build());
    }

    @Test
    public void test1() throws IOException {
        // weirdly written but there is no dedicated logic in place
        final String END_RESULT = "-1-(instance type int)+n>=1?\"abc\":nullable instance type String";
        final String END_RESULT_NO_OPERATIONS = "instance type int>=0?\"abc\":instance type String";

        EvaluationResultVisitor evaluationResultVisitor = d -> {
            if ("method".equals(d.methodInfo().name)) {
                if ("2.0.0".equals(d.statementId())) {
                    String expect = d.iteration() == 0 ? "1+<v:i>" : "1+i$2";
                    Assert.assertEquals(expect, d.evaluationResult().value().toString());
                }
                if ("2.0.1".equals(d.statementId())) {
                    String expect = d.iteration() == 0 ? "<v:i>>=n" : "1+i$2>=n";
                    Assert.assertEquals(expect, d.evaluationResult().value().toString());
                }
                if ("3".equals(d.statementId())) {
                    String expect = d.iteration() == 0 ? DELAYED_BY_STATE : END_RESULT;
                    Assert.assertEquals(expect, d.evaluationResult().value().toString());
                }
            }
        };
        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if (!"method".equals(d.methodInfo().name)) return;
            if ("res2$2$2_0_2-E".equals(d.variableName())) {
                Assert.assertTrue("In " + d.statementId() + ", it " + d.iteration(), d.variableInfo().isRead());
            }
            if ("res2$2".equals(d.variableName())) {
                Assert.assertTrue(d.iteration() > 0);
                if ("2.0.0".equals(d.statementId())) {
                    Assert.assertEquals("nullable instance type String", d.currentValue().toString());
                    Assert.assertEquals(MultiLevel.NULLABLE, d.getProperty(VariableProperty.NOT_NULL_EXPRESSION));
                }
                if ("2.0.2".equals(d.statementId())) {
                    Assert.assertEquals("nullable instance type String", d.currentValue().toString());
                    Assert.assertEquals(MultiLevel.NULLABLE, d.getProperty(VariableProperty.NOT_NULL_EXPRESSION));
                }
            }
            if ("res2".equals(d.variableName())) {
                if ("2.0.0".equals(d.statementId())) {
                    Assert.assertEquals("2", d.variableInfoContainer().getVariableInLoop()
                            .statementId(VariableInLoop.VariableType.IN_LOOP_DEFINED_OUTSIDE));
                }
                if ("2.0.1.0.0".equals(d.statementId())) {
                    Assert.assertEquals("2", d.variableInfoContainer().getVariableInLoop()
                            .statementId(VariableInLoop.VariableType.IN_LOOP_DEFINED_OUTSIDE));
                }
                if ("2.0.1".equals(d.statementId())) {
                    Assert.assertEquals("2", d.variableInfoContainer().getVariableInLoop()
                            .statementId(VariableInLoop.VariableType.IN_LOOP_DEFINED_OUTSIDE));
                }
                if ("2.0.2".equals(d.statementId())) {
                    // statement says: res="abc", but the value takes the state into account
                    String expectValue = d.iteration() == 0 ? DELAYED_BY_STATE : "-1-i$2+n>=1?\"abc\":res2$2";
                    Assert.assertEquals(expectValue, d.variableInfo().getValue().toString());
                    // clearly, NNE has to follow the value rather than the actual assignment
                    int expectNNE = d.iteration() == 0 ? Level.DELAY : MultiLevel.NULLABLE;
                    Assert.assertEquals(expectNNE, d.getProperty(VariableProperty.NOT_NULL_EXPRESSION));
                }
                if ("2".equals(d.statementId())) {
                    String expectValue = d.iteration() == 0 ? DELAYED_BY_STATE : END_RESULT;
                    Assert.assertEquals(expectValue, d.variableInfo().getValue().toString());

                    // first, understanding how this works...
                    Primitives primitives = d.evaluationContext().getCurrentStatement().statementAnalysis.primitives;
                    NewObject string1 = NewObject.forTesting(primitives, primitives.stringParameterizedType);
                    Assert.assertEquals(MultiLevel.EFFECTIVELY_NOT_NULL, string1.getProperty(d.evaluationContext(), VariableProperty.NOT_NULL_EXPRESSION, true));
                    Map<VariableProperty, Integer> map = Map.of(VariableProperty.NOT_NULL_EXPRESSION, MultiLevel.NULLABLE);
                    Expression string1Wrapped = PropertyWrapper.propertyWrapperForceProperties(string1, map);
                    Assert.assertEquals(MultiLevel.NULLABLE, string1Wrapped.getProperty(d.evaluationContext(), VariableProperty.NOT_NULL_EXPRESSION, true));
                    Expression inline = EvaluateInlineConditional.conditionalValueConditionResolved(d.evaluationContext(),
                            GreaterThanZero.greater(d.evaluationContext(), NewObject.forTesting(primitives, primitives.intParameterizedType),
                                    new IntConstant(primitives, 0), true),
                            new StringConstant(primitives, "abc"),
                            string1Wrapped, ObjectFlow.NO_FLOW).value();
                    Assert.assertEquals(END_RESULT_NO_OPERATIONS, inline.toString());
                    Assert.assertEquals(MultiLevel.NULLABLE, inline.getProperty(d.evaluationContext(), VariableProperty.NOT_NULL_EXPRESSION, true));
                }
            }
        };

        StatementAnalyserVisitor statementAnalyserVisitor = d -> {
            if ("method".equals(d.methodInfo().name)) {
                FlowData.Execution execution = d.statementAnalysis().flowData.getGuaranteedToBeReachedInCurrentBlock();
                if ("2.0.0".equals(d.statementId())) {
                    Assert.assertEquals("true", d.condition().debugOutput());
                    Assert.assertEquals("true", d.absoluteState().debugOutput());
                    Assert.assertSame(ALWAYS, execution);
                }
                if ("2.0.1.0.0".equals(d.statementId())) {
                    String expectCondition = d.iteration() == 0 ? "<v:i>>=n" : "1+i$2>=n";
                    Assert.assertEquals(expectCondition, d.condition().toString());
                    Assert.assertEquals(d.iteration() == 0, d.localConditionManager().isDelayed());
                }
                if ("2.0.1".equals(d.statementId())) { // if (i>=n) break;
                    Assert.assertSame(ALWAYS, execution);

                    // both are NO_VALUE in the first iteration, because we're showing the stateData
                    // and not the local condition manager
                    Assert.assertEquals("true", d.condition().debugOutput());
                    String expectState = d.iteration() == 0 ? "n-<v:i>>=1" : "-1-i$2+n>=1";
                    Assert.assertEquals(expectState, d.absoluteState().toString());
                    Assert.assertEquals(d.iteration() == 0, d.conditionManagerForNextStatement().isDelayed());
                }
                if ("2.0.2".equals(d.statementId())) { // res2 = "abc"
                    Assert.assertEquals("true", d.condition().debugOutput());
                    Assert.assertEquals(d.iteration() == 0, d.localConditionManager().isDelayed());

                    String expectState = d.iteration() == 0 ? "n-<v:i>>=1" : "-1-i$2+n>=1";

                    Assert.assertEquals(expectState, d.localConditionManager().state().toString());
                    Assert.assertEquals(expectState, d.absoluteState().toString());

                    FlowData.Execution expect = d.iteration() == 0 ? DELAYED_EXECUTION : CONDITIONALLY;
                    Assert.assertSame(expect, execution);
                }
            }
        };

        testClass("Loops_1", 0, 0, new DebugConfiguration.Builder()
                .addEvaluationResultVisitor(evaluationResultVisitor)
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .addStatementAnalyserVisitor(statementAnalyserVisitor)
                .build());
    }

    @Test
    public void test2() throws IOException {
        EvaluationResultVisitor evaluationResultVisitor = d -> {
            if (!"method".equals(d.methodInfo().name)) return;
            if ("1".equals(d.statementId())) {
                Assert.assertEquals("{\"a\",\"b\",\"c\"}", d.evaluationResult().value().toString());
                Assert.assertEquals(MultiLevel.EFFECTIVELY_CONTENT_NOT_NULL, d.evaluationResult().value()
                        .getProperty(d.evaluationResult().evaluationContext(), VariableProperty.NOT_NULL_EXPRESSION, true));
            }
            if ("1.0.0".equals(d.statementId()) && d.iteration() > 0) {
                Assert.assertEquals("s$1", d.evaluationResult().value().toString());
            }
        };
        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if (!"method".equals(d.methodInfo().name)) return;
            if ("s".equals(d.variableName())) {
                if ("1.0.0".equals(d.statementId())) {
                    Assert.assertEquals("java.lang.String", d.variableInfo().variable()
                            .parameterizedType().typeInfo.fullyQualifiedName);
                    if (d.iteration() == 0) {
                        Assert.assertEquals("<v:s>", d.currentValue().toString());
                        Assert.assertEquals(Level.DELAY, d.getProperty(VariableProperty.NOT_NULL_EXPRESSION));
                    } else {
                        // the ENN has been set on s$1, not on s
                        Assert.assertEquals(Level.DELAY, d.getProperty(VariableProperty.NOT_NULL_EXPRESSION));
                        Assert.assertEquals("instance type String", d.currentValue().toString());
                    }
                    Assert.assertEquals("", d.variableInfo().getLinkedVariables().toString());
                }
            }
            if ("s$1".equals(d.variableName())) {
                Assert.assertTrue(d.iteration() > 0);
                if ("1.0.0".equals(d.statementId())) {
                    Assert.assertEquals("nullable instance type String", d.currentValue().toString());
                    Assert.assertEquals(MultiLevel.EFFECTIVELY_NOT_NULL, d.getProperty(VariableProperty.CONTEXT_NOT_NULL));
                    Assert.assertEquals(MultiLevel.NULLABLE, d.getProperty(VariableProperty.NOT_NULL_EXPRESSION));
                    Assert.assertEquals("s", d.variableInfo().getStaticallyAssignedVariables().toString());
                }
            }
            if ("res$1$1_0_0-E".equals(d.variableName())) {
                Assert.assertEquals("1.0.0", d.statementId());

                String expectValue = d.iteration() == 0 ? "<v:s>" : "s$1";
                Assert.assertEquals(expectValue, d.currentValue().toString());
                Assert.assertEquals("s", d.variableInfo().getStaticallyAssignedVariables().toString());
                Assert.assertEquals("", d.variableInfo().getLinkedVariables().toString());
                int expectNN = d.iteration() == 0 ? Level.DELAY : MultiLevel.EFFECTIVELY_NOT_NULL;
                Assert.assertEquals(expectNN, d.getProperty(VariableProperty.NOT_NULL_EXPRESSION));
            }
            if ("res".equals(d.variableName())) {
                if ("1.0.0".equals(d.statementId())) {
                    String expectValue = d.iteration() == 0 ? "<v:s>" : "s$1";
                    Assert.assertEquals(expectValue, d.currentValue().toString());
                    Assert.assertEquals("", d.variableInfo().getLinkedVariables().toString());
                }
                if ("1".equals(d.statementId())) {
                    String expectValue = d.iteration() == 0 ? "<new:String>" : "nullable instance type String";
                    Assert.assertEquals(expectValue, d.currentValue().toString());
                }
                if ("2".equals(d.statementId())) {
                    String expectValue = d.iteration() == 0 ? "<new:String>" : "nullable instance type String";
                    Assert.assertEquals(expectValue, d.currentValue().toString());
                    int expectNne = d.iteration() == 0 ? Level.DELAY : MultiLevel.EFFECTIVELY_NOT_NULL;
                    Assert.assertEquals(expectNne, d.getProperty(VariableProperty.NOT_NULL_EXPRESSION));
                }
            }
            if ("org.e2immu.analyser.testexample.Loops_2.method()".equals(d.variableName())) {
                if ("2".equals(d.statementId())) {
                    String expect = d.iteration() == 0 ? "<new:String>" : "res"; // indirection
                    Assert.assertEquals(expect, d.currentValue().toString());
                }
            }
        };

        testClass("Loops_2", 0, 0, new DebugConfiguration.Builder()
                .addEvaluationResultVisitor(evaluationResultVisitor)
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .build());
    }

    // explicitly empty loop
    @Test
    public void test3() throws IOException {
        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if ("method".equals(d.methodInfo().name)) {
                if ("1".equals(d.statementId()) && "s".equals(d.variableName())) {
                    Assert.assertEquals("1", d.variableInfoContainer().getStatementIndexOfThisLoopVariable());
                }
                if ("res".equals(d.variableName())) {
                    if ("0".equals(d.statementId())) {
                        Assert.assertEquals("\"a\"", d.currentValue().toString());
                    }
                    if ("1".equals(d.statementId())) {
                        // make sure that res isn't messed with
                        VariableInfo initial = d.variableInfoContainer().getPreviousOrInitial();
                        Assert.assertEquals("\"a\"", initial.getValue().toString());

                        // once we have determined that the loop is empty, the merger should take the original value
                        String expectValue = "\"a\"";
                        Assert.assertEquals(expectValue, d.currentValue().toString());
                        String expectLinked = "";
                        Assert.assertEquals(expectLinked, d.variableInfo().getLinkedVariables().toString());
                    }
                }
            }
        };

        StatementAnalyserVisitor statementAnalyserVisitor = d -> {
            if ("method".equals(d.methodInfo().name)) {
                if ("1.0.0".equals(d.statementId())) {
                    Assert.assertEquals("statement should be unreachable after iteration 0", 0, d.iteration());
                }
                if ("1".equals(d.statementId())) {
                    if (d.statementAnalysis().statement instanceof ForEachStatement forEachStatement) {
                        FlowData.Execution exec = forEachStatement.structure.statementExecution()
                                .apply(new ArrayInitializer(d.statementAnalysis().primitives, ObjectFlow.NO_FLOW,
                                        List.of(), d.statementAnalysis().primitives.stringParameterizedType), d.evaluationContext());
                        Assert.assertSame(FlowData.Execution.NEVER, exec);

                        StatementAnalysis firstInBlock = d.statementAnalysis().navigationData.blocks.get().get(0).orElseThrow();
                        Assert.assertEquals("1.0.0", firstInBlock.index);
                        if (d.iteration() > 0) {
                            Assert.assertTrue(firstInBlock.flowData.isUnreachable());
                            Assert.assertNotNull(d.haveError(Message.EMPTY_LOOP));
                        }
                    } else Assert.fail();
                }
                if ("2".equals(d.statementId())) {
                    Assert.assertFalse(d.statementAnalysis().variables.isSet("s"));
                }
            }
        };
        // empty loop
        testClass("Loops_3", 1, 0, new DebugConfiguration.Builder()
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .addStatementAnalyserVisitor(statementAnalyserVisitor)
                .build());
    }

    @Test
    public void test4() throws IOException {
        StatementAnalyserVisitor statementAnalyserVisitor = d -> {
            if ("method".equals(d.methodInfo().name)) {
                if ("0".equals(d.statementId())) {
                    String expect = d.iteration() == 0 ? "<new:int>>=10" : "instance type int>=10";
                    Assert.assertEquals(expect, d.state().toString());
                    Assert.assertNull(d.haveError(Message.INLINE_CONDITION_EVALUATES_TO_CONSTANT));
                }
                if ("1".equals(d.statementId())) {
                    Assert.assertEquals("true", d.condition().toString());
                    String expectState = d.iteration() == 0 ? "<new:int>>=10" : "instance type int>=10";
                    Assert.assertEquals(expectState, d.state().toString());
                    Assert.assertNull(d.haveError(Message.INLINE_CONDITION_EVALUATES_TO_CONSTANT));
                }
            }

        };

        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if ("method".equals(d.methodInfo().name)) {
                if ("i".equals(d.variableName())) {
                    Assert.assertEquals("0", d.variableInfoContainer().getStatementIndexOfThisLoopVariable());
                    if ("0.0.0.0.0".equals(d.statementId())) {
                        String expect = d.iteration() == 0 ? "<v:i>" : "1+i$0";
                        Assert.assertEquals(expect, d.currentValue().toString());
                    }
                    if ("0.0.0".equals(d.statementId())) {
                        String expect = d.iteration() == 0 ? "1==<v:i>?<v:i>:1+<v:i>" : "1+i$0";
                        Assert.assertEquals(expect, d.currentValue().toString());
                    }
                    if ("0".equals(d.statementId())) {
                        String expect = d.iteration() == 0 ? "1+<v:i>" : "1+i$0";
                        Assert.assertEquals(expect, d.currentValue().toString());
                    }
                }
                if (d.variable() instanceof ReturnVariable) {
                    Assert.assertEquals("org.e2immu.analyser.testexample.Loops_4.method()", d.variableName());
                    if ("0.0.0.0.0".equals(d.statementId())) {
                        // delayed state
                        String expect = d.iteration() == 0 ? "<s:int>" : "4";
                        Assert.assertEquals(expect, d.currentValue().toString());
                    }
                    if ("0.0.0".equals(d.statementId())) {
                        String expect = d.iteration() == 0 ? "1==<v:i>?<s:int>:<return value>" : "0==i$0?4:<return value>";
                        Assert.assertEquals(expect, d.currentValue().toString());
                    }
                    if ("0".equals(d.statementId())) {
                        String expect = d.iteration() == 0 ? "<new:int><=9?1==<new:int>?<s:int>:<return value>:<return value>"
                                : "instance type int<=9?0==instance type int?4:<return value>:<return value>";
                        Assert.assertEquals(expect, d.currentValue().toString());
                    }
                    if ("1".equals(d.statementId())) {
                        String expect = d.iteration() == 0
                                ? "<new:int>>=10?0:<new:int><=9&&<new:int><=9?1==<new:int>&&<new:int><=9?<s:int>:<return value>:<return value>"
                                : "instance type int>=10?0:0==instance type int?4:<return value>";
                        Assert.assertEquals(expect, d.currentValue().toString());
                    }
                }
            }
        };

        EvaluationResultVisitor evaluationResultVisitor = d -> {
            if ("method".equals(d.methodInfo().name)) {
                if ("0".equals(d.statementId())) {
                    String expectValue = d.iteration() == 0 ? "<v:i><=9" : "i$0<=9";
                    Assert.assertEquals(expectValue, d.evaluationResult().value().toString());
                }
                if ("0.0.0".equals(d.statementId())) {
                    String expectValue = d.iteration() == 0 ? "1==<v:i>" : "0==i$0";
                    Assert.assertEquals(expectValue, d.evaluationResult().value().toString());
                }
            }
        };

        testClass("Loops_4", 0, 0, new DebugConfiguration.Builder()
                .addEvaluationResultVisitor(evaluationResultVisitor)
                .addStatementAnalyserVisitor(statementAnalyserVisitor)
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .build());
    }

    @Test
    public void test5() throws IOException {
        EvaluationResultVisitor evaluationResultVisitor = d -> {
            if ("method".equals(d.methodInfo().name)) {
                if ("1.0.0".equals(d.statementId())) {
                    // instead of 1==i$1, it is 0==i$1 because i's value is i$1+1
                    String expect = d.iteration() == 0 ? "1==<v:i>" : "0==i$1";
                    Assert.assertEquals(expect, d.evaluationResult().value().toString());
                }
                if ("2".equals(d.statementId())) {
                    String expect = d.iteration() == 0 ? "(<v:i><=9?1==<v:i>?<v:i>:1+<v:i>:1+<v:i>)>=10"
                            : "instance type int>=9";
                    Assert.assertEquals(expect, d.evaluationResult().value().toString());
                }
            }
        };
        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if (!"method".equals(d.methodInfo().name)) return;
            if ("i$1".equals(d.variableName())) {
                Assert.assertTrue(d.iteration() > 0);
                Assert.assertEquals("instance type int", d.currentValue().toString());
            }
            if ("i".equals(d.variableName())) {
                if ("1.0.0".equals(d.statementId())) {
                    Assert.assertEquals("1", d.variableInfoContainer().getVariableInLoop()
                            .statementId(VariableInLoop.VariableType.IN_LOOP_DEFINED_OUTSIDE));
                    String expect = d.iteration() == 0 ? "1==<v:i>?<v:i>:1+<v:i>" : "1+i$1";
                    Assert.assertEquals(expect, d.currentValue().toString());
                }
                if ("1".equals(d.statementId())) {
                    String expect = d.iteration() == 0 ? "<v:i><=9?1==<v:i>?<v:i>:1+<v:i>:1+<v:i>" : "1+instance type int";
                    if (d.iteration() > 0) Assert.assertTrue(d.variableInfoContainer().hasMerge());
                    Assert.assertEquals(expect, d.currentValue().toString());
                }
                if ("2".equals(d.statementId())) {
                    String expect = d.iteration() == 0 ? "<v:i><=9?1==<v:i>?<v:i>:1+<v:i>:1+<v:i>" : "1+instance type int";
                    Assert.assertEquals(expect, d.currentValue().toString());
                }
            }
            if (d.variable() instanceof ReturnVariable && "2".equals(d.statementId())) {
                String expectReturn = d.iteration() == 0 ? "<v:return method>" :
                        "instance type int<=9?0==instance type int?5:<return value>:<return value>";
                Assert.assertEquals(expectReturn, d.currentValue().toString());
            }
        };
        StatementAnalyserVisitor statementAnalyserVisitor = d -> {
            if ("method".equals(d.methodInfo().name) && "2".equals(d.statementId())) {
                String expectState = d.iteration() == 0 ? "<v:i>>=10" : "instance type int>=10";
                Assert.assertEquals(expectState, d.state().toString());
            }
        };
        // expect: warning: always true in assert
        testClass("Loops_5", 0, 1, new DebugConfiguration.Builder()
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .addStatementAnalyserVisitor(statementAnalyserVisitor)
                .addEvaluationResultVisitor(evaluationResultVisitor)
                .build());
    }

    @Test
    public void test6() throws IOException {
        testClass("Loops_6", 2, 0, new DebugConfiguration.Builder()
                .build());
    }

    @Test
    public void test7() throws IOException {
        EvaluationResultVisitor evaluationResultVisitor = d -> {
            if (!"method".equals(d.methodInfo().name)) return;
            if ("1".equals(d.statementId())) {
                String expect = d.iteration() == 0 ? "n><v:i>" : "n>i$1";
                Assert.assertEquals(expect, d.evaluationResult().value().toString());
            }
        };

        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if (!"method".equals(d.methodInfo().name)) return;
            if ("k".equals(d.variableName()) && "1.0.0".equals(d.statementId())) {
                String expect = d.iteration() == 0 ? "<v:i>" : "i$1";
                Assert.assertEquals(expect, d.currentValue().toString());
            }
        };
        StatementAnalyserVisitor statementAnalyserVisitor = d -> {
            if (!"method".equals(d.methodInfo().name)) return;
            if ("0".equals(d.statementId())) {
                Assert.assertSame(ALWAYS, d.statementAnalysis().flowData.getGuaranteedToBeReachedInMethod());
                Assert.assertSame(ALWAYS, d.statementAnalysis().flowData.getGuaranteedToBeReachedInCurrentBlock());
            }
            if ("1".equals(d.statementId())) {
                Assert.assertSame(ALWAYS, d.statementAnalysis().flowData.getGuaranteedToBeReachedInMethod());
                Assert.assertSame(ALWAYS, d.statementAnalysis().flowData.getGuaranteedToBeReachedInCurrentBlock());

                Assert.assertTrue(d.statementAnalysis().localVariablesAssignedInThisLoop.isFrozen());
                Assert.assertEquals("i", d.statementAnalysis().localVariablesAssignedInThisLoop.stream().collect(Collectors.joining()));
            }
            if ("1.0.0".equals(d.statementId())) {
                FlowData.Execution expectExec = d.iteration() == 0 ? DELAYED_EXECUTION : CONDITIONALLY;
                Assert.assertSame(expectExec, d.statementAnalysis().flowData.getGuaranteedToBeReachedInMethod());
                Assert.assertSame(ALWAYS, d.statementAnalysis().flowData.getGuaranteedToBeReachedInCurrentBlock());

                String expect = d.iteration() == 0 ? "n><v:i>" : "n>i$1";
                Assert.assertEquals(expect, d.absoluteState().toString());
            }
            if ("1.0.1".equals(d.statementId())) {
                String expect = d.iteration() == 0 ? "n><v:i>" : "n>i$1";
                Assert.assertEquals(expect, d.absoluteState().toString());
                Assert.assertEquals(expect, d.condition().toString());
                String expectInterrupt = "{}";
                Assert.assertEquals(expectInterrupt, d.statementAnalysis().flowData.getInterruptsFlow().toString());
            }
            if ("1.0.2".equals(d.statementId())) {
                String expect = d.iteration() == 0 ? "1+<v:k>!=<v:i>" : "false";
                Assert.assertEquals(expect, d.state().toString());
                if (d.iteration() == 0) {
                    Assert.assertFalse(d.statementAnalysis().flowData.interruptsFlowIsSet());
                } else {
                    Assert.assertEquals("{break=CONDITIONALLY}", d.statementAnalysis().flowData.getInterruptsFlow().toString());
                }
            }
            if ("1.0.2.0.0".equals(d.statementId())) {
                String expect = "{break=ALWAYS}";
                Assert.assertEquals(expect, d.statementAnalysis().flowData.getInterruptsFlow().toString());
                if (d.iteration() == 0) {
                    Assert.assertFalse(d.statementAnalysis().flowData.blockExecution.isSet());
                } else {
                    Assert.assertSame(CONDITIONALLY, d.statementAnalysis().flowData.blockExecution.get());
                }
            }
            if ("1.0.3".equals(d.statementId())) {
                FlowData.Execution expectExec = d.iteration() == 0 ? DELAYED_EXECUTION : FlowData.Execution.NEVER;
                Assert.assertSame(expectExec, d.statementAnalysis().flowData.getGuaranteedToBeReachedInMethod());
            }
        };

        testClass("Loops_7", 2, 0, new DebugConfiguration.Builder()
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .addStatementAnalyserVisitor(statementAnalyserVisitor)
                .addEvaluationResultVisitor(evaluationResultVisitor)
                .build());
    }

}
