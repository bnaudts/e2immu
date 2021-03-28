
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

import org.e2immu.analyser.analyser.VariableProperty;
import org.e2immu.analyser.config.DebugConfiguration;
import org.e2immu.analyser.model.Level;
import org.e2immu.analyser.model.MultiLevel;
import org.e2immu.analyser.model.ParameterAnalysis;
import org.e2immu.analyser.model.ParameterInfo;
import org.e2immu.analyser.model.variable.FieldReference;
import org.e2immu.analyser.visitor.*;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;

public class Test_37_EventuallyE2Immutable extends CommonTestRunner {

    public Test_37_EventuallyE2Immutable() {
        super(true);
    }

    @Test
    public void test_0() throws IOException {

        StatementAnalyserVisitor statementAnalyserVisitor = d -> {
            if ("setT".equals(d.methodInfo().name)) {
                if ("0.0.0".equals(d.statementId()) || "0".equals(d.statementId())) {
                    assertTrue(d.statementAnalysis().stateData.precondition.get().isEmpty());
                }
                if ("1.0.0".equals(d.statementId())) {
                    if (d.iteration() == 0) {
                        assertNull(d.statementAnalysis().stateData.precondition.get());
                    } else {
                        assertEquals("null==t", d.statementAnalysis().stateData.precondition
                                .get().expression().toString());
                        assertEquals("null==t", d.statementAnalysis().methodLevelData
                                .combinedPrecondition.get().expression().toString());
                    }
                }
                if ("1".equals(d.statementId())) {
                    if (d.iteration() == 0) {
                        assertNull(d.statementAnalysis().stateData.precondition.get());
                    } else {
                        assertTrue(d.statementAnalysis().stateData.precondition.get().isEmpty());
                        assertEquals("null==t", d.statementAnalysis().methodLevelData
                                .combinedPrecondition.get().expression().toString());
                    }
                }
            }
            if ("set2".equals(d.methodInfo().name)) {
                String expectPrecondition = switch (d.iteration()) {
                    case 0 -> "<precondition>";
                    case 1 -> "null==<f:t>";
                    default -> "null==t";
                };
                assertEquals(expectPrecondition, d.statementAnalysis()
                        .stateData.precondition.get().expression().toString());
            }
        };

        TypeAnalyserVisitor typeAnalyserVisitor = d -> {
            if ("EventuallyE2Immutable_0".equals(d.typeInfo().simpleName)) {
                String expect = d.iteration() <= 1 ? "{}" : "{t=null==t}";
                assertEquals(expect, d.typeAnalysis().getApprovedPreconditionsE2().toString());
                assertEquals(d.iteration() > 1, d.typeAnalysis().approvedPreconditionsIsFrozen(true));

                int expectImmutable = d.iteration() <= 1 ? Level.DELAY : MultiLevel.EVENTUALLY_E2IMMUTABLE;
                assertEquals(expectImmutable, d.typeAnalysis().getProperty(VariableProperty.IMMUTABLE));
            }
        };


        MethodAnalyserVisitor methodAnalyserVisitor = d -> {
            if ("getT".equals(d.methodInfo().name)) {
                if (d.iteration() == 0) {
                    assertNull(d.methodAnalysis().getSingleReturnValue());
                } else {
                    assertEquals("t", d.methodAnalysis().getSingleReturnValue().toString());
                }
            }
        };

        testClass("EventuallyE2Immutable_0", 0, 0, new DebugConfiguration.Builder()
                .addStatementAnalyserVisitor(statementAnalyserVisitor)
                .addAfterTypePropertyComputationsVisitor(typeAnalyserVisitor)
                .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                .build());
    }

