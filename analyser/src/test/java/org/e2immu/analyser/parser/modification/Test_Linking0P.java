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

import org.e2immu.analyser.analyser.ChangeData;
import org.e2immu.analyser.analyser.HiddenContentSelector;
import org.e2immu.analyser.analyser.LinkedVariables;
import org.e2immu.analyser.analyser.Property;
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

import static org.e2immu.analyser.parser.VisitorTestSupport.IterationInfo.it;
import static org.junit.jupiter.api.Assertions.*;

public class Test_Linking0P extends CommonTestRunner {

    public Test_Linking0P() {
        super(true);
    }

    @Test
    public void test_0() throws IOException {

        EvaluationResultVisitor evaluationResultVisitor = d -> {
            LinkedVariables lvsExpression = d.evaluationResult().linkedVariablesOfExpression();
            if ("reverse".equals(d.methodInfo().name)) {
                assertLinked(d, lvsExpression, it(0, 1, "pair.f:-1,pair.g:-1,pair:-1"),
                        it(2, "pair.f:4,pair.g:4"));
                assertSingleLv(d, lvsExpression, 2, 0, "1-4-*");
                assertSingleLv(d, lvsExpression, 2, 1, "0-4-*");

                ChangeData cdf = d.evaluationResult().findChangeData("f");
                assertNotNull(cdf);
                assertLinked(d, cdf.linkedVariables(), it(0, 1, "pair:-1"),
                        it(2, "pair:4"));
                assertSingleLv(d, cdf.linkedVariables(), 2, 0, "*-4-0");
                ChangeData cdg = d.evaluationResult().findChangeData("g");
                assertNotNull(cdg);
                assertLinked(d, cdg.linkedVariables(), it(0, 1, "pair:-1"),
                        it(2, "pair:4"));
                assertSingleLv(d, cdg.linkedVariables(), 2, 0, "*-4-1");
            }
            if ("reverse4".equals(d.methodInfo().name)) {
                String expected = d.iteration() < 3 ? "<new:R<Y,X>>" : "new R<>(new Pair<>(r.pair.g,r.pair.f))";
                assertEquals(expected, d.evaluationResult().value().toString());
                assertLinked(d, lvsExpression, it(0, 2, "r.pair.f:-1,r.pair.g:-1,r.pair:-1,r:-1"),
                        it(3, "r.pair.f:4,r.pair.g:4"));
                // FIXME here is reverse4 issue
                //  problem is due to absence of intermediary variable, see reverse4b
                // assertSingleLv(d, lvsExpression, 3, 0, "1-4-*");
                //  assertSingleLv(d, lvsExpression, 3, 1, "0-4-*");
            }
            if ("reverse4b".equals(d.methodInfo().name)) {
                if ("0".equals(d.statementId())) {
                    String expected = d.iteration() < 3 ? "<new:Pair<Y,X>>" : "new Pair<>(r.pair.g,r.pair.f)";
                    assertEquals(expected, d.evaluationResult().value().toString());
                    assertLinked(d, lvsExpression, it(0, 2, "r.pair.f:-1,r.pair.g:-1,r.pair:-1,r:-1,yxPair:0"),
                            it(3, "r.pair.f:4,r.pair.g:4,yxPair:0"));
                    assertSingleLv(d, lvsExpression, 3, 0, "1-4-*");
                    assertSingleLv(d, lvsExpression, 3, 1, "0-4-*");
                }
            }
        };

        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if ("create0".equals(d.methodInfo().name)) {
                if (d.variable() instanceof ReturnVariable) {
                    assertCurrentValue(d, 2, "new Pair<>(x,y)");
                    assertLinked(d, it(0, 1, "x:-1,y:-1"), it(0, "x:4,y:4"));
                    assertSingleLv(d, 2, 0, "0-4-*");
                    assertSingleLv(d, 2, 1, "1-4-*");
                }
            }
            if ("create1".equals(d.methodInfo().name)) {
                if (d.variable() instanceof ReturnVariable) {
                    assertCurrentValue(d, 2, "new Pair<>(x,m)");
                    assertLinked(d, it(0, 1, "m:-1,x:-1"), it(0, "m:4,x:4"));
                    assertSingleLv(d, 2, 0, "1M-4-*M");
                    assertSingleLv(d, 2, 1, "0-4-*");
                }
            }
            if ("create2".equals(d.methodInfo().name)) {
                if (d.variable() instanceof ReturnVariable && "1".equals(d.statementId())) {
                    assertCurrentValue(d, 2, "new Pair<>(x,m)");
                    assertLinked(d, it(0, 1, "m:-1,p:0,x:-1"), it(0, "m:4,p:0,x:4"));
                    assertSingleLv(d, 2, 0, "1M-4-*M");
                    assertSingleLv(d, 2, 2, "0-4-*");
                }
            }
            if ("create3".equals(d.methodInfo().name)) {
                if (d.variable() instanceof ReturnVariable && "1".equals(d.statementId())) {
                    assertCurrentValue(d, 2, "new Pair<>(n,m)");
                    assertLinked(d, it(0, 1, "m:-1,n:-1,p:0"), it(0, "m:4,n:4,p:0"));
                    assertSingleLv(d, 2, 0, "1M-4-*M");
                    assertSingleLv(d, 2, 1, "0M-4-*M");
                }
            }
            if ("create4".equals(d.methodInfo().name)) {
                if (d.variable() instanceof ReturnVariable && "1".equals(d.statementId())) {
                    assertCurrentValue(d, 2, "new Pair<>(i,m)");
                    assertLinked(d, it(0, 1, "m:-1,p:0"), it(0, "m:4,p:0"));
                    assertSingleLv(d, 2, 0, "1M-4-*M");
                }
            }
            if ("reverse".equals(d.methodInfo().name)) {
                if (d.variable() instanceof ParameterInfo pi && "pair".equals(pi.name)) {
                    assertCurrentValue(d, 2, "nullable instance type Pair<X,Y>/*@Identity*/");
                    assertLinked(d, it(0, 1, "pair.f:-1,pair.g:-1"),
                            it(2, "pair.f:4,pair.g:4"));
                    assertSingleLv(d, 2, 0, "0-4-*");
                    assertSingleLv(d, 2, 1, "1-4-*");
                }
                if (d.variable() instanceof FieldReference fr && "f".equals(fr.fieldInfo().name)) {
                    assertCurrentValue(d, 2, "nullable instance type F");
                    assertLinked(d, it(0, 1, "pair.g:-1,pair:-1"), it(2, "pair:4"));
                    assertSingleLv(d, 2, 0, "*-4-0");
                }
                if (d.variable() instanceof FieldReference fr && "g".equals(fr.fieldInfo().name)) {
                    assertCurrentValue(d, 2, "nullable instance type G");
                    assertLinked(d, it(0, 1, "pair.f:-1,pair:-1"), it(2, "pair:4"));
                    assertSingleLv(d, 2, 0, "*-4-1");
                }
                if (d.variable() instanceof ReturnVariable) {
                    assertCurrentValue(d, 2, "new Pair<>(pair.g,pair.f)");
                    assertLinked(d, it(0, 1, "pair.f:-1,pair.g:-1,pair:-1"),
                            it(2, "pair.f:4,pair.g:4,pair:4"));
                    assertSingleLv(d, 2, 0, "1-4-*");
                    assertSingleLv(d, 2, 1, "0-4-*");
                    assertSingleLv(d, 2, 2, "0,1-4-1,0");
                }
            }
            if ("reverse2".equals(d.methodInfo().name)) {
                if (d.variable() instanceof ReturnVariable) {
                    assertCurrentValue(d, 2, "new Pair<>(pair.g(),pair.f())");
                    //     assertLinked(d, it(0, 1, "pair:-1"),
                    // FIXME empty is not the solution!!
                    //           it(2, "pair.f:4,pair.g:4,pair:4"));
                }
            }
            if ("reverse3".equals(d.methodInfo().name)) {
                if (d.variable() instanceof FieldReference fr && "f".equals(fr.fieldInfo().name)) {
                    assertCurrentValue(d, 3, "nullable instance type F");
                    assertLinked(d, it(0, 1, "r.pair.g:-1,r.pair:-1,r:-1"), it(2, "r.pair:4,r:4"));
                    assertSingleLv(d, 2, 0, "*-4-0");
                    assertSingleLv(d, 2, 1, "*-4-0");
                }
                if (d.variable() instanceof FieldReference fr && "pair".equals(fr.fieldInfo().name)) {
                    assertCurrentValue(d, 3, "instance type Pair<X,Y>");
                    assertLinked(d, it(0, 1, "r.pair.f:-1,r.pair.g:-1,r:-1"),
                            it(2, "r.pair.f:4,r.pair.g:4,r:4"));
                    assertSingleLv(d, 2, 0, "0-4-*");
                    assertSingleLv(d, 2, 1, "1-4-*");
                    assertSingleLv(d, 2, 2, "0,1-4-0,1");
                }
                if (d.variable() instanceof ReturnVariable) {
                    assertCurrentValue(d, 3, "new Pair<>(r.pair.g,r.pair.f)");
                    assertLinked(d, it(0, 2, "r.pair.f:-1,r.pair.g:-1,r.pair:-1,r:-1"),
                            it(3, "r.pair.f:4,r.pair.g:4,r.pair:4,r:4"));
                    assertSingleLv(d, 3, 0, "1-4-*");
                    assertSingleLv(d, 3, 1, "0-4-*");
                    assertSingleLv(d, 3, 2, "0,1-4-1,0");
                    assertSingleLv(d, 3, 3, "0,1-4-1,0");
                }
            }
            if ("reverse4".equals(d.methodInfo().name)) {
                if (d.variable() instanceof FieldReference fr && "f".equals(fr.fieldInfo().name)) {
                    assertCurrentValue(d, 3, "nullable instance type F");
                    assertLinked(d, it(0, 1, "r.pair.g:-1,r.pair:-1,r:-1"), it(2, "r.pair:4,r:4"));
                    assertSingleLv(d, 2, 0, "*-4-0");
                    assertSingleLv(d, 2, 1, "*-4-0");
                }
                if (d.variable() instanceof ParameterInfo pi && "r".equals(pi.name)) {
                    assertCurrentValue(d, 3, "nullable instance type R<X,Y>/*@Identity*/");
                    assertLinked(d, it(0, 1, "r.pair.f:-1,r.pair.g:-1,r.pair:-1"),
                            it(2, "r.pair.f:4,r.pair.g:4,r.pair:4"));
                    assertSingleLv(d, 2, 0, "0-4-*");
                    assertSingleLv(d, 2, 1, "1-4-*");
                    assertSingleLv(d, 2, 2, "0,1-4-0,1");
                }
                if (d.variable() instanceof FieldReference fr && "pair".equals(fr.fieldInfo().name)) {
                    assertCurrentValue(d, 3, "instance type Pair<X,Y>");
                    assertLinked(d, it(0, 1, "r.pair.f:-1,r.pair.g:-1,r:-1"),
                            it(2, "r.pair.f:4,r.pair.g:4,r:4"));
                    assertSingleLv(d, 2, 0, "0-4-*");
                    assertSingleLv(d, 2, 1, "1-4-*");
                    assertSingleLv(d, 2, 2, "0,1-4-0,1");
                }
                if (d.variable() instanceof ReturnVariable) {
                    assertCurrentValue(d, 3, "new R<>(new Pair<>(r.pair.g,r.pair.f))");
                    //assertLinked(d, it(0, 2, "r.pair.f:-1,r.pair.g:-1,r.pair:-1,r:-1"),
                    //         it(3, "r.pair.f:4,r.pair.g:4,r.pair:4,r:4"));
                    // FIXME
                    // assertSingleLv(d, 3, 0, "1-4-*");
                    //  assertSingleLv(d, 3, 1, "0-4-*");
                }
            }
            if ("reverse4b".equals(d.methodInfo().name)) {
                if (d.variable() instanceof ReturnVariable && "1".equals(d.statementId())) {
                    assertCurrentValue(d, 3, "new R<>(new Pair<>(r.pair.g,r.pair.f))");
                    assertLinked(d, it(0, 2, "r.pair.f:-1,r.pair.g:-1,r.pair:-1,r:-1,yxPair:-1"),
                            it(3, "r.pair.f:4,r.pair.g:4,r.pair:4,r:4,yxPair:4"));
                    assertSingleLv(d, 3, 0, "1-4-*");
                    assertSingleLv(d, 3, 1, "0-4-*");
                    assertSingleLv(d, 3, 2, "0,1-4-1,0");
                    assertSingleLv(d, 3, 3, "0,1-4-1,0");
                    assertSingleLv(d, 3, 4, "0,1-4-0,1");
                }
            }
        };

