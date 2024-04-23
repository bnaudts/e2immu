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

import org.e2immu.analyser.analyser.*;
import org.e2immu.analyser.config.DebugConfiguration;
import org.e2immu.analyser.model.MethodInfo;
import org.e2immu.analyser.model.MultiLevel;
import org.e2immu.analyser.model.ParameterInfo;
import org.e2immu.analyser.model.expression.MethodReference;
import org.e2immu.analyser.model.expression.PropertyWrapper;
import org.e2immu.analyser.model.variable.ReturnVariable;
import org.e2immu.analyser.parser.CommonTestRunner;
import org.e2immu.analyser.visitor.EvaluationResultVisitor;
import org.e2immu.analyser.visitor.MethodAnalyserVisitor;
import org.e2immu.analyser.visitor.StatementAnalyserVariableVisitor;
import org.e2immu.analyser.visitor.TypeAnalyserVisitor;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.e2immu.analyser.parser.VisitorTestSupport.IterationInfo.*;
import static org.junit.jupiter.api.Assertions.*;

public class Test_Linking1A extends CommonTestRunner {

    public Test_Linking1A() {
        super(true);
    }

    @Test
    public void test_0() throws IOException {
        EvaluationResultVisitor evaluationResultVisitor = d -> {
            switch (d.methodInfo().name) {
                case "s0m" -> {
                    if ("0".equals(d.statementId())) {
                        assertEquals("supplier::get", d.evaluationResult().value().toString());
                        assertLinked(d, d.evaluationResult().linkedVariablesOfExpression(), it(0, "s:0,supplier:4"));
                    }
                }
                case "s0l" -> {
                    if ("0".equals(d.statementId())) {
                        assertEquals("/*inline get*/supplier.get()/*{L supplier:4}*/",
                                d.evaluationResult().value().toString());
                        assertLinked(d, d.evaluationResult().linkedVariablesOfExpression(), it(0, "s:0,supplier:4"));
                    }
                    if ("1".equals(d.statementId())) {
                        assertEquals("supplier.get()", d.evaluationResult().value().toString());
                        assertLinked(d, d.evaluationResult().linkedVariablesOfExpression(), it(0, "s:4"));
                    }
                }
                case "p0l" -> {
                    if ("1".equals(d.statementId())) {
                        // p.test(x)
                        String expected = d.iteration() == 0 ? "<m:test>" : "predicate.test(x)";
                        assertEquals(expected, d.evaluationResult().value().toString());
                        assertLinked(d, d.evaluationResult().linkedVariablesOfExpression(),
                                it0("p:-1,x:-1"), it(1, ""));
                    }
                }
            }
        };
        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if (d.variable() instanceof ReturnVariable) {
                switch (d.methodInfo().name) {
                    case "s0" -> {
                        assertCurrentValue(d, 0, "supplier.get()");
                        assertLinked(d, it(0, "supplier:4"));
                        assertSingleLv(d, 2, 0, "*-4-0");
                    }
                    case "s0l" -> {
                        if ("1".equals(d.statementId())) {
                            assertCurrentValue(d, 0, "supplier.get()");
                            assertLinked(d, it(0, "s:4,supplier:4"));
                            assertSingleLv(d, 0, 0, "*-4-0");
                            assertSingleLv(d, 0, 1, "*-4-0");
                        }
                    }
                    case "s0m" -> {
                        if ("1".equals(d.statementId())) {
                            assertCurrentValue(d, 0, "nullable instance 1 type X");
                            assertLinked(d, it(0, "s:4,supplier:4"));
                        }
                    }
                    case "s0a" -> {
                        if ("1".equals(d.statementId())) {
                            assertCurrentValue(d, 0, "supplier.get()");
                            assertLinked(d, it(0, "s:4,supplier:4")); //FIXME no link at all, even though s-4-supplier
                        }
                    }
                    case "s1" -> {
                        assertCurrentValue(d, 2, "supplier.get()");
                        assertLinked(d, it(0, 1, "supplier:-1"), it(2, "supplier:4"));
                        assertSingleLv(d, 2, 0, "*M-4-0M");
                    }
                    case "s1l" -> {
                        if ("1".equals(d.statementId())) {
                            assertCurrentValue(d, 2, "supplier.get()");
                            assertLinked(d, it(0, 1, "s:-1,supplier:-1"),
                                    it(2, "s:4,supplier:4"));
                            assertSingleLv(d, 2, 0, "*M-4-0M");
                            assertSingleLv(d, 2, 1, "*M-4-0M");
                        }
                    }
                    case "s1m" -> {
                        if ("1".equals(d.statementId())) {
                            assertCurrentValue(d, 2, "nullable instance 1 type M");
                            assertLinked(d, it(0, 1, "s:-1,supplier:-1"),
                                    it(0, "s:4,supplier:4"));
                            assertSingleLv(d, 2, 0, "*M-4-0M");
                            assertSingleLv(d, 2, 1, "*M-4-0M");
                        }
                    }
                    case "s2" -> {
                        assertCurrentValue(d, 0, "supplier.get()");
                        assertLinked(d, it(0, ""));
                    }
                    case "s2l" -> {
                        if ("1".equals(d.statementId())) {
                            assertCurrentValue(d, 0, "supplier.get()");
                            assertLinked(d, it(0, ""));
                        }
                    }
                    case "s2m" -> {
                        if ("1".equals(d.statementId())) {
                            assertCurrentValue(d, 0, "nullable instance 1 type Integer");
                            assertLinked(d, it(0, ""));
                        }
                    }

                    case "p0", "p1", "p2" -> {
                        assertCurrentValue(d, 0, "predicate.test(x)");
                        assertLinked(d, it(0, ""));
                    }
                    case "p0l" -> {
                        if ("1".equals(d.statementId())) {
                            assertCurrentValue(d, 1, "predicate.test(x)");
                            assertLinked(d, it0("p:-1,predicate:-1,x:-1"), it(1, ""));
                        }
                    }
                    case "p0m", "p2m" -> {
                        if ("1".equals(d.statementId())) {
                            assertCurrentValue(d, 0, "instance 1 type boolean");
                            assertLinked(d, it(0, ""));
                        }
                    }
                    case "p1l" -> {
                        if ("1".equals(d.statementId())) {
                            assertCurrentValue(d, 1, "p$0.test(x)");
                            assertLinked(d, it(0, ""));
                        }
                    }
                    case "p1m" -> {
                        if ("1".equals(d.statementId())) {
                            assertCurrentValue(d, 2, "instance 1 type boolean");
                            assertLinked(d, it(0, ""));
                        }
                    }
                    case "p2l" -> {
                        if ("1".equals(d.statementId())) {
                            assertCurrentValue(d, 1, "predicate.test(x)");
                            assertLinked(d, it(0, ""));
                        }
                    }
                    case "c0" -> {
                        if ("1".equals(d.statementId())) {
                            assertCurrentValue(d, 0, "consumer");
                            assertLinked(d, it(0, "consumer:0,x:4"));
                            assertSingleLv(d, 0, 1, "0-4-*");
                        }
                    }
                    case "c0l" -> {
                        if ("2".equals(d.statementId())) {
                            assertCurrentValue(d, 1, "consumer");
                            // FIXME
                            assertLinked(d, it0("c:-1,consumer:0,x:-1"), it(1, "c:4,consumer:0,x:4"));
                        }
                    }
                    case "c0m" -> {
                        if ("2".equals(d.statementId())) {
                            assertCurrentValue(d, 0, "consumer");
                            assertLinked(d, it(0, "c:4,consumer:0,x:4"));
                            assertSingleLv(d, 0, 0, "0-4-0");
                            assertSingleLv(d, 0, 2, "0-4-*");
                        }
                    }
                    case "c1" -> {
                        if ("1".equals(d.statementId())) {
                            assertCurrentValue(d, 2, "consumer");
                            assertLinked(d, it(0, 1, "consumer:0,m:-1"), it(2, "consumer:0,m:4"));
                            assertSingleLv(d, 2, 1, "0M-4-*M");
                        }
                    }
                    case "c1m" -> {
                        if ("2".equals(d.statementId())) {
                            assertCurrentValue(d, 2, "consumer");
                            assertLinked(d, it(0, 1, "c:-1,consumer:0,m:-1"), it(2, "c:4,consumer:0,m:4"));
                            assertSingleLv(d, 2, 0, "0M-4-0M");
                            assertSingleLv(d, 2, 2, "0M-4-*M");
                        }
                    }
                    case "c2" -> {
                        if ("1".equals(d.statementId())) {
                            assertCurrentValue(d, 0, "consumer");
                            assertLinked(d, it(0, "consumer:0"));
                        }
                    }
                    case "c2m" -> {
                        if ("2".equals(d.statementId())) {
                            assertCurrentValue(d, 0, "consumer");
                            assertLinked(d, it(0, "consumer:0"));
                        }
                    }
                    case "f0" -> {
                        // X, Y
                        assertCurrentValue(d, 0, "function.apply(x)");
                        assertLinked(d, it(0, "function:4"));
                        assertSingleLv(d, 0, 0, "*-4-1");
                    }
                    case "f1" -> {
                        // X, M
                        assertCurrentValue(d, 2, "function.apply(x)");
                        assertLinked(d, it(0, 1, "function:-1,x:-1"), it(2, "function:4"));
                        assertSingleLv(d, 2, 0, "*M-4-1M");
                    }
                    case "f2" -> {
                        // M, Y
                        assertCurrentValue(d, 2, "function.apply(m)");
                        assertLinked(d, it(0, 1, "function:-1,m:-1"), it(2, "function:4"));
                        assertSingleLv(d, 2, 0, "*-4-1");
                    }
                    case "f3" -> {
                        // X, Integer
                        assertCurrentValue(d, 0, "function.apply(x)");
                        assertLinked(d, it(0, ""));
                    }
                    case "f4" -> {
                        // Integer, Y
                        assertCurrentValue(d, 0, "function.apply(i)");
                        assertLinked(d, it(0, "function:4"));
                        assertSingleLv(d, 0, 0, "*-4-1");
                    }
                    case "f5" -> {
                        // N, M
                        assertCurrentValue(d, 2, "function.apply(n)");
                        assertLinked(d, it(0, 1, "function:-1,n:-1"), it(2, "function:4"));
                        assertSingleLv(d, 2, 0, "*M-4-1M");
                    }
                    case "f6" -> {
                        // String, M
                        assertCurrentValue(d, 2, "function.apply(s)");
                        assertLinked(d, it(0, 1, "function:-1"), it(2, "function:4"));
                        assertSingleLv(d, 2, 0, "*M-4-1M");
                    }
                    case "f7" -> {
                        // M, String
                        assertCurrentValue(d, 0, "function.apply(m)");
                        assertLinked(d, it(0, ""));
                    }
                    case "f8" -> {
                        // Integer, String
                        assertCurrentValue(d, 0, "function.apply(i)");
                        assertLinked(d, it(0, ""));
                    }
                    case "f9" -> {
                        // T, T -- function links to t, but the result does not (*-..-*)
                        assertCurrentValue(d, 0, "function.apply(t)");
                        assertLinked(d, it(0, "function:4"));
                        assertSingleLv(d, 0, 0, "*-4-0;1");
                    }
                    case "f10" -> {
                        // List<T>, T
                        assertCurrentValue(d, 0, "function.apply(ts)");
                        assertLinked(d, it(0, "function:4,ts:4"));
                        assertSingleLv(d, 0, 0, "*-4-0;1");
                        assertSingleLv(d, 0, 1, "*-4-0");
                    }
                    case "f11" -> {
                        // T, List<T>
                        assertCurrentValue(d, 0, "function.apply(t)");
                        assertLinked(d, it(0, "function:4,t:4"));
                        assertSingleLv(d, 0, 0, "0-2-1");
                        assertSingleLv(d, 0, 1, "0-4-*");
                    }
                    case "f12" -> {
                        assertCurrentValue(d, 0, "function.apply(ts)");
                        assertLinked(d, it(0, "function:4,ts:4"));
                        assertSingleLv(d, 0, 0, "*M-4-1M"); // M because List is mutable
                        assertSingleLv(d, 0, 1, "0-4-0"); // possibility to share HC
                    }
                }
            }
            final boolean vIsFunction = d.variable() instanceof ParameterInfo pi && "function".equals(pi.name);
            switch (d.methodInfo().name) {
                case "s0l" -> {
                    if (d.variable() instanceof ParameterInfo pi && "supplier".equals(pi.name)) {
                        if ("0".equals(d.statementId())) {
                            assertCurrentValue(d, 0,
                                    "nullable instance type Supplier<X>/*@Identity*//*@IgnoreMods*/");
                            assertDv(d, MultiLevel.INDEPENDENT_HC_DV, Property.INDEPENDENT);
                            assertDv(d, MultiLevel.EFFECTIVELY_IMMUTABLE_HC_DV, Property.IMMUTABLE);
                        }
                    }
                    if ("s".equals(d.variableName())) {
                        // both statements "0" and "1"
                        assertCurrentValue(d, 0, "/*inline get*/supplier.get()/*{L supplier:4}*/");
                        assertLinked(d, it(0, "supplier:4"));
                        assertSingleLv(d, 0, 0, "*-4-0");
                        assertDv(d, MultiLevel.INDEPENDENT_HC_DV, Property.INDEPENDENT);
                        assertDv(d, MultiLevel.EFFECTIVELY_IMMUTABLE_HC_DV, Property.IMMUTABLE);
                    }
                }
                case "s0m" -> {
                    if ("s".equals(d.variableName())) {
                        if ("0".equals(d.statementId())) {
                            assertCurrentValue(d, 0, "supplier::get");
                            assertLinked(d, it(0, "supplier:4"));
                            assertSingleLv(d, 0, 0, "0-4-0");
                        }
                        if ("1".equals(d.statementId())) {
                            assertCurrentValue(d, 0, "instance 1 type Supplier<X>");
                            assertLinked(d, it(0, "supplier:4"));
                            assertSingleLv(d, 0, 0, "0-4-0");
                        }
                    }
                }
                case "s0a" -> {
                    if ("0".equals(d.statementId()) && "s".equals(d.variableName())) {
                        assertCurrentValue(d, 0, "new $2(){public X get(){return supplier.get();}}");
                        assertLinked(d, it(0, "supplier:4"));
                        assertSingleLv(d, 0, 0, "*-4-0");
                    }
                }
                case "s1m" -> {
                    if ("s".equals(d.variableName())) {
                        if ("0".equals(d.statementId())) {
                            assertCurrentValue(d, 0, "supplier::get");
                            assertLinked(d, it(0, 1, "supplier:-1"), it(2, "supplier:4"));
                            assertSingleLv(d, 2, 0, "0M-4-0M");
                        }
                        if ("1".equals(d.statementId())) {
                            assertCurrentValue(d, 0, "instance 1 type Supplier<M>");
                            assertLinked(d, it(0, 1, "supplier:-1"), it(2, "supplier:4"));
                            assertSingleLv(d, 2, 0, "0M-4-0M");
                        }
                    }
                }
                case "s2m" -> {
                    if ("s".equals(d.variableName())) {
                        if ("0".equals(d.statementId())) {
                            assertCurrentValue(d, 0, "supplier::get");
                            assertLinked(d, it(0, ""));
                        }
                        if ("1".equals(d.statementId())) {
                            assertCurrentValue(d, 0, "instance 1 type Supplier<Integer>");
                            assertLinked(d, it(0, ""));
                        }
                    }
                }
                case "p0l" -> {
                    if (d.variable() instanceof ParameterInfo pi && "predicate".equals(pi.name)) {
                        if ("0".equals(d.statementId())) {
                            assertCurrentValue(d, 1,
                                    "nullable instance type Predicate<X>/*@IgnoreMods*/");
                            assertDv(d, 1, MultiLevel.INDEPENDENT_HC_DV, Property.INDEPENDENT);
                            assertDv(d, 1, MultiLevel.EFFECTIVELY_IMMUTABLE_HC_DV, Property.IMMUTABLE);
                        }
                    }
                    if ("p".equals(d.variableName())) {
                        if ("0".equals(d.statementId())) {
                            assertCurrentValue(d, 1, "/*inline test*/predicate.test(t)");
                            assertLinked(d, it0("predicate:-1"), it(1, ""));
                        }
                        if ("1".equals(d.statementId())) {
                            assertCurrentValue(d, 1, "/*inline test*/predicate.test(t)");
                            assertLinked(d, it0("predicate:-1,x:-1"), it(1, ""));
                        }
                    }
                }
                case "p1l" -> {
                    if (d.variable() instanceof ParameterInfo pi && "predicate".equals(pi.name)) {
                        if ("0".equals(d.statementId())) {
                            assertCurrentValue(d, 1,
                                    "nullable instance type Predicate<M>/*@IgnoreMods*/");
                            assertDv(d, 1, MultiLevel.INDEPENDENT_HC_DV, Property.INDEPENDENT);
                            assertDv(d, 1, MultiLevel.EFFECTIVELY_IMMUTABLE_HC_DV, Property.IMMUTABLE);
                        }
                    }
                    if ("p".equals(d.variableName())) {
                        if ("0".equals(d.statementId())) {
                            assertCurrentValue(d, 1, "instance 0 type $5");
                            assertLinked(d, it0("predicate:-1"), it(1, ""));
                        }
                        if ("1".equals(d.statementId())) {
                            assertCurrentValue(d, 3, "instance 0 type $5");
                            assertLinked(d, it0("predicate:-1,x:-1"),
                                    it(1, 2, "x:-1"),
                                    it(3, ""));
                        }
                    }
                }
                case "c0l" -> {
                    if ("c".equals(d.variableName())) {
                        if ("0".equals(d.statementId())) {
                            assertCurrentValue(d, 0, "instance 0 type $7/*{L consumer:4}*/");
                            assertLinked(d, it(0, "consumer:4"));
                            assertSingleLv(d, 0, 0, "0-4-0"); // FIXME here we go wrong... so must use new method!
                        }
                    }
                }
                case "c0m" -> {
                    if ("c".equals(d.variableName())) {
                        if ("0".equals(d.statementId())) {
                            assertCurrentValue(d, 0, "consumer::accept");
                            assertInstanceOf(MethodReference.class, d.variableInfo().getValue());
                            assertLinked(d, it(0, "consumer:4"));
                            assertSingleLv(d, 0, 0, "0-4-0");
                        }
                    }
                }
                case "c1" -> {
                    if (d.variable() instanceof ParameterInfo pi && "consumer".equals(pi.name)) {
                        if ("0".equals(d.statementId())) {
                            assertCurrentValue(d, 2,
                                    "nullable instance type Consumer<M>/*@IgnoreMods*/");
                            assertLinked(d, it(0, 1, "m:-1"), it(2, "m:4"));
                            assertSingleLv(d, 2, 0, "0M-4-*M");
                        }
                    }
                }
                case "c1m" -> {
                    if ("c".equals(d.variableName())) {
                        if ("0".equals(d.statementId())) {
                            assertCurrentValue(d, 0, "consumer::accept");
                            assertInstanceOf(MethodReference.class, d.variableInfo().getValue());
                            assertLinked(d, it(0, 1, "consumer:-1"), it(2, "consumer:4"));
                            assertSingleLv(d, 2, 0, "0M-4-0M");
                        }
                    }
                }
                case "c2" -> {
                    if (d.variable() instanceof ParameterInfo pi && "consumer".equals(pi.name)) {
                        if ("0".equals(d.statementId())) {
                            assertCurrentValue(d, 0,
                                    "nullable instance type Consumer<Integer>/*@IgnoreMods*/");
                            assertLinked(d, it(0, ""));
                        }
                    }
                }
                case "c2m" -> {
                    if (d.variable() instanceof ParameterInfo pi && "consumer".equals(pi.name)) {
                        if ("0".equals(d.statementId())) {
                            assertCurrentValue(d, 0,
                                    "nullable instance type Consumer<Integer>/*@IgnoreMods*/");
                            assertLinked(d, it(0, ""));
                        }
                    }
                }
                case "f0" -> {
                    if (vIsFunction) {
                        assertCurrentValue(d, 0, "nullable instance type Function<X,Y>/*@IgnoreMods*/");
                        assertLinked(d, it(0, "x:4"));
                        assertSingleLv(d, 0, 0, "0-4-*");
                    }
                }
                case "f1" -> {
                    if (vIsFunction) {
                        assertCurrentValue(d, 2, "nullable instance type Function<X,M>/*@IgnoreMods*/");
                        assertLinked(d, it(0, "x:4"));
                        assertSingleLv(d, 0, 0, "0-4-*");
                    }
                }
                case "f2" -> {
                    if (vIsFunction) {
                        assertCurrentValue(d, 2, "nullable instance type Function<M,Y>/*@IgnoreMods*/");
                        assertLinked(d, it(0, 1, "m:-1"), it(2, "m:4"));
                        assertSingleLv(d, 2, 0, "0M-4-*M");
                    }
                }
                case "f3" -> {
                    if (vIsFunction) {
                        assertCurrentValue(d, 0, "nullable instance type Function<X,Integer>/*@IgnoreMods*/");
                        assertLinked(d, it(0, "x:4"));
                        assertSingleLv(d, 0, 0, "0-4-*");
                    }
                }
                case "f4" -> {
                    if (vIsFunction) {
                        assertCurrentValue(d, 0, "nullable instance type Function<Integer,Y>/*@IgnoreMods*/");
                        assertLinked(d, it(0, ""));
                    }
                }
                case "f5" -> {
                    if (vIsFunction) {
                        assertCurrentValue(d, 2, "nullable instance type Function<N,M>/*@IgnoreMods*/");
                        assertLinked(d, it(0, 1, "n:-1"), it(2, "n:4"));
                        assertSingleLv(d, 2, 0, "0M-4-*M");
                    }
                }
                case "f6" -> {
                    if (vIsFunction) {
                        assertCurrentValue(d, 2, "nullable instance type Function<String,M>/*@IgnoreMods*/");
                        assertLinked(d, it(0, ""));
                    }
                }
                case "f7" -> {
                    if (vIsFunction) {
                        assertCurrentValue(d, 2, "nullable instance type Function<M,String>/*@IgnoreMods*/");
                        assertLinked(d, it(0, 1, "m:-1"), it(2, "m:4"));
                        assertSingleLv(d, 2, 0, "0M-4-*M");
                    }
                }
                case "f8" -> {
                    if (vIsFunction) {
                        assertCurrentValue(d, 0, "nullable instance type Function<Integer,String>/*@IgnoreMods*/");
                        assertLinked(d, it(0, ""));
                    }
                }
                case "f9" -> {
                    // T, T
                    if (vIsFunction) {
                        assertCurrentValue(d, 0, "nullable instance type Function<T,T>/*@IgnoreMods*/");
                        assertLinked(d, it(0, "t:4"));
                        assertSingleLv(d, 0, 0, "0;1-4-*");
                    }
                }
                case "f10" -> {
                    // List<T>, T
                    if (vIsFunction) {
                        assertCurrentValue(d, 0, "nullable instance type Function<List<T>,T>/*@IgnoreMods*/");
                        assertLinked(d, it(0, "ts:4"));
                        assertSingleLv(d, 0, 0, "0-4-0");
                    }
                }
                case "f11" -> {
                    // T, List<T>
                    if (vIsFunction) {
                        assertCurrentValue(d, 0, "nullable instance type Function<T,List<T>>/*@IgnoreMods*/");
                        assertLinked(d, it(0, "t:4"));
                        assertSingleLv(d, 0, 0, "0-4-*");
                    }
                }
                case "f12" -> {
                    // List<T>, Set<T>
                    if (vIsFunction) {
                        assertCurrentValue(d, 0, "nullable instance type Function<List<T>,Set<T>>/*@IgnoreMods*/");
                        assertLinked(d, it(0, "ts:4"));
                        assertSingleLv(d, 0, 0, "0-4-0");
                    }
                }
            }
        };