    @Test
    public void test_1() throws IOException {

        TypeAnalyserVisitor typeAnalyserVisitor = d -> {
            if ("EventuallyE2Immutable_1".equals(d.typeInfo().simpleName)) {
                String expect = d.iteration() <= 1 ? "{}" : "{t=null==t}";
                assertEquals(expect, d.typeAnalysis().getApprovedPreconditionsE1().toString());
                assertEquals("{}", d.typeAnalysis().getApprovedPreconditionsE2().toString());
                assertEquals(d.iteration() > 1, d.typeAnalysis().approvedPreconditionsIsFrozen(true));

                int expectImmutable = d.iteration() <= 1 ? Level.DELAY : MultiLevel.EVENTUALLY_E2IMMUTABLE;
                assertEquals(expectImmutable, d.typeAnalysis().getProperty(VariableProperty.IMMUTABLE));
            }
        };

        StatementAnalyserVisitor statementAnalyserVisitor = d -> {
            if ("error".equals(d.methodInfo().name)) {
                String expectPrecondition = switch (d.iteration()) {
                    case 0 -> "<precondition>&&<precondition>";
                    case 1 -> "null==<f:t>&&null!=<f:t>";
                    default -> "false";
                };
                assertEquals(expectPrecondition, d.statementAnalysis()
                        .stateData.precondition.get().expression().toString());
                assertEquals(d.iteration() <= 1, d.statementAnalysis().stateData.precondition.isVariable());

                assertEquals(d.iteration() <= 1, d.statementAnalysis().methodLevelData
                        .combinedPrecondition.get().isEmpty());
                assertEquals(d.iteration() <= 1, d.statementAnalysis().methodLevelData
                        .combinedPrecondition.isVariable());
            }
        };

        MethodAnalyserVisitor methodAnalyserVisitor = d -> {
            if ("error".equals(d.methodInfo().name)) {
                assertNull(d.methodAnalysis().getPreconditionForEventual());
            }
        };

        testClass("EventuallyE2Immutable_1", 1, 0, new DebugConfiguration.Builder()
                //  .addAfterTypePropertyComputationsVisitor(typeAnalyserVisitor)
                //   .addStatementAnalyserVisitor(statementAnalyserVisitor)
                //  .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                .build());
    }


    @Test
    public void test_2() throws IOException {
        MethodAnalyserVisitor methodAnalyserVisitor = d -> {
            if ("setT".equals(d.methodInfo().name)) {
                assertEquals(Level.TRUE, d.methodAnalysis().getProperty(VariableProperty.MODIFIED_METHOD));
                ParameterAnalysis p0 = d.parameterAnalyses().get(0);
                int expectMv = d.iteration() <= 1 ? Level.DELAY : Level.FALSE;
                assertEquals(expectMv, p0.getProperty(VariableProperty.MODIFIED_VARIABLE));
            }

            if ("copyInto".equals(d.methodInfo().name)) {
                int expectMm = d.iteration() <= 1 ? Level.DELAY : Level.FALSE;
                assertEquals(expectMm, d.methodAnalysis().getProperty(VariableProperty.MODIFIED_METHOD));
                ParameterAnalysis p0 = d.parameterAnalyses().get(0);
                int expectMv = d.iteration() <= 2 ? Level.DELAY : Level.TRUE;
                assertEquals(expectMv, p0.getProperty(VariableProperty.CONTEXT_MODIFIED));
                assertEquals(expectMv, p0.getProperty(VariableProperty.MODIFIED_VARIABLE));
            }
        };

        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if ("copyInto".equals(d.methodInfo().name) && "0".equals(d.statementId())) {
                if (d.variable() instanceof ParameterInfo other && "other".equals(other.name)) {
                    int expectCm = d.iteration() <= 1 ? Level.DELAY : Level.TRUE;
                    assertEquals(expectCm, d.getProperty(VariableProperty.CONTEXT_MODIFIED));
                }
            }
        };

        testClass("EventuallyE2Immutable_2", 0, 0, new DebugConfiguration.Builder()
                .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .build());
    }

    /*
    untranslated precondition and condition manager combine to detect errors
     */

