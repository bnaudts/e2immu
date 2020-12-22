package org.e2immu.analyser.parser;

import org.e2immu.analyser.analyser.*;
import org.e2immu.analyser.config.*;
import org.e2immu.analyser.model.Level;
import org.e2immu.analyser.model.MultiLevel;
import org.e2immu.analyser.model.expression.EmptyExpression;
import org.junit.Assert;
import org.junit.Test;

import java.io.IOException;
import java.util.Map;

public class Test_02_ConditionalChecks extends CommonTestRunner {

    public static final String CONDITIONAL_CHECKS = "conditionalChecks";

    public Test_02_ConditionalChecks() {
        super(false);
    }

    @Test
    public void test0() throws IOException {
        final String RETURN1 = "org.e2immu.analyser.testexample.ConditionalChecks_0.method1(boolean,boolean)";
        final String RETURN_1_VALUE = "!a&&b?4:a&&!b?3:!a&&!b?2:a&&b?1:<return value>";

        StatementAnalyserVisitor statementAnalyserVisitor = d -> {
            FlowData.Execution inBlock = d.statementAnalysis().flowData.guaranteedToBeReachedInCurrentBlock.get();
            FlowData.Execution inMethod = d.statementAnalysis().flowData.guaranteedToBeReachedInMethod.get();
            Map<InterruptsFlow, FlowData.Execution> interruptsFlow = d.statementAnalysis().flowData.interruptsFlow.getOrElse(null);

            if ("method1".equals(d.methodInfo().name)) {

                if ("0.0.0".equals(d.statementId())) {
                    Assert.assertEquals("a&&b", d.condition().toString());
                    Assert.assertEquals("a&&b", d.state().toString());
                    Assert.assertEquals(FlowData.Execution.ALWAYS, inBlock);
                    Assert.assertEquals(FlowData.Execution.CONDITIONALLY, inMethod);
                    Assert.assertEquals(Map.of(InterruptsFlow.RETURN, FlowData.Execution.ALWAYS), interruptsFlow);
                }
                if ("0".equals(d.statementId())) {
                    Assert.assertEquals("false", d.condition().toString());
                    Assert.assertEquals("!a||!b", d.state().toString());
                    Assert.assertEquals(FlowData.Execution.ALWAYS, inBlock);
                    Assert.assertEquals(FlowData.Execution.ALWAYS, inMethod);
                    Assert.assertEquals(Map.of(InterruptsFlow.RETURN, FlowData.Execution.CONDITIONALLY), interruptsFlow);
                    Assert.assertEquals("true", d.statementAnalysis().methodLevelData.combinedPrecondition.get().toString());
                }
                if ("1.0.0".equals(d.statementId())) {
                    Assert.assertEquals("!a&&!b", d.condition().toString());
                    Assert.assertEquals("!a&&!b", d.state().toString());
                    Assert.assertEquals(FlowData.Execution.ALWAYS, inBlock);
                    Assert.assertEquals(FlowData.Execution.CONDITIONALLY, inMethod);
                }
                if ("1".equals(d.statementId())) {
                    Assert.assertEquals("(a||b)&&(!a||!b)", d.state().toString());
                    Assert.assertEquals(FlowData.Execution.CONDITIONALLY, inBlock);
                    Assert.assertEquals(FlowData.Execution.CONDITIONALLY, inMethod);
                    Assert.assertEquals("true", d.statementAnalysis().methodLevelData.combinedPrecondition.get().toString());
                }
                if ("2".equals(d.statementId())) {
                    Assert.assertEquals("a&&!b", d.statementAnalysis().stateData.valueOfExpression.get().toString());
                    Assert.assertEquals("!a&&b", d.state().toString());
                    Assert.assertEquals(FlowData.Execution.CONDITIONALLY, inBlock);
                    Assert.assertEquals(FlowData.Execution.CONDITIONALLY, inMethod);
                }
                // constant condition
                if ("3".equals(d.statementId())) {
                    Assert.assertEquals("true", d.statementAnalysis().stateData.valueOfExpression.get().toString());
                    Assert.assertEquals("false", d.state().toString()); // after the statement...
                    Assert.assertEquals("ERROR in M:method1:3: Condition in 'if' or 'switch' statement evaluates to constant",
                            d.haveError(Message.CONDITION_EVALUATES_TO_CONSTANT));
                    Assert.assertEquals(FlowData.Execution.CONDITIONALLY, inBlock);
                    Assert.assertEquals(FlowData.Execution.CONDITIONALLY, inMethod);
                    Assert.assertEquals("true", d.statementAnalysis().methodLevelData.combinedPrecondition.get().toString());
                }
                // unreachable statement
                if ("4".equals(d.statementId())) {
                    Assert.assertEquals(FlowData.Execution.NEVER, inBlock);
                    Assert.assertEquals(FlowData.Execution.NEVER, inMethod);
                    Assert.assertNotNull(d.haveError(Message.UNREACHABLE_STATEMENT));
                    Assert.assertFalse(d.statementAnalysis().methodLevelData.combinedPrecondition.isSet());
                }
                if ("5".equals(d.statementId())) {
                    Assert.assertEquals(FlowData.Execution.NEVER, inBlock);
                    Assert.assertEquals(FlowData.Execution.NEVER, inMethod);
                    Assert.assertNull(d.haveError(Message.UNREACHABLE_STATEMENT));
                    VariableInfo ret = d.getReturnAsVariable();
                    Assert.assertNull(ret); // unreachable statement, no data have even been copied!
                    Assert.assertFalse(d.statementAnalysis().methodLevelData.combinedPrecondition.isSet());
                }

            }
        };

        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if (RETURN1.equals(d.variableName())) {
                // return 1;
                if ("0.0.0".equals(d.statementId())) {
                    Assert.assertEquals("1", d.currentValue().toString());
                    Assert.assertEquals("a&&b", d.variableInfo().getStateOnAssignment().toString());
                }
                // after if(a&&b) return 1
                if ("0".equals(d.statementId())) {
                    Assert.assertEquals("a&&b?1:<return value>", d.currentValue().toString());
                    Assert.assertEquals("true", d.variableInfo().getStateOnAssignment().toString());
                }
                if ("1.0.0".equals(d.statementId())) {
                    Assert.assertEquals("2", d.currentValue().toString());
                    Assert.assertEquals("!a&&!b", d.variableInfo().getStateOnAssignment().toString());
                }
                // after if (!a && !b) return 2;
                if ("1".equals(d.statementId())) {
                    Assert.assertEquals("true", d.variableInfo().getStateOnAssignment().toString());
                    // we do NOT expect a regression to the ReturnVariable
                    Assert.assertEquals("!a&&!b?2:a&&b?1:<return value>",
                            d.currentValue().toString());
                }
                if ("2".equals(d.statementId())) {
                    Assert.assertEquals("true", d.variableInfo().getStateOnAssignment().toString());
                    // we do NOT expect a regression to the ReturnVariable
                    Assert.assertEquals("a&&!b?3:!a&&!b?2:a&&b?1:<return value>",
                            d.currentValue().toString());
                }
                if ("3".equals(d.statementId())) {
                    Assert.assertEquals("true", d.variableInfo().getStateOnAssignment().toString());
                    // we do NOT expect a regression to the ReturnVariable
                    Assert.assertEquals(RETURN_1_VALUE, d.currentValue().toString());
                }
                if ("4".equals(d.statementId())) {
                    Assert.fail("not reached!");
                }
            }
        };