        MethodAnalyserVisitor methodAnalyserVisitor = d -> {
            if ("get".equals(d.methodInfo().name) && "s0l".equals(d.enclosingMethod().name)) {
                // () -> supplier.get() ~ public X get() { return supplier.get(); }
                assertDv(d, MultiLevel.EFFECTIVELY_IMMUTABLE_HC_DV, Property.IMMUTABLE);
            }
            HiddenContentTypes hct = d.methodInfo().methodResolution.get().hiddenContentTypes();
            if ("get".equals(d.methodInfo().name) && "s0a".equals(d.enclosingMethod().name)) {
                LinkedVariables lvs = d.methodAnalysis().getLinkedVariables();
                assertLinked(d, lvs, it(0, "supplier:4"));
                assertSingleLv(d, lvs, 0, 0, "*-4-0");
                assertDv(d, 0, MultiLevel.INDEPENDENT_HC_DV, Property.INDEPENDENT);
                assertEquals("$2:X - get:", hct.toString());
                if (d.methodAnalysis().getHiddenContentSelector() instanceof HiddenContentSelector.All all) {
                    assertEquals(0, all.getHiddenContentIndex());
                } else fail();
            }
            if ("test".equals(d.methodInfo().name) && "p0l".equals(d.enclosingMethod().name)) {
                String expected = d.iteration() == 0 ? "<m:test>" : "/*inline test*/predicate.test(t)";
                assertEquals(expected, d.methodAnalysis().getSingleReturnValue().toString());
                // result is a boolean, so 'none'
                assertEquals("$4:X - test:", hct.toString());
                assertFalse(hct.isEmpty());
                assertEquals(1, hct.size());
                assertTrue(d.methodAnalysis().getHiddenContentSelector().isNone());
            }
            if ("test".equals(d.methodInfo().name) && "p1l".equals(d.enclosingMethod().name)) {
                String expected = d.iteration() == 0 ? "<m:test>" : "predicate.test(t)";
                assertEquals(expected, d.methodAnalysis().getSingleReturnValue().toString());
                // result is a boolean, so 'none'
                assertEquals("$5: - test:", hct.toString());
                assertTrue(d.methodAnalysis().getHiddenContentSelector().isNone());
            }
            if ("f10".equals(d.methodInfo().name)) {
                assertEquals("Linking_1A: - f10:T", hct.toString());
            }
        };

        TypeAnalyserVisitor typeAnalyserVisitor = d -> {
            MethodInfo enclosingMethod = d.typeInspection().enclosingMethod();
            boolean isAnonymous = d.typeInfo().packageNameOrEnclosingType.isRight() && enclosingMethod != null;
            if (isAnonymous && "p0l".equals(enclosingMethod.name)) {
                assertEquals("$4:X", d.typeInfo().typeResolution.get().hiddenContentTypes().toString());
            }
            if (isAnonymous && "p1l".equals(enclosingMethod.name)) {
                // Predicate<M> does not have hidden content types
                assertTrue(d.typeInfo().typeResolution.get().hiddenContentTypes().isEmpty());
            }
            if ("M".equals(d.typeInfo().simpleName)) {
                assertTrue(d.typeInfo().typeResolution.get().hiddenContentTypes().isEmpty());
            }
        };

        // finalizer on a parameter
        testClass("Linking_1A", 0, 0, new DebugConfiguration.Builder()
                .addEvaluationResultVisitor(evaluationResultVisitor)
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                .addAfterTypeAnalyserVisitor(typeAnalyserVisitor)
                .build());
    }

}