        MethodAnalyserVisitor methodAnalyserVisitor = d -> {
            if ("f".equals(d.methodInfo().name)) {
                HiddenContentSelector hcs = d.methodAnalysis().getHiddenContentSelector();
                if (hcs instanceof HiddenContentSelector.All all) {
                    assertEquals(0, all.getHiddenContentIndex());
                } else fail();
            }
            if ("g".equals(d.methodInfo().name)) {
                HiddenContentSelector hcs = d.methodAnalysis().getHiddenContentSelector();
                if (hcs instanceof HiddenContentSelector.All all) {
                    assertEquals(1, all.getHiddenContentIndex());
                } else fail();
            }
            if ("pair".equals(d.methodInfo().name)) {
                assertEquals("R", d.methodInfo().typeInfo.simpleName);
                HiddenContentSelector hcs = d.methodAnalysis().getHiddenContentSelector();
                assertEquals("0,1", hcs.toString());
            }
            if ("setOncePair".equals(d.methodInfo().name)) {
                assertEquals("R1", d.methodInfo().typeInfo.simpleName);
                HiddenContentSelector hcs = d.methodAnalysis().getHiddenContentSelector();
                assertEquals("0=0.0,1=0.1", hcs.toString());
            }
        };

        TypeAnalyserVisitor typeAnalyserVisitor = d -> {
            if ("Pair".equals(d.typeInfo().simpleName)) {
                assertDv(d, 1, MultiLevel.EFFECTIVELY_IMMUTABLE_HC_DV, Property.IMMUTABLE);
            }
        };

        testClass("Linking_0P", 0, 0, new DebugConfiguration.Builder()
                .addEvaluationResultVisitor(evaluationResultVisitor)
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                .addAfterTypeAnalyserVisitor(typeAnalyserVisitor)
                .build());
    }
}