    @Test
    public void test_3() throws IOException {
        String TYPE = "org.e2immu.analyser.testexample.EventuallyE2Immutable_3";
        String OTHER_T_0 = TYPE + ".t#" + TYPE + ".error1(" + TYPE + "<T>):0:other$0";
        StatementAnalyserVisitor statementAnalyserVisitor = d -> {
            if ("error1".equals(d.methodInfo().name)) {
                if ("0.0.0".equals(d.statementId())) {
                    String expectCondition = switch (d.iteration()) {
                        case 0 -> "!<m:isSet>";
                        case 1 -> "null==<f:t>";
                        default -> "null==" + OTHER_T_0;
                    };
                    assertEquals(expectCondition, d.condition().toString());
                }
            }
        };
        testClass("EventuallyE2Immutable_3", 2, 0, new DebugConfiguration.Builder()
                .addStatementAnalyserVisitor(statementAnalyserVisitor)
                .build());
    }


    /*
    object flow helps to find the errors
     */

    @Test
    public void test_4() throws IOException {
        testClass("EventuallyE2Immutable_4", 2, 0, new DebugConfiguration.Builder()
                .build());
    }

    @Test
    public void test_5() throws IOException {
        testClass("EventuallyE2Immutable_5", 0, 0, new DebugConfiguration.Builder()
                .build());
    }

    /*
    in this example we combine the eventual work, based on preconditions,
    with the companion work/instance state changes

    An additional complication is that the precondition discovered is !data.isEmpty(),
    while the instance state says that this.size()>=data.size().
    We should be able to conclude from this that after statement 2 in initialise, !set.isEmpty()
     */
    @Test
    public void test_6() throws IOException {
        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if ("initialize".equals(d.methodInfo().name)) {
                if (d.variable() instanceof FieldReference fr && "set".equals(fr.fieldInfo.name)) {
                    if ("2".equals(d.statementId())) {
                        String expectValue = d.iteration() == 0 ? "<f:set>" :
                                "instance type HashSet<T>/*this.size()>=data.size()*/";
                        assertEquals(expectValue, d.currentValue().toString());
                    }
                }
            }
        };

        StatementAnalyserVisitor statementAnalyserVisitor = d -> {
            if ("initialize".equals(d.methodInfo().name)) {
                if ("1".equals(d.statementId())) {
                    if (d.iteration() == 0) {
                        assertTrue(d.statementAnalysis().stateData.precondition.isVariable());
                    } else {
                        assertTrue(d.statementAnalysis().stateData.precondition.get().isEmpty());
                        assertEquals("!data.isEmpty()&&set.isEmpty()",
                                d.statementAnalysis().methodLevelData.combinedPrecondition.get().expression().toString());
                    }
                }
            }
        };

        MethodAnalyserVisitor methodAnalyserVisitor = d -> {
            if ("stream".equals(d.methodInfo().name)) {
                if (d.iteration() <= 1) {
                    assertNull(d.methodAnalysis().getPreconditionForEventual());
                } else {
                    assertEquals("!set.isEmpty()",
                            d.methodAnalysis().getPreconditionForEventual().expression().toString());
                }
            }
            if ("initialize".equals(d.methodInfo().name)) {
                if (d.iteration() <= 1) {
                    // need to wait for implicitly immutable types
                    assertNull(d.methodAnalysis().getPreconditionForEventual());
                } else {
                    assertEquals("set.isEmpty()",
                            d.methodAnalysis().getPreconditionForEventual().expression().toString());
                }
            }
        };

        FieldAnalyserVisitor fieldAnalyserVisitor = d -> {
            if ("set".equals(d.fieldInfo().name)) {
                if (d.iteration() == 0) {
                    assertNull(d.fieldAnalysis().isOfImplicitlyImmutableDataType());
                } else {
                    assertFalse(d.fieldAnalysis().isOfImplicitlyImmutableDataType());
                }
            }
        };

