package org.e2immu.analyser.parser;

import org.e2immu.analyser.analyser.TransferValue;
import org.e2immu.analyser.analyser.VariableProperty;
import org.e2immu.analyser.config.*;
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.model.abstractvalue.FinalFieldValue;
import org.e2immu.analyser.model.abstractvalue.MethodValue;
import org.junit.Assert;
import org.junit.Test;

import java.io.IOException;
import java.util.Set;

public class TestSimpleSizeChecks extends CommonTestRunner {
    public TestSimpleSizeChecks() {
        super(true);
    }

    private static final int SIZE_EQUALS_2 = Level.encodeSizeEquals(2);

    StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
        if ("method1bis".equals(d.methodInfo.name) && "0".equals(d.statementId) && "set".equals(d.variableName)) {
            Assert.assertTrue(d.currentValue instanceof MethodValue);
            Assert.assertEquals(MultiLevel.EFFECTIVELY_E2IMMUTABLE, d.currentValue.getPropertyOutsideContext(VariableProperty.IMMUTABLE));
            Assert.assertEquals(Level.TRUE, d.currentValue.getPropertyOutsideContext(VariableProperty.CONTAINER));
            Assert.assertTrue(d.variable instanceof LocalVariableReference);

            // properties are on the value; in the map is the value of the type before the assignment
            // at the moment, the Set interface is not E1Immutable
            Assert.assertEquals(MultiLevel.MUTABLE, (int) d.properties.get(VariableProperty.IMMUTABLE));
            Assert.assertNull(d.properties.get(VariableProperty.CONTAINER));
        }
        if ("method2".equals(d.methodInfo.name) && "0".equals(d.statementId) && "SimpleSizeChecks.this.intSet".equals(d.variableName)) {
            if (d.iteration > 0) {
                Assert.assertEquals("this.intSet", d.currentValue.toString());
                Assert.assertTrue(d.currentValue instanceof FinalFieldValue);

                if (d.iteration > 1) {
                    Assert.assertEquals(SIZE_EQUALS_2, d.currentValue.getPropertyOutsideContext(VariableProperty.SIZE));
                }
            }
        }
    };

    StatementAnalyserVisitor statementAnalyserVisitor = d -> {
        if ("method1".equals(d.methodInfo.name) && "1".equals(d.statementId)) {
            Assert.assertTrue(d.statementAnalysis.errorFlags.errorValue.isSet());
        }
        if ("method1bis".equals(d.methodInfo.name) && "1".equals(d.statementId)) {
            Assert.assertTrue(d.statementAnalysis.errorFlags.errorValue.isSet());
        }
        if ("method1bis".equals(d.methodInfo.name) && "2".equals(d.statementId)) {
            Assert.assertTrue(d.statementAnalysis.errorFlags.errorValue.isSet());
        }
    };

    private static final int SIZE_EQUALS_1 = Level.encodeSizeEquals(1);

    MethodAnalyserVisitor methodAnalyserVisitor = d -> {
        MethodAnalysis methodAnalysis = d.methodAnalysis();
        if ("method1".equals(d.methodInfo().name)) {
            TransferValue tv = methodAnalysis.methodLevelData().returnStatementSummaries.get("2");
            Assert.assertNotNull(tv);
            Assert.assertEquals(MultiLevel.EFFECTIVELY_CONTENT_NOT_NULL, tv.getProperty(VariableProperty.NOT_NULL));
            Assert.assertEquals(SIZE_EQUALS_1, tv.properties.get(VariableProperty.SIZE)); // (1)
            Assert.assertEquals(SIZE_EQUALS_1, methodAnalysis.getProperty(VariableProperty.SIZE));

            // immutable gets copied into transfer value, but container does not (not a dynamic property)
            Assert.assertEquals(MultiLevel.EFFECTIVELY_E2IMMUTABLE, tv.getProperty(VariableProperty.IMMUTABLE));
            Assert.assertFalse(tv.properties.isSet(VariableProperty.CONTAINER));

            // then from the transfer value it goes onto the method
            Assert.assertEquals(MultiLevel.EFFECTIVELY_E2IMMUTABLE, methodAnalysis.getProperty(VariableProperty.IMMUTABLE));
            // the container property should be there because Set is a container
            Assert.assertEquals(Level.TRUE, methodAnalysis.getProperty(VariableProperty.CONTAINER));
        }
    };

    FieldAnalyserVisitor fieldAnalyserVisitor = d -> {
        if (d.iteration() > 0 && d.fieldInfo().name.equals("intSet")) {
            Assert.assertEquals(SIZE_EQUALS_2, d.fieldAnalysis().getProperty(VariableProperty.SIZE));
        }
    };

    TypeContextVisitor typeContextVisitor = typeContext -> {
        TypeInfo set = typeContext.getFullyQualified(Set.class);
        MethodInfo setOf = set.findUniqueMethod("of", 0);
        Assert.assertEquals(MultiLevel.EFFECTIVELY_E2IMMUTABLE, setOf.methodAnalysis.get().getProperty(VariableProperty.IMMUTABLE));
        Assert.assertEquals(Level.TRUE, setOf.methodAnalysis.get().getProperty(VariableProperty.CONTAINER));
    };

    @Test
    public void test() throws IOException {
        testClass("SimpleSizeChecks", 3, 0, new DebugConfiguration.Builder()
                .addTypeContextVisitor(typeContextVisitor)
                .addStatementAnalyserVisitor(statementAnalyserVisitor)
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                .addAfterFieldAnalyserVisitor(fieldAnalyserVisitor)
                .build());
    }

}