        MethodAnalyserVisitor methodAnalyserVisitor = d -> {
            if ("method1".equals(d.methodInfo().name)) {
                Assert.assertEquals("true", d.methodAnalysis().getPrecondition().toString());
                Assert.assertEquals(Level.FALSE, d.methodAnalysis().getProperty(VariableProperty.MODIFIED));
                Assert.assertEquals(RETURN_1_VALUE, d.methodAnalysis().getSingleReturnValue().toString());
            }
        };

        testClass("ConditionalChecks_0", 2, 0, new DebugConfiguration.Builder()
                .addStatementAnalyserVisitor(statementAnalyserVisitor)
                .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .build(), new AnalyserConfiguration.Builder().setSkipTransformations(true).build());
    }

    @Test
    public void test1() throws IOException {
        StatementAnalyserVisitor statementAnalyserVisitor = d -> {
            if ("method2".equals(d.methodInfo().name) && "0.0.0".equals(d.statementId())) {
                Assert.assertEquals("null==a||null==b", d.state().toString());
                Assert.assertEquals("null==a||null==b", d.condition().toString());
            }
            if ("method2".equals(d.methodInfo().name) && "0".equals(d.statementId())) {
                Assert.assertEquals("null!=a&&null!=b", d.state().toString());
                Assert.assertEquals("false", d.condition().toString());
            }
        };
        testClass("ConditionalChecks_1", 0, 0, new DebugConfiguration.Builder()
                .addStatementAnalyserVisitor(statementAnalyserVisitor)
                .build(), new AnalyserConfiguration.Builder().setSkipTransformations(true).build());
    }

    @Test
    public void test2() throws IOException {
        final String RETURN3 = "org.e2immu.analyser.testexample.ConditionalChecks.method3(String,String)";
        final String A3 = RETURN3 + ":0:a";
        final String B3 = "org.e2immu.analyser.testexample.ConditionalChecks.method3(String,String):1:b";

        MethodAnalyserVisitor methodAnalyserVisitor = d -> {
            if ("method3".equals(d.methodInfo().name)) {
                Assert.assertEquals(MultiLevel.EFFECTIVELY_NOT_NULL, d.parameterAnalyses().get(0).getProperty(VariableProperty.NOT_NULL));
                Assert.assertEquals(MultiLevel.EFFECTIVELY_NOT_NULL, d.parameterAnalyses().get(1).getProperty(VariableProperty.NOT_NULL));
            }
        };

        StatementAnalyserVisitor statementAnalyserVisitor = d -> {
            if ("method3".equals(d.methodInfo().name)) {
                if (d.iteration() == 0) {
                    if ("0".equals(d.statementId())) {
                        Assert.assertEquals("false", d.condition().toString());
                        Assert.assertEquals("null!=a", d.state().toString());
                        Assert.assertTrue(d.statementAnalysis().stateData.statementContributesToPrecondition.isSet());
                    }
                    if ("0.0.0".equals(d.statementId())) {
                        Assert.assertEquals("null==a", d.condition().toString());
                        Assert.assertEquals("null==a", d.state().toString());
                        Assert.assertTrue(d.haveSetProperty(VariableProperty.NOT_NULL, MultiLevel.EFFECTIVELY_NOT_NULL));
                        Assert.assertFalse(d.statementAnalysis().stateData.statementContributesToPrecondition.isSet());
                    }
                    if ("1".equals(d.statementId())) {
                        Assert.assertEquals("false", d.condition().toString());
                        Assert.assertEquals("null!=a&&null!=b", d.state().toString());
                        Assert.assertTrue(d.statementAnalysis().stateData.statementContributesToPrecondition.isSet());
                    }
                }
            }
        };

        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if (RETURN3.equals(d.variableName())) {
                if ("0".equals(d.statementId())) {
                    Assert.assertEquals("<return value>", d.currentValue().toString());
                }
                if ("1".equals(d.statementId())) {
                    Assert.assertEquals("<return value>", d.currentValue().toString());
                }
                if ("2".equals(d.statementId())) {
                    Assert.assertEquals(A3 + " + " + B3, d.currentValue().toString());
                }
            }
        };

        testClass("ConditionalChecks_2", 0, 0, new DebugConfiguration.Builder()
                .addStatementAnalyserVisitor(statementAnalyserVisitor)
                .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .build(), new AnalyserConfiguration.Builder().setSkipTransformations(true).build());
    }


    @Test
    public void test_3() throws IOException {
        testClass("ConditionalChecks_3", 0, 0, new DebugConfiguration.Builder()
                .build(), new AnalyserConfiguration.Builder().setSkipTransformations(true).build());
    }


    @Test
    public void test_4() throws IOException {
        final String TYPE = "org.e2immu.analyser.testexample.ConditionalChecks_4";
        final String RETURN5 = TYPE + ".method5(Object)";
        final String O5 = RETURN5 + ":0:o";
        final String I = TYPE + ".i";
        final String CC_I = TYPE + ".i#" + O5;

        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if ("method5".equals(d.methodInfo().name)) {
                if ("0".equals(d.statementId())) {
                    if (O5.equals(d.variableName())) {
                        Assert.assertFalse(d.hasProperty(VariableProperty.NOT_NULL));
                    }
                    if (RETURN5.equals(d.variableName())) {
                        Assert.assertEquals("<return value>||o==this", d.currentValue().toString());
                    }
                }

                if (CONDITIONAL_CHECKS.equals(d.variableName())) {
                    if ("2".equals(d.statementId())) {
                        Assert.assertEquals("o", d.currentValue().toString());
                    }
                }
                if ("3".equals(d.statementId())) {
                    if (CC_I.equals(d.variableName())) {
                        String expectValue = d.iteration() == 0 ? EmptyExpression.NO_VALUE.toString() : "instance type int";
                        Assert.assertEquals(expectValue, d.currentValue().toString());
                    }
                    if (RETURN5.equals(d.variableName())) {
                        String expectValue = d.iteration() == 0 ? EmptyExpression.NO_VALUE.toString() : "i==o.i";
                        Assert.assertEquals(expectValue, d.currentValue().toString());
                        Assert.assertEquals(VariableInfoContainer.LEVEL_3_EVALUATION, d.variableInfoContainer().getCurrentLevel());
                    }
                }
            }
        };

        StatementAnalyserVisitor statementAnalyserVisitor = d -> {
            if ("method5".equals(d.methodInfo().name)) {
                // the escape mechanism does NOT kick in!
                if ("0".equals(d.statementId())) {
                    Assert.assertEquals("o!=this", d.state().toString());
                } else if ("0.0.0".equals(d.statementId())) {
                    Assert.assertEquals("o==this", d.state().toString());
                } else if ("1.0.0".equals(d.statementId())) {
                    Assert.assertEquals("o!=this&&(null==o||o.getClass()!=this.getClass())", d.state().toString());
                } else {
                    Assert.assertEquals("null!=o&&o.getClass()==this.getClass()&&o!=this", d.state().toString());
                }
                if ("3".equals(d.statementId())) {
                    AnalysisStatus expectStatus = d.iteration() == 0 ? AnalysisStatus.PROGRESS : AnalysisStatus.DONE;
                    Assert.assertEquals(d.toString(), expectStatus, d.result().analysisStatus);
                } else {
                    Assert.assertEquals("Statement " + d.statementId() + " it " + d.iteration(), AnalysisStatus.DONE, d.result().analysisStatus);
                }
            }
        };

        MethodAnalyserVisitor methodAnalyserVisitor = d -> {
            if ("method5".equals(d.methodInfo().name)) {
                int expect = d.iteration() == 0 ? Level.DELAY : MultiLevel.NULLABLE;
                Assert.assertEquals(expect, d.parameterAnalyses().get(0).getProperty(VariableProperty.NOT_NULL));
            }
        };

        EvaluationResultVisitor evaluationResultVisitor = d -> {
            if ("method5".equals(d.methodInfo().name)) {
                if ("0".equals(d.statementId())) {
                    Assert.assertEquals(StatementAnalyser.STEP_3, d.step());
                    Assert.assertEquals("o==this", d.evaluationResult().value().toString());
                }
                if ("0.0.0".equals(d.statementId())) {
                    Assert.assertEquals(StatementAnalyser.STEP_3, d.step());
                    Assert.assertEquals("true", d.evaluationResult().value().toString());
                }
                if ("1".equals(d.statementId())) {
                    Assert.assertEquals(StatementAnalyser.STEP_3, d.step());
                    Assert.assertEquals("null==o||o.getClass()!=this.getClass()", d.evaluationResult().value().toString());
                    Assert.assertTrue(d.evaluationResult().getModificationStream().count() > 0);
                    Assert.assertTrue(d.haveMarkRead(O5));
                    Assert.assertFalse(d.haveSetProperty(O5, VariableProperty.NOT_NULL));
                }
                if ("2".equals(d.statementId())) {
                    Assert.assertFalse(d.haveSetProperty(O5, VariableProperty.NOT_NULL));
                    Assert.assertTrue(d.haveValueChange(CONDITIONAL_CHECKS));
                    Assert.assertEquals("o", d.findValueChange(CONDITIONAL_CHECKS).value().toString());
                    Assert.assertEquals("o", d.evaluationResult().value().toString());
                }
                if ("3".equals(d.statementId())) {
                    // there will be two iterations, in the second one, i will not have value "NO_VALUE" anymore
                    String expectValueString = d.iteration() == 0 ? EmptyExpression.NO_VALUE.toString() : "i==o.i";
                    Assert.assertEquals(expectValueString, d.evaluationResult().value().toString());
                    if (d.iteration() == 0) {
                        // markRead is only done in the first iteration
                        Assert.assertTrue(d.haveMarkRead(CONDITIONAL_CHECKS));
                        Assert.assertTrue(d.haveMarkRead(I));
                        Assert.assertTrue(d.haveMarkRead(I + "#" + O5));
                    }
                    Assert.assertFalse(d.haveSetProperty(O5, VariableProperty.NOT_NULL));
                    Assert.assertFalse(d.haveSetProperty(CONDITIONAL_CHECKS, VariableProperty.NOT_NULL));
                }
            }
        };

        testClass("ConditionalChecks_4", 0, 0, new DebugConfiguration.Builder()
                .addStatementAnalyserVisitor(statementAnalyserVisitor)
                .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .addEvaluationResultVisitor(evaluationResultVisitor)
                .build(), new AnalyserConfiguration.Builder().setSkipTransformations(true).build());
    }

    @Test
    public void test_5() throws IOException {
        testClass("ConditionalChecks_5", 0, 0, new DebugConfiguration.Builder()
                .build(), new AnalyserConfiguration.Builder().setSkipTransformations(true).build());
    }

    @Test
    public void test_6() throws IOException {
        testClass("ConditionalChecks_6", 0, 1, new DebugConfiguration.Builder()
                .build(), new AnalyserConfiguration.Builder().setSkipTransformations(true).build());
    }

}
