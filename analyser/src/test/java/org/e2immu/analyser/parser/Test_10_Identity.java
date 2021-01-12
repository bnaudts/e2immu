package org.e2immu.analyser.parser;

import org.e2immu.analyser.analyser.LinkedVariables;
import org.e2immu.analyser.analyser.VariableInfo;
import org.e2immu.analyser.analyser.VariableInfoContainer;
import org.e2immu.analyser.analyser.VariableProperty;
import org.e2immu.analyser.config.*;
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.model.expression.EmptyExpression;
import org.e2immu.analyser.model.expression.PropertyWrapper;
import org.e2immu.analyser.model.expression.VariableExpression;
import org.junit.Assert;
import org.junit.Test;
import org.slf4j.Logger;

import java.io.IOException;

public class Test_10_Identity extends CommonTestRunner {
    public Test_10_Identity() {
        super(true);
    }

    private static final String IDEM = "org.e2immu.analyser.testexample.Identity_0.idem(String)";
    private static final String IDEM_S = IDEM + ":0:s";
    private static final String IDEM3 = "org.e2immu.analyser.testexample.Identity_0.idem3(String)";
    private static final String IDEM3_S = IDEM3 + ":0:s";

    StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
        if (d.methodInfo().name.equals("idem") && IDEM_S.equals(d.variableName())) {
            if ("0".equals(d.statementId())) {
                // strings are @NM, @E2Container by definition/API annotations
                Assert.assertEquals(Level.FALSE, d.getProperty(VariableProperty.MODIFIED));
                Assert.assertTrue(d.variableInfo().isRead());
                if (d.iteration() > 0) {
                    Assert.assertEquals(MultiLevel.EFFECTIVELY_E2IMMUTABLE, d.getProperty(VariableProperty.IMMUTABLE));
                    Assert.assertEquals(Level.TRUE, d.getProperty(VariableProperty.CONTAINER));

                    // there is an explicit @NotNull on the first parameter of debug
                    Assert.assertEquals(MultiLevel.EFFECTIVELY_NOT_NULL, d.getProperty(VariableProperty.NOT_NULL));
                } // else: nothing much happening in the first iteration, because LOGGER is still unknown!
            } else if ("1".equals(d.statementId())) {
                Assert.assertTrue(d.variableInfo().isRead());
                Assert.assertEquals("1" + VariableInfoContainer.Level.EVALUATION, d.variableInfo().getReadId());
                int expectModified = d.iteration() == 0 ? Level.DELAY : Level.FALSE;
                Assert.assertEquals(expectModified, d.getProperty(VariableProperty.MODIFIED));

                // there is an explicit @NotNull on the first parameter of debug
                int expectNN = d.iteration() == 0 ? MultiLevel.DELAY : MultiLevel.EFFECTIVELY_NOT_NULL;
                Assert.assertEquals(expectNN, d.getProperty(VariableProperty.NOT_NULL));
            } else Assert.fail();
        }
        if (d.methodInfo().name.equals("idem3") && IDEM3_S.equals(d.variableName())) {
            // there is an explicit @NotNull on the first parameter of debug
            int expectNN = d.iteration() == 0 ? MultiLevel.DELAY : MultiLevel.EFFECTIVELY_NOT_NULL;

            Assert.assertEquals(expectNN, d.getPropertyOfCurrentValue(VariableProperty.NOT_NULL));
        }
    };

    StatementAnalyserVisitor statementAnalyserVisitor = d -> {
        if ("idem".equals(d.methodInfo().name) && "1".equals(d.statementId())) {
            boolean expect = d.iteration() > 0;
            Assert.assertEquals(expect,
                    d.statementAnalysis().methodAnalysis.methodLevelData().linksHaveBeenEstablished.isSet());
        }
        if ("idem2".equals(d.methodInfo().name) && "1".equals(d.statementId())) {
            // false because static method
            int expectMC = d.iteration() == 0 ? Level.DELAY : Level.FALSE;
            Assert.assertEquals(expectMC, d.getThisAsVariable().getProperty(VariableProperty.METHOD_CALLED));
        }
        if ("idem3".equals(d.methodInfo().name) && "1.0.0".equals(d.statementId()) && d.iteration() > 0) {
            Expression value = d.statementAnalysis().stateData.valueOfExpression.get();
            Assert.assertTrue(value instanceof PropertyWrapper);
            Expression valueInside = ((PropertyWrapper) value).expression;
            Assert.assertTrue(valueInside instanceof PropertyWrapper);
            Expression valueInside2 = ((PropertyWrapper) valueInside).expression;
            Assert.assertTrue(valueInside2 instanceof VariableExpression);
            // check that isInstanceOf bypasses the wrappers
            Assert.assertTrue(value.isInstanceOf(VariableExpression.class));
            Assert.assertEquals(MultiLevel.EFFECTIVELY_NOT_NULL, d.getProperty(value, VariableProperty.NOT_NULL));
        }
    };

    MethodAnalyserVisitor methodAnalyserVisitor = d -> {
        MethodAnalysis methodAnalysis = d.methodAnalysis();
        if (d.iteration() > 0) {
            if ("idem".equals(d.methodInfo().name)) {
                VariableInfo vi = d.getReturnAsVariable();
                Assert.assertFalse(vi.hasProperty(VariableProperty.MODIFIED));

                Assert.assertEquals(Level.FALSE, methodAnalysis.getProperty(VariableProperty.MODIFIED));
                Assert.assertEquals(Level.TRUE, methodAnalysis.getProperty(VariableProperty.IDENTITY));
                Assert.assertEquals("s", d.methodAnalysis().getSingleReturnValue().toString());
            }

            if ("idem2".equals(d.methodInfo().name)) {
                Assert.assertEquals(Level.FALSE, methodAnalysis.getProperty(VariableProperty.MODIFIED));
                Assert.assertEquals(Level.TRUE, methodAnalysis.getProperty(VariableProperty.IDENTITY));
                Assert.assertEquals("s/*@Immutable,@NotNull*/", d.methodAnalysis().getSingleReturnValue().toString());
            }

            if ("idem3".equals(d.methodInfo().name)) {
                Assert.assertEquals(Level.TRUE, methodAnalysis.getProperty(VariableProperty.IDENTITY));
                Assert.assertEquals(Level.FALSE, methodAnalysis.getProperty(VariableProperty.MODIFIED));

                VariableInfo vi = d.getReturnAsVariable();
                Assert.assertEquals(MultiLevel.EFFECTIVELY_NOT_NULL, vi.getProperty(VariableProperty.NOT_NULL));

                Assert.assertEquals("s/*@Immutable,@NotNull*//*@Immutable,@NotNull*/",
                        d.methodAnalysis().getSingleReturnValue().toString());

                // combining both, we obtain:
                Assert.assertEquals(MultiLevel.EFFECTIVELY_NOT_NULL, methodAnalysis.getProperty(VariableProperty.NOT_NULL));
            }
            if ("idem4".equals(d.methodInfo().name)) {
                Assert.assertEquals(Level.FALSE, methodAnalysis.getProperty(VariableProperty.MODIFIED));
                Assert.assertEquals(Level.TRUE, methodAnalysis.getProperty(VariableProperty.IDENTITY));
            }
        }
    };

    EvaluationResultVisitor evaluationResultVisitor = d -> {
        if ("idem4".equals(d.methodInfo().name) && "1".equals(d.statementId())) {
            // double property wrapper
            String expect = d.iteration() == 0 ? EmptyExpression.NO_VALUE.toString() : "s/*@Immutable,@NotNull*//*@Immutable,@NotNull*/";
            Assert.assertEquals(expect, d.evaluationResult().value().toString());
        }
    };

    FieldAnalyserVisitor fieldAnalyserVisitor = d -> {
        if ("LOGGER".equals(d.fieldInfo().name) && "Identity_0".equals(d.fieldInfo().owner.simpleName)) {
            //if (d.iteration() == 0) {
                Assert.assertTrue( d.fieldAnalysis().getLinkedVariables().isEmpty());
           // } else {
           //     Assert.assertTrue(d.fieldAnalysis().getLinkedVariables().isEmpty());
           // }
        }
    };

    TypeMapVisitor typeMapVisitor = typeMap -> {
        TypeInfo logger = typeMap.get(Logger.class);
        MethodInfo debug = logger.typeInspection.get().methodStream(TypeInspection.Methods.THIS_TYPE_ONLY)
                .filter(m -> "org.slf4j.Logger.debug(String,Object...)".equals(m.fullyQualifiedName))
                .findFirst().orElseThrow();
        Assert.assertEquals(Level.FALSE, debug.methodAnalysis.get().getProperty(VariableProperty.MODIFIED));

        MethodInfo debug1 = logger.findUniqueMethod("debug", 1);
        Assert.assertEquals(Level.FALSE, debug1.methodAnalysis.get().getProperty(VariableProperty.MODIFIED));
        Assert.assertEquals(MultiLevel.EFFECTIVELY_NOT_NULL, debug1.methodInspection.get().getParameters().get(0)
                .parameterAnalysis.get().getProperty(VariableProperty.NOT_NULL));
    };

    @Test
    public void test() throws IOException {
        testClass("Identity_0", 0, 0, new DebugConfiguration.Builder()
                        .addStatementAnalyserVisitor(statementAnalyserVisitor)
                        .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                        .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                        .addTypeMapVisitor(typeMapVisitor)
                        .addEvaluationResultVisitor(evaluationResultVisitor)
                        .addAfterFieldAnalyserVisitor(fieldAnalyserVisitor)
                        .build(),
                new AnalyserConfiguration.Builder().setSkipTransformations(true).build());
    }

}