        testClass("EventuallyE2Immutable_6", 0, 0, new DebugConfiguration.Builder()
                .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                .addAfterFieldAnalyserVisitor(fieldAnalyserVisitor)
                .addStatementAnalyserVisitor(statementAnalyserVisitor)
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .build());
    }

    /*
    The slightly less complicated version of test 6: Here we only have size() not isEmpty.

    This tests uses size()>0 instead of size() !=0, because that expression is not (yet) recognized
    by Filter.individualFieldClause. Similarly, for consistency we use <= 0 instead of != 0 in the precondition.

    Dedicated code in MethodCallIncompatibleWithPrecondition detects the modification wrt the precondition.
     */
    @Test
    public void test_7() throws IOException {

        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if ("initialize".equals(d.methodInfo().name)) {
                if (d.variable() instanceof FieldReference fr && "set".equals(fr.fieldInfo.name)) {
                    if ("2".equals(d.statementId())) {
                        String expectValue = d.iteration() == 0 ? "<f:set>" :
                                "instance type HashSet<T>/*this.size()>=data.size()*/";
                        assertEquals(expectValue, d.currentValue().toString());
                    }
                }
            }
        };

        StatementAnalyserVisitor statementAnalyserVisitor = d -> {
            if ("initialize".equals(d.methodInfo().name)) {
                if ("1".equals(d.statementId())) {
                    if (d.iteration() == 0) {
                        assertTrue(d.statementAnalysis().stateData.precondition.isVariable());
                    } else {
                        assertTrue(d.statementAnalysis().stateData.precondition.get().isEmpty());
                        assertEquals("data.size()>=1&&set.size()<=0",
                                d.statementAnalysis().methodLevelData.combinedPrecondition.get().expression().toString());
                    }
                }
            }
        };

        MethodAnalyserVisitor methodAnalyserVisitor = d -> {
            if ("stream".equals(d.methodInfo().name)) {
                if (d.iteration() <= 1) {
                    assertNull(d.methodAnalysis().getPreconditionForEventual());
                } else {
                    assertEquals("set.size()>=1",
                            d.methodAnalysis().getPreconditionForEventual().expression().toString());
                }
            }
            if ("initialize".equals(d.methodInfo().name)) {
                if (d.iteration() <= 1) {
                    // need to wait for implicitly immutable types
                    assertNull(d.methodAnalysis().getPreconditionForEventual());
                } else {
                    assertEquals("set.size()<=0",
                            d.methodAnalysis().getPreconditionForEventual().expression().toString());
                }
            }
        };

        testClass("EventuallyE2Immutable_7", 0, 0, new DebugConfiguration.Builder()
                .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                .addStatementAnalyserVisitor(statementAnalyserVisitor)
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .build());
    }

    @Test
    public void test_8() throws IOException {

        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if ("initialize".equals(d.methodInfo().name)) {
                if (d.variable() instanceof FieldReference fr && "set".equals(fr.fieldInfo.name)) {
                    if ("2".equals(d.statementId())) {
                        String expectValue = d.iteration() == 0 ? "<f:set>" :
                                "instance type HashSet<T>/*this.size()>=data.size()*/";
                        assertEquals(expectValue, d.currentValue().toString());
                    }
                }
            }
        };

        StatementAnalyserVisitor statementAnalyserVisitor = d -> {
            if ("initialize".equals(d.methodInfo().name)) {
                if ("1".equals(d.statementId())) {
                    if (d.iteration() == 0) {
                        assertTrue(d.statementAnalysis().stateData.precondition.isVariable());
                    } else {
                        assertTrue(d.statementAnalysis().stateData.precondition.get().isEmpty());
                        assertEquals("0!=data.size()&&0==set.size()",
                                d.statementAnalysis().methodLevelData.combinedPrecondition.get().expression().toString());
                    }
                }
            }
        };

        MethodAnalyserVisitor methodAnalyserVisitor = d -> {
            if ("stream".equals(d.methodInfo().name)) {
                if (d.iteration() <= 1) {
                    assertNull(d.methodAnalysis().getPreconditionForEventual());
                } else {
                    assertEquals("0!=set.size()",
                            d.methodAnalysis().getPreconditionForEventual().expression().toString());
                }
            }
            if ("initialize".equals(d.methodInfo().name)) {
                if (d.iteration() <= 1) {
                    // need to wait for implicitly immutable types
                    assertNull(d.methodAnalysis().getPreconditionForEventual());
                } else {
                    assertEquals("0==set.size()",
                            d.methodAnalysis().getPreconditionForEventual().expression().toString());
                }
            }
        };

        testClass("EventuallyE2Immutable_8", 0, 0, new DebugConfiguration.Builder()
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .addStatementAnalyserVisitor(statementAnalyserVisitor)
                .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                .build());
    }

}