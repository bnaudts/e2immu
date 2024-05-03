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
import org.e2immu.analyser.analysis.ParameterAnalysis;
import org.e2immu.analyser.config.DebugConfiguration;
import org.e2immu.analyser.model.MethodInfo;
import org.e2immu.analyser.model.MultiLevel;
import org.e2immu.analyser.model.ParameterInfo;
import org.e2immu.analyser.model.expression.ConstructorCall;
import org.e2immu.analyser.model.expression.MethodReference;
import org.e2immu.analyser.model.variable.ReturnVariable;
import org.e2immu.analyser.parser.CommonTestRunner;
import org.e2immu.analyser.visitor.EvaluationResultVisitor;
import org.e2immu.analyser.visitor.MethodAnalyserVisitor;
import org.e2immu.analyser.visitor.StatementAnalyserVariableVisitor;
import org.e2immu.analyser.visitor.TypeAnalyserVisitor;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import static org.e2immu.analyser.analyser.LinkedVariables.NOT_YET_SET_STR;
import static org.e2immu.analyser.parser.VisitorTestSupport.IterationInfo.*;
import static org.junit.jupiter.api.Assertions.*;

public class Test_Linking1A extends CommonTestRunner {

    public Test_Linking1A() {
        super(true);
    }

    private final Map<String, String> anonymousOfEnclosing = new HashMap<>();

    @Test
    public void test_0() throws IOException {
        EvaluationResultVisitor evaluationResultVisitor = d -> {
            final LinkedVariables lvs = d.evaluationResult().linkedVariablesOfExpression();
            switch (d.methodInfo().name) {
                case "s0m" -> {
                    if ("0".equals(d.statementId())) {
                        assertEquals("supplier::get", d.evaluationResult().value().toString());
                        assertLinked(d, lvs, it(0, "s:0,supplier:4"));
                    }
                }
                case "s0l" -> {
                    if ("0".equals(d.statementId())) {
                        String expected = d.iteration() == 0 ? "/*inline get*/supplier.get()/*{DL supplier:-1}*/"
                                : "/*inline get*/supplier.get()/*{L supplier:4}*/";
                        assertEquals(expected,
                                d.evaluationResult().value().toString());
                        assertLinked(d, lvs, it0("s:0,supplier:-1"), it(1, "s:0,supplier:4"));
                    }
                    if ("1".equals(d.statementId())) {
                        String expected = d.iteration() == 0 ? "<m:get>" : "supplier.get()";
                        assertEquals(expected, d.evaluationResult().value().toString());
                        assertLinked(d, lvs, it(0, "s:4"));
                    }
                }
                case "p0l" -> {
                    if ("1".equals(d.statementId())) {
                        // p.test(x)
                        String expected = d.iteration() == 0 ? "<m:test>" : "predicate.test(x)";
                        assertEquals(expected, d.evaluationResult().value().toString());
                        assertLinked(d, lvs, it(0, ""));
                        //       it0("p:-1,x:-1"), it(1, ""));
                    }
                }
                case "f9m" -> {
                    if ("0".equals(d.statementId())) {
                        assertEquals("function::apply", d.evaluationResult().value().toString());
                        assertLinked(d, lvs, it(0, "f:0,function:4"));
                        assertSingleLv(d, lvs, 0, 1, "0;1-4-0;1");
                    }
                    if ("1".equals(d.statementId())) {
                        // f.apply(t)
                        assertEquals("nullable instance 1 type T", d.evaluationResult().value().toString());
                        assertLinked(d, lvs, it(0, "f:4"));
                    }
                }
                case "f10" -> {
                    // p.test(x)
                    assertEquals("function.apply(ts)", d.evaluationResult().value().toString());
                    assertLinked(d, lvs, it(0, "function:4,ts:4"));
                    assertSingleLv(d, 0, 0, "*-4-0");
                    assertSingleLv(d, 0, 1, "*-4-0");
                }
            }
        };
        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            boolean isStatement0 = "0".equals(d.statementId());
            boolean isStatement1 = "1".equals(d.statementId());
            boolean isStatement2 = "2".equals(d.statementId());
            String myAnonymous = anonymousOfEnclosing.get(d.methodInfo().name);

            if (d.variable() instanceof ReturnVariable) {
                switch (d.methodInfo().name) {
                    case "s0" -> {
                        assertCurrentValue(d, 0, "supplier.get()");
                        assertLinked(d, it(0, "supplier:4"));
                        assertSingleLv(d, 2, 0, "*-4-0");
                    }
                    case "s0l", "s0a" -> {
                        if (isStatement1) {
                            assertCurrentValue(d, 1, "supplier.get()");
                            assertLinked(d, it0("s:-1,supplier:-1"), it(1, "s:4,supplier:4"));
                            assertSingleLv(d, 1, 0, "*-4-0");
                            assertSingleLv(d, 1, 1, "*-4-0");
                        }
                    }
                    case "s0m" -> {
                        if (isStatement1) {
                            assertCurrentValue(d, 0, "nullable instance 1 type X");
                            assertLinked(d, it(0, "s:4,supplier:4"));
                            assertSingleLv(d, 0, 0, "*-4-0");
                            assertSingleLv(d, 0, 1, "*-4-0");
                        }
                    }
                    case "s1" -> {
                        assertCurrentValue(d, 2, "supplier.get()");
                        assertLinked(d, it(0, 1, "supplier:-1"), it(2, "supplier:4"));
                        assertSingleLv(d, 2, 0, "*M-4-0M");
                    }
                    case "s1l" -> {
                        if (isStatement1) {
                            assertCurrentValue(d, 2, "supplier.get()");
                            assertLinked(d, it(0, 1, "s:-1,supplier:-1"),
                                    it(2, "s:4,supplier:4"));
                            assertSingleLv(d, 2, 0, "*M-4-0M");
                            assertSingleLv(d, 2, 1, "*M-4-0M");
                        }
                    }
                    case "s1m" -> {
                        if (isStatement1) {
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
                        if (isStatement1) {
                            assertCurrentValue(d, 0, "supplier.get()");
                            assertLinked(d, it(0, ""));
                        }
                    }
                    case "s2m", "f3m" -> {
                        if (isStatement1) {
                            assertCurrentValue(d, 0, "nullable instance 1 type Integer");
                            assertLinked(d, it(0, ""));
                        }
                    }
                    case "sp0" -> {
                        assertCurrentValue(d, 2, "supplier.get()");
                        assertLinked(d, it(0, 1, "supplier:-1"), it(2, "supplier:4"));
                        assertSingleLv(d, 2, 0, "0,1-4-0,1");
                    }
                    case "sp1" -> {
                        assertCurrentValue(d, 2, "supplier.get()");
                        assertLinked(d, it(0, 1, "supplier:-1"), it(2, "supplier:4"));
                        // FIXME should become 0,1M-4-0,1M, see also f13
                        //assertSingleLv(d, 4, 0, "0-4-0");
                    }
                    case "p0", "p1", "p2" -> {
                        assertCurrentValue(d, 0, "predicate.test(x)");
                        assertLinked(d, it(0, ""));
                    }
                    case "p0l", "p2l" -> {
                        if (isStatement1) {
                            assertCurrentValue(d, 1, "predicate.test(x)");
                            assertLinked(d, it(0, ""));
                        }
                    }
                    case "p0a" -> {
                        if (isStatement1) {
                            assertCurrentValue(d, 2, "predicate.test(x)");
                            assertLinked(d, it0(NOT_YET_SET_STR), it(1, ""));
                        }
                    }
                    case "p0m", "p2m" -> {
                        if (isStatement1) {
                            assertCurrentValue(d, 0, "instance 1 type boolean");
                            assertLinked(d, it(0, ""));
                        }
                    }
                    case "p1l" -> {
                        if (isStatement1) {
                            assertCurrentValue(d, 1, "p$0.test(x)");
                            assertLinked(d, it(0, ""));
                        }
                    }
                    case "p1a" -> {
                        if (isStatement1) {
                            assertCurrentValue(d, 3, "(new " + myAnonymous
                                                     + "(){public boolean test(M x){return predicate.test(x);}}).test(x)");
                            assertLinked(d, it0(NOT_YET_SET_STR), it(1, ""));
                        }
                    }
                    case "p1m" -> {
                        if (isStatement1) {
                            assertCurrentValue(d, 2, "instance 1 type boolean");
                            assertLinked(d, it(0, ""));
                        }
                    }
                    case "p2a" -> {
                        if (isStatement1) {
                            assertCurrentValue(d, 1, "predicate.test(x)");
                            assertLinked(d, it0(NOT_YET_SET_STR), it(0, ""));
                        }
                    }
                    case "ppa0" -> {
                        assertCurrentValue(d, 0, "predicate.test(x,y)");
                        assertLinked(d, it(0, ""));
                    }
                    case "ppb0" -> {
                        assertCurrentValue(d, 1, "predicate.test(pair.f,pair.g)");
                        assertLinked(d, it(0, ""));
                    }
                    case "ppc0" -> {
                        assertCurrentValue(d, 0, "predicate.test(new Pair<>(x,y))");
                        assertLinked(d, it(0, ""));
                    }
                    case "c0" -> {
                        if (isStatement1) {
                            assertCurrentValue(d, 0, "consumer");
                            assertLinked(d, it(0, "consumer:0,x:4"));
                            assertSingleLv(d, 0, 1, "0-4-*");
                        }
                    }
                    case "c0l" -> {
                        if (isStatement2) {
                            assertCurrentValue(d, 1, "consumer");
                            assertLinked(d, it0("c:-1,consumer:0,x:-1"),
                                    it(1, "c:4,consumer:0,x:4"));
                            assertSingleLv(d, 0, 0, "0-4-0");
                            assertSingleLv(d, 0, 2, "0-4-*");
                        }
                    }
                    case "c0m" -> {
                        if (isStatement2) {
                            assertCurrentValue(d, 0, "consumer");
                            assertLinked(d, it(0, "c:4,consumer:0,x:4"));
                            assertSingleLv(d, 0, 0, "0-4-0");
                            assertSingleLv(d, 0, 2, "0-4-*");
                        }
                    }
                    case "c0a" -> {
                        if (isStatement2) {
                            assertCurrentValue(d, 2, "consumer");
                            assertLinked(d, it0(NOT_YET_SET_STR), it1("c:-1,consumer:0,this:-1,x:-1"),
                                    it(2, "c:4,consumer:0,x:4"));
                            assertSingleLv(d, 2, 0, "0-4-0");
                            assertSingleLv(d, 2, 2, "0-4-*");
                        }
                    }
                    case "c1" -> {
                        if (isStatement1) {
                            assertCurrentValue(d, 2, "consumer");
                            assertLinked(d, it(0, 1, "consumer:0,m:-1"), it(2, "consumer:0,m:4"));
                            assertSingleLv(d, 2, 1, "0M-4-*M");
                        }
                    }
                    case "c1m" -> {
                        if (isStatement2) {
                            assertCurrentValue(d, 2, "consumer");
                            assertLinked(d, it(0, 1, "c:-1,consumer:0,m:-1"), it(2, "c:4,consumer:0,m:4"));
                            assertSingleLv(d, 2, 0, "0M-4-0M");
                            assertSingleLv(d, 2, 2, "0M-4-*M");
                        }
                    }
                    case "c1a" -> {
                        if (isStatement2) {
                            assertCurrentValue(d, 3, "consumer");
                            assertLinked(d, it0(NOT_YET_SET_STR), it1("c:-1,consumer:0,m:-1,this:-1"),
                                    it(2, 2, "c:-1,consumer:0,m:-1"),
                                    it(3, "c:4,consumer:0,m:4"));
                            assertSingleLv(d, 3, 0, "0M-4-0M");
                            assertSingleLv(d, 3, 2, "0M-4-*M");
                        }
                    }
                    case "c2" -> {
                        if (isStatement1) {
                            assertCurrentValue(d, 0, "consumer");
                            assertLinked(d, it(0, "consumer:0"));
                        }
                    }
                    case "c2m" -> {
                        if (isStatement2) {
                            assertCurrentValue(d, 0, "consumer");
                            assertLinked(d, it(0, "consumer:0"));
                        }
                    }
                    case "c2a" -> {
                        if (isStatement2) {
                            assertCurrentValue(d, 1, "consumer");
                            assertLinked(d, it0(NOT_YET_SET_STR), it(1, "consumer:0"));
                        }
                    }
                    case "f0" -> {
                        // X, Y
                        assertCurrentValue(d, 0, "function.apply(x)");
                        assertLinked(d, it(0, "function:4"));
                        assertSingleLv(d, 0, 0, "*-4-1");
                    }
                    case "f0m", "f4m" -> {
                        if (isStatement1) {
                            assertCurrentValue(d, 0, "nullable instance 1 type Y");
                            assertLinked(d, it(0, "f:4,function:4"));
                            assertSingleLv(d, 0, 0, "*-4-1");
                            assertSingleLv(d, 0, 1, "*-4-1");
                        }
                    }
                    case "f0a" -> {
                        if (isStatement1) {
                            assertCurrentValue(d, 2, "function.apply(x)");
                            assertLinked(d, it0("f:-1"), it1("f:-1,function:-1,this:-1,x:-1"),
                                    it(2, "f:4,function:4"));
                            assertSingleLv(d, 2, 0, "*-4-1");
                            assertSingleLv(d, 2, 1, "*-4-1");
                        }
                    }
                    case "f1" -> {
                        // X, M
                        assertCurrentValue(d, 2, "function.apply(x)");
                        assertLinked(d, it(0, 1, "function:-1,x:-1"), it(2, "function:4"));
                        assertSingleLv(d, 2, 0, "*M-4-1M");
                    }
                    case "f1m" -> {
                        if (isStatement1) {
                            assertCurrentValue(d, 2, "nullable instance 1 type M");
                            assertLinked(d, it(0, 1, "f:-1,function:-1,x:-1"), it(2, "f:4,function:4"));
                            assertSingleLv(d, 2, 0, "*M-4-1M");
                            assertSingleLv(d, 2, 1, "*M-4-1M");
                        }
                    }
                    case "f1a" -> {
                        if (isStatement1) {
                            assertCurrentValue(d, 2, "function.apply(x)");
                            assertLinked(d, it0("f:-1,x:-1"), it1("f:-1,function:-1,this:-1,x:-1"),
                                    it(2, "f:4,function:4"));
                            assertSingleLv(d, 2, 0, "*M-4-1M");
                            assertSingleLv(d, 2, 1, "*M-4-1M");
                        }
                    }
                    case "f2" -> {
                        // M, Y
                        assertCurrentValue(d, 2, "function.apply(m)");
                        assertLinked(d, it(0, 1, "function:-1,m:-1"), it(2, "function:4"));
                        assertSingleLv(d, 2, 0, "*-4-1");
                    }
                    case "f2m" -> {
                        if (isStatement1) {
                            assertCurrentValue(d, 2, "nullable instance 1 type Y");
                            assertLinked(d, it(0, 1, "f:-1,function:-1,m:-1"), it(2, "f:4,function:4"));
                            assertSingleLv(d, 2, 0, "*-4-1");
                            assertSingleLv(d, 2, 1, "*-4-1");
                        }
                    }
                    case "f2a" -> {
                        if (isStatement1) {
                            assertCurrentValue(d, 3,
                                    "(new " + myAnonymous + "(){public Y apply(M m){return function.apply(m);}}).apply(m)");
                            assertLinked(d, it0("f:-1,m:-1"), it1("f:-1,function:-1,m:-1,this:-1"),
                                    it(2, 2, "f:-1,function:-1"),
                                    it(2, "f:4,function:4"));
                            assertSingleLv(d, 3, 0, "*-4-1");
                            assertSingleLv(d, 3, 1, "*-4-1");
                        }
                    }
                    case "f3" -> {
                        // X, Integer
                        assertCurrentValue(d, 0, "function.apply(x)");
                        assertLinked(d, it(0, ""));
                    }
                    case "f3a" -> {
                        if (isStatement1) {
                            assertCurrentValue(d, 2, "function.apply(x)");
                            assertLinked(d, it0(NOT_YET_SET_STR), it(1, ""));
                        }
                    }
                    // f3m ~ s2m
                    case "f4" -> {
                        // Integer, Y
                        assertCurrentValue(d, 0, "function.apply(i)");
                        assertLinked(d, it(0, "function:4"));
                        assertSingleLv(d, 0, 0, "*-4-1");
                    }
                    case "f4a" -> {
                        if (isStatement1) {
                            assertCurrentValue(d, 1, "function.apply(i)");
                            assertLinked(d, it0("f:-1"), it(1, "f:4,function:4"));
                            assertSingleLv(d, 2, 0, "*-4-1");
                            assertSingleLv(d, 2, 1, "*-4-1");
                        }
                    }
                    // f4m ~ f0m
                    case "f5" -> {
                        // N, M
                        assertCurrentValue(d, 2, "function.apply(n)");
                        assertLinked(d, it(0, 1, "function:-1,n:-1"), it(2, "function:4"));
                        assertSingleLv(d, 2, 0, "*M-4-1M");
                    }
                    case "f5m" -> {
                        if (isStatement1) {
                            assertCurrentValue(d, 2, "nullable instance 1 type M");
                            assertLinked(d, it(0, 1, "f:-1,function:-1,n:-1"), it(2, "f:4,function:4"));
                            assertSingleLv(d, 2, 0, "*M-4-1M");
                            assertSingleLv(d, 2, 1, "*M-4-1M");
                        }
                    }
                    case "f5a" -> {
                        if (isStatement1) {
                            assertCurrentValue(d, 3, "(new $21(){public M apply(N n){return function.apply(n);}}).apply(n)");
                            assertLinked(d, it0("f:-1,n:-1"), it1("f:-1,function:-1,n:-1,this:-1"),
                                    it(2, 2, "f:-1,function:-1"),
                                    it(3, "f:4,function:4"));
                            assertSingleLv(d, 3, 0, "*M-4-1M");
                            assertSingleLv(d, 3, 1, "*M-4-1M");
                        }
                    }
                    case "f6" -> {
                        // String, M
                        assertCurrentValue(d, 2, "function.apply(s)");
                        assertLinked(d, it(0, 1, "function:-1"), it(2, "function:4"));
                        assertSingleLv(d, 2, 0, "*M-4-1M");
                    }
                    case "f6m" -> {
                        if (isStatement1) {
                            assertCurrentValue(d, 2, "nullable instance 1 type M");
                            assertLinked(d, it(0, 1, "f:-1,function:-1"), it(2, "f:4,function:4"));
                            assertSingleLv(d, 2, 0, "*M-4-1M");
                            assertSingleLv(d, 2, 1, "*M-4-1M");
                        }
                    }
                    case "f6a" -> {
                        if (isStatement1) {
                            assertCurrentValue(d, 2, "function.apply(s)");
                            assertLinked(d, it0("f:-1"), it1("f:-1,function:-1"),
                                    it(2, "f:4,function:4"));
                            assertSingleLv(d, 2, 0, "*M-4-1M");
                            assertSingleLv(d, 2, 1, "*M-4-1M");
                        }
                    }
                    case "f7" -> {
                        // M, String
                        assertCurrentValue(d, 0, "function.apply(m)");
                        assertLinked(d, it(0, ""));
                    }
                    case "f7m" -> {
                        if (isStatement1) {
                            assertCurrentValue(d, 2, "nullable instance 1 type String");
                            assertLinked(d, it(0, ""));
                        }
                    }
                    case "f7a" -> {
                        if (isStatement1) {
                            assertCurrentValue(d, 3, "(new " + myAnonymous
                                                     + "(){public String apply(M m){return function.apply(m);}}).apply(m)");
                            assertLinked(d, it0(NOT_YET_SET_STR), it(1, ""));
                        }
                    }
                    case "f8" -> {
                        // Integer, String
                        assertCurrentValue(d, 0, "function.apply(i)");
                        assertLinked(d, it(0, ""));
                    }
                    case "f8m" -> {
                        if (isStatement1) {
                            assertCurrentValue(d, 0, "nullable instance 1 type String");
                            assertLinked(d, it(0, ""));
                        }
                    }
                    case "f8a" -> {
                        if (isStatement1) {
                            assertCurrentValue(d, 1, "function.apply(i)");
                            assertLinked(d, it0(NOT_YET_SET_STR), it(1, ""));
                        }
                    }
                    case "f9" -> {
                        // T, T -- function links to t, but the result does not (*-..-*)
                        assertCurrentValue(d, 0, "function.apply(t)");
                        assertLinked(d, it(0, "function:4"));
                        assertSingleLv(d, 0, 0, "*-4-0;1");
                    }
                    case "f9m" -> {
                        if (isStatement1) {
                            assertCurrentValue(d, 0, "nullable instance 1 type T");
                            assertLinked(d, it(0, "f:4,function:4"));
                            assertSingleLv(d, 0, 0, "*-4-0;1");
                            assertSingleLv(d, 0, 1, "*-4-0;1");
                        }
                    }
                    case "f9a" -> {
                        if (isStatement1) {
                            assertCurrentValue(d, 2, "function.apply(t)");
                            assertLinked(d, it0("f:-1"), it1("f:-1,function:-1,t:-1,this:-1"),
                                    it(2, "f:4,function:4"));
                            assertSingleLv(d, 2, 0, "*-4-0;1");
                            assertSingleLv(d, 2, 1, "*-4-0;1");
                        }
                    }
                    case "f10" -> {
                        // List<T>, T
                        assertCurrentValue(d, 0, "function.apply(ts)");
                        assertLinked(d, it(0, "function:4,ts:4"));
                        assertSingleLv(d, 0, 0, "*-4-0");
                        assertSingleLv(d, 0, 1, "*-4-0");
                    }
                    case "f10m" -> {
                        if (isStatement1) {
                            assertCurrentValue(d, 0, "nullable instance 1 type T");
                            assertLinked(d, it(0, "f:4,function:4,ts:4"));
                            assertSingleLv(d, 0, 0, "*-4-0");
                            assertSingleLv(d, 0, 1, "*-4-0");
                            assertSingleLv(d, 0, 2, "*-4-0");
                        }
                    }
                    case "f10a" -> {
                        if (isStatement1) {
                            assertCurrentValue(d, 2, "(new " + myAnonymous
                                                     + "(){public T apply(List<T> list){return function.apply(list);}}).apply(ts)");
                            assertLinked(d, it0("f:-1,ts:-1"),
                                    it1("f:-1,function:-1,this:-1,ts:-1"),
                                    it(2, "f:4,function:4,ts:4"));
                            assertSingleLv(d, 2, 0, "*-4-0");
                            assertSingleLv(d, 2, 1, "*-4-0");
                            assertSingleLv(d, 2, 2, "*-4-0");
                        }
                    }
                    case "f11" -> {
                        // T, List<T>
                        assertCurrentValue(d, 0, "function.apply(t)");
                        assertLinked(d, it(0, "function:4,t:4"));
                        //FIXME   assertSingleLv(d, 0, 0, "0-4-0");
                        assertSingleLv(d, 0, 1, "0-4-*");
                    }
                    case "f12" -> {
                        assertCurrentValue(d, 0, "function.apply(ts)");
                        assertLinked(d, it(0, "function:4,ts:4"));
                        //FIXME assertSingleLv(d, 0, 0, "0-4-0");
                        //assertSingleLv(d, 0, 1, "0-4-0");
                    }
                }
            }
            final boolean vIsFunction = d.variable() instanceof ParameterInfo pi && "function".equals(pi.name);
            switch (d.methodInfo().name) {
                case "s0l" -> {
                    if (d.variable() instanceof ParameterInfo pi && "supplier".equals(pi.name)) {
                        if (isStatement0) {
                            assertCurrentValue(d, 1,
                                    "nullable instance type Supplier<X>/*@Identity*//*@IgnoreMods*/");
                            assertDv(d, 1, MultiLevel.INDEPENDENT_HC_DV, Property.INDEPENDENT);
                            assertDv(d, 1, MultiLevel.EFFECTIVELY_IMMUTABLE_HC_DV, Property.IMMUTABLE);
                        }
                    }
                    if ("s".equals(d.variableName())) {
                        if (isStatement0) {
                            assertCurrentValue(d, 1, "/*inline get*/supplier.get()/*{L supplier:4}*/");
                            assertLinked(d, it0("supplier:-1"), it(1, "supplier:4"));
                            // TODO not consistent with s0m, s0a
                            assertSingleLv(d, 1, 0, "*-4-0");
                            assertDv(d, 1, MultiLevel.INDEPENDENT_HC_DV, Property.INDEPENDENT);
                            assertDv(d, 0, MultiLevel.EFFECTIVELY_IMMUTABLE_HC_DV, Property.IMMUTABLE);
                        }
                    }
                }
                case "s0m" -> {
                    if ("s".equals(d.variableName())) {
                        if (isStatement0) {
                            assertCurrentValue(d, 0, "supplier::get");
                            assertLinked(d, it(0, "supplier:4"));
                            assertSingleLv(d, 0, 0, "0-4-0");
                        }
                        if (isStatement1) {
                            assertCurrentValue(d, 0, "instance 1 type Supplier<X>");
                            assertLinked(d, it(0, "supplier:4"));
                            assertSingleLv(d, 0, 0, "0-4-0");
                        }
                    }
                }
                case "s0a" -> {
                    if ("s".equals(d.variableName())) {
                        if (isStatement0) {
                            assertCurrentValue(d, 1, "new $2(){public X get(){return supplier.get();}}");
                            assertLinked(d, it0("supplier:-1"), it(1, "supplier:4"));
                            assertSingleLv(d, 1, 0, "0-4-0");
                        }
                    }
                }
                case "s1m" -> {
                    if ("s".equals(d.variableName())) {
                        if (isStatement0) {
                            assertCurrentValue(d, 0, "supplier::get");
                            assertLinked(d, it(0, 1, "supplier:-1"), it(2, "supplier:4"));
                            assertSingleLv(d, 2, 0, "0M-4-0M");
                        }
                        if (isStatement1) {
                            assertCurrentValue(d, 0, "instance 1 type Supplier<M>");
                            assertLinked(d, it(0, 1, "supplier:-1"), it(2, "supplier:4"));
                            assertSingleLv(d, 2, 0, "0M-4-0M");
                        }
                    }
                }
                case "s2m" -> {
                    if ("s".equals(d.variableName())) {
                        if (isStatement0) {
                            assertCurrentValue(d, 0, "supplier::get");
                            assertLinked(d, it(0, ""));
                        }
                        if (isStatement1) {
                            assertCurrentValue(d, 0, "instance 1 type Supplier<Integer>");
                            assertLinked(d, it(0, ""));
                        }
                    }
                }
                case "p0l" -> {
                    if (d.variable() instanceof ParameterInfo pi && "predicate".equals(pi.name)) {
                        if (isStatement0) {
                            assertCurrentValue(d, 1,
                                    "nullable instance type Predicate<X>/*@IgnoreMods*/");
                            assertDv(d, 1, MultiLevel.INDEPENDENT_HC_DV, Property.INDEPENDENT);
                            assertDv(d, 1, MultiLevel.EFFECTIVELY_IMMUTABLE_HC_DV, Property.IMMUTABLE);
                        }
                    }
                    if ("p".equals(d.variableName())) {
                        if (isStatement0) {
                            assertCurrentValue(d, 1, "/*inline test*/predicate.test(t)");
                            assertLinked(d, it0("predicate:-1"), it(1, ""));
                        }
                        if (isStatement1) {
                            assertCurrentValue(d, 1, "/*inline test*/predicate.test(t)");
                            // there is no difference between a predicate and a consumer from the point of view of linking
                            assertLinked(d, it0("predicate:-1,x:-1"), it(1, "predicate:4,x:4"));
                            assertSingleLv(d, 1, 0, "0-4-0");
                            assertSingleLv(d, 1, 1, "0-4-*");
                        }
                    }
                }
                case "p1l" -> {
                    if (d.variable() instanceof ParameterInfo pi && "predicate".equals(pi.name)) {
                        if (isStatement0) {
                            assertCurrentValue(d, 1,
                                    "nullable instance type Predicate<M>/*@IgnoreMods*/");
                            assertDv(d, 1, MultiLevel.INDEPENDENT_HC_DV, Property.INDEPENDENT);
                            assertDv(d, 1, MultiLevel.EFFECTIVELY_IMMUTABLE_HC_DV, Property.IMMUTABLE);
                        }
                    }
                    if ("p".equals(d.variableName())) {
                        if (isStatement0) {
                            assertCurrentValue(d, 1, "instance 0 type " + myAnonymous);
                            assertLinked(d, it0("predicate:-1"), it(1, ""));
                        }
                        if (isStatement1) {
                            assertCurrentValue(d, 2, "instance 0 type " + myAnonymous);
                            assertLinked(d, it0("predicate:-1,x:-1"),
                                    it(1, 1, "x:-1"),
                                    it(2, "x:4"));
                            assertSingleLv(d, 2, 0, "0M-4-*M");
                        }
                    }
                }
                case "ppc0" -> {
                    if (d.variable() instanceof ParameterInfo pi && "x".equals(pi.name)) {
                        assertCurrentValue(d, 2, "nullable instance type X/*@Identity*/");
                        assertLinked(d, it(0, 1, "predicate:-1,y:-1"), it(2, "predicate:4"));
                        assertSingleLv(d, 2, 0, "*-4-0.0");
                    }
                    if (d.variable() instanceof ParameterInfo pi && "y".equals(pi.name)) {
                        assertCurrentValue(d, 2, "nullable instance type Y");
                        assertLinked(d, it(0, 1, "predicate:-1,x:-1"), it(2, "predicate:4"));
                        assertSingleLv(d, 2, 0, "*-4-0.1");
                    }
                    if (d.variable() instanceof ParameterInfo pi && "predicate".equals(pi.name)) {
                        assertCurrentValue(d, 2, "nullable instance type Predicate<Pair<X,Y>>/*@IgnoreMods*/");
                        assertLinked(d, it(0, 1, "x:-1,y:-1"), it(2, "x:4,y:4"));
                        assertSingleLv(d, 2, 0, "0.0-4-*");
                        assertSingleLv(d, 2, 1, "0.1-4-*");
                    }
                }
                case "ppc0bis" -> {
                    if ("0".equals(d.statementId())) {
                        if (d.variable() instanceof ParameterInfo pi && "x".equals(pi.name)) {
                            assertCurrentValue(d, 2, "nullable instance type X/*@Identity*/");
                            assertLinked(d, it(0, 1, "p:-1,y:-1"), it(2, "p:4"));
                            assertSingleLv(d, 2, 0, "*-4-0");
                        }
                        if (d.variable() instanceof ParameterInfo pi && "y".equals(pi.name)) {
                            assertCurrentValue(d, 2, "nullable instance type Y");
                            assertLinked(d, it(0, 1, "p:-1,x:-1"), it(2, "p:4"));
                            assertSingleLv(d, 2, 0, "*-4-1");
                        }
                        if (d.variable() instanceof ParameterInfo pi && "predicate".equals(pi.name)) {
                            assertCurrentValue(d, 0, "nullable instance type Predicate<Pair<X,Y>>/*@IgnoreMods*/");
                            assertLinked(d, it(0, ""));
                        }
                    }
                }
                case "ppd0" -> {
                    if (d.variable() instanceof ParameterInfo pi && "x".equals(pi.name)) {
                        assertCurrentValue(d, 2, "nullable instance type X/*@Identity*/");
                        assertLinked(d, it(0, 1, "predicate:-1"), it(2, "predicate:4"));
                        assertSingleLv(d, 2, 0, "*-4-0.0");
                    }
                    if (d.variable() instanceof ParameterInfo pi && "y".equals(pi.name)) {
                        assertCurrentValue(d, 2, "nullable instance type Integer");
                        assertLinked(d, it(0, ""));
                    }
                    if (d.variable() instanceof ParameterInfo pi && "predicate".equals(pi.name)) {
                        assertCurrentValue(d, 2, "nullable instance type Predicate<Pair<X,Integer>>/*@IgnoreMods*/");
                        assertLinked(d, it(0, 1, "x:-1"), it(2, "x:4"));
                        assertSingleLv(d, 2, 0, "0.0-4-*");
                    }
                }
                case "ppe0" -> {
                    if (d.variable() instanceof ParameterInfo pi && "x".equals(pi.name)) {
                        assertCurrentValue(d, 2, "nullable instance type M/*@Identity*/");
                        assertLinked(d, it(0, 1, "predicate:-1,y:-1"), it(2, "predicate:4"));
                        assertSingleLv(d, 2, 0, "*M-4-0.0M");
                    }
                    if (d.variable() instanceof ParameterInfo pi && "y".equals(pi.name)) {
                        assertCurrentValue(d, 2, "nullable instance type N");
                        assertLinked(d, it(0, 1, "predicate:-1,x:-1"), it(2, "predicate:4"));
                        assertSingleLv(d, 2, 0, "*M-4-0.1M");
                    }
                    if (d.variable() instanceof ParameterInfo pi && "predicate".equals(pi.name)) {
                        assertCurrentValue(d, 2, "nullable instance type Predicate<Pair<M,N>>/*@IgnoreMods*/");
                        assertLinked(d, it(0, 1, "x:-1,y:-1"), it(2, "x:4,y:4"));
                        assertSingleLv(d, 2, 0, "0.0M-4-*M");
                        assertSingleLv(d, 2, 1, "0.1M-4-*M");
                    }
                }
                case "c0l" -> {
                    if ("c".equals(d.variableName())) {
                        if (isStatement0) {
                            assertCurrentValue(d, 0, "instance 0 type " + myAnonymous + "/*{L consumer:4}*/");
                            assertLinked(d, it(0, "consumer:4"));
                            assertSingleLv(d, 0, 0, "0-4-0");
                        }
                    }
                }
                case "c0m" -> {
                    if ("c".equals(d.variableName())) {
                        if (isStatement0) {
                            assertCurrentValue(d, 0, "consumer::accept");
                            assertInstanceOf(MethodReference.class, d.variableInfo().getValue());
                            assertLinked(d, it(0, "consumer:4"));
                            assertSingleLv(d, 0, 0, "0-4-0");
                        }
                    }
                }
                case "accept" -> {
                    if ("c0a".equals(d.enclosingMethod().name)) {
                        if (d.variable() instanceof ParameterInfo pi && "consumer".equals(pi.name)) {
                            assertCurrentValue(d, 0,
                                    "nullable instance type Consumer<X>/*@IgnoreMods*/");
                            assertLinked(d, it0(NOT_YET_SET_STR), it(1, "xx:4"));
                            assertSingleLv(d, 1, 0, "0-4-*");
                        }
                        if (d.variable() instanceof ParameterInfo pi && "xx".equals(pi.name)) {
                            assertCurrentValue(d, 0, "nullable instance type X/*@Identity*/");
                            assertLinked(d, it0(NOT_YET_SET_STR), it(1, "consumer:4"));
                            assertSingleLv(d, 1, 0, "*-4-0");
                        }
                    }
                }
                case "c0a" -> {
                    if ("c".equals(d.variableName())) {
                        if (isStatement0) {
                            assertCurrentValue(d, 0, "new " + myAnonymous
                                                     + "(){public void accept(X xx){consumer.accept(xx);}}");
                            assertInstanceOf(ConstructorCall.class, d.variableInfo().getValue());
                            assertLinked(d, it0(NOT_YET_SET_STR),
                                    it1("consumer:-1,this:-1,x:-1"),
                                    it(2, "consumer:4"));
                            assertSingleLv(d, 2, 0, "0-4-0");
                        }
                        if (isStatement1) {
                            assertCurrentValue(d, 2, "new " + myAnonymous
                                                     + "(){public void accept(X xx){consumer.accept(xx);}}");
                            assertLinked(d, it0(NOT_YET_SET_STR),
                                    it1("consumer:-1,this:-1,x:-1"),
                                    it(1, "consumer:4,x:4"));
                            assertSingleLv(d, 2, 0, "0-4-0");
                            assertSingleLv(d, 2, 1, "0-4-*");
                        }
                    }
                    if (d.variable() instanceof ParameterInfo pi && "x".equals(pi.name)) {
                        if (isStatement1) {
                            assertCurrentValue(d, 2, "nullable instance type X/*@Identity*/");
                            assertLinked(d, it0(NOT_YET_SET_STR),
                                    it1("c:-1,consumer:-1,this:-1"),
                                    it(1, "c:4,consumer:4"));
                            assertSingleLv(d, 2, 0, "*-4-0");
                            assertSingleLv(d, 2, 1, "*-4-0");
                        }
                    }
                    if (d.variable() instanceof ParameterInfo pi && "consumer".equals(pi.name)) {
                        if (isStatement1) {
                            assertCurrentValue(d, 2,
                                    "nullable instance type Consumer<X>/*@IgnoreMods*/");
                            assertLinked(d, it0(NOT_YET_SET_STR), it1("c:-1,this:-1,x:-1"),
                                    it(1, "c:4,x:4"));
                            assertSingleLv(d, 2, 0, "0-4-0");
                            assertSingleLv(d, 2, 1, "0-4-*");
                        }
                        if (isStatement2) {
                            assertCurrentValue(d, 2,
                                    "nullable instance type Consumer<X>/*@IgnoreMods*/");
                            assertLinked(d, it0(NOT_YET_SET_STR),
                                    it1("c:-1,this:-1,x:-1"),
                                    it(2, "c:4,x:4"));
                            assertSingleLv(d, 2, 0, "0-4-0");
                            assertSingleLv(d, 2, 1, "0-4-*");
                        }
                    }
                }
                case "c1" -> {
                    if (d.variable() instanceof ParameterInfo pi && "consumer".equals(pi.name)) {
                        if (isStatement0) {
                            assertCurrentValue(d, 2,
                                    "nullable instance type Consumer<M>/*@IgnoreMods*/");
                            assertLinked(d, it(0, 1, "m:-1"), it(2, "m:4"));
                            assertSingleLv(d, 2, 0, "0M-4-*M");
                        }
                    }
                }
                case "c1m" -> {
                    if ("c".equals(d.variableName())) {
                        if (isStatement0) {
                            assertCurrentValue(d, 0, "consumer::accept");
                            assertInstanceOf(MethodReference.class, d.variableInfo().getValue());
                            assertLinked(d, it(0, 1, "consumer:-1"), it(2, "consumer:4"));
                            assertSingleLv(d, 2, 0, "0M-4-0M");
                        }
                    }
                }
                case "c2" -> {
                    if (d.variable() instanceof ParameterInfo pi && "consumer".equals(pi.name)) {
                        if (isStatement0) {
                            assertCurrentValue(d, 0,
                                    "nullable instance type Consumer<Integer>/*@IgnoreMods*/");
                            assertLinked(d, it(0, ""));
                        }
                    }
                }
                case "c2m" -> {
                    if (d.variable() instanceof ParameterInfo pi && "consumer".equals(pi.name)) {
                        if (isStatement0) {
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
                    if ("f".equals(d.variableName())) {
                        if (isStatement0) {
                            assertCurrentValue(d, 0, "function::apply");
                            assertLinked(d, it(0, "function:4"));
                            assertSingleLv(d, 0, 0, "0,1-4-0,1");
                        }
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
                case "f9m" -> {
                    if ("f".equals(d.variableName())) {
                        if (isStatement0) {
                            assertCurrentValue(d, 0, "function::apply");
                            assertLinked(d, it(0, "function:4"));
                            assertSingleLv(d, 0, 0, "0;1-4-0;1");
                        }
                    }
                }
                case "f10" -> {
                    // List<T>, T
                    if (vIsFunction) {
                        assertCurrentValue(d, 0, "nullable instance type Function<List<T>,T>/*@IgnoreMods*/");
                        assertLinked(d, it(0, "ts:4"));
                        assertSingleLv(d, 0, 0, "0.0-4-0");
                    }
                }
                case "f11" -> {
                    // T, List<T>
                    if (vIsFunction) {
                        assertCurrentValue(d, 0, "nullable instance type Function<T,List<T>>/*@IgnoreMods*/");
                        //    assertLinked(d, it(0, "t:4"));
                        //     assertSingleLv(d, 0, 0, "0-4-*");
                    }
                }
                case "f12" -> {
                    // List<T>, Set<T>
                    if (vIsFunction) {
                        assertCurrentValue(d, 0, "nullable instance type Function<List<T>,Set<T>>/*@IgnoreMods*/");
                        //   assertLinked(d, it(0, "ts:4"));
                        //   assertSingleLv(d, 0, 0, "0-4-0");
                    }
                }
            }
        };

        MethodAnalyserVisitor methodAnalyserVisitor = d -> {
            HiddenContentTypes hct = d.methodInfo().methodResolution.get().hiddenContentTypes();
            String enclosingMethod = d.enclosingMethod() != null ? d.enclosingMethod().name : "";
            if ("get".equals(d.methodInfo().name) && "s0l".equals(enclosingMethod)) {
                // () -> supplier.get() ~ public X get() { return supplier.get(); }
                assertDv(d, MultiLevel.EFFECTIVELY_IMMUTABLE_HC_DV, Property.IMMUTABLE);
                if (d.methodAnalysis().getHiddenContentSelector() instanceof HiddenContentSelector.All all) {
                    assertEquals(0, all.getHiddenContentIndex());
                } else fail();
                LinkedVariables lvs = d.methodAnalysis().getLinkedVariables();
                assertLinked(d, lvs, it0("supplier:-1"), it(1, "supplier:4"));
                assertSingleLv(d, lvs, 1, 0, "*-4-0");
            }
            if ("get".equals(d.methodInfo().name) && "s0a".equals(enclosingMethod)) {
                assertEquals("$2:X - get:", hct.toString());
                assertDv(d, 1, MultiLevel.INDEPENDENT_HC_DV, Property.INDEPENDENT);
                if (d.methodAnalysis().getHiddenContentSelector() instanceof HiddenContentSelector.All all) {
                    assertEquals(0, all.getHiddenContentIndex());
                } else fail();
                LinkedVariables lvs = d.methodAnalysis().getLinkedVariables();
                assertLinked(d, lvs, it0("supplier:-1"), it(0, "supplier:4"));
                assertSingleLv(d, lvs, 1, 0, "*-4-0");
            }
            if ("test".equals(d.methodInfo().name) && "p0l".equals(enclosingMethod)) {
                String expected = d.iteration() == 0 ? "<m:test>" : "/*inline test*/predicate.test(t)";
                assertEquals(expected, d.methodAnalysis().getSingleReturnValue().toString());
                // result is a boolean, so 'none'
                assertEquals("X - ", hct.sortedTypes());
                assertFalse(hct.isEmpty());
                assertEquals(1, hct.size());
                assertTrue(d.methodAnalysis().getHiddenContentSelector().isNone());
            }
            if ("test".equals(d.methodInfo().name) && "p1l".equals(enclosingMethod)) {
                String expected = d.iteration() == 0 ? "<m:test>" : "predicate.test(t)";
                assertEquals(expected, d.methodAnalysis().getSingleReturnValue().toString());
                // result is a boolean, so 'none'
                assertEquals(" - ", hct.sortedTypes());
                assertTrue(d.methodAnalysis().getHiddenContentSelector().isNone());
            }
            if ("accept".equals(d.methodInfo().name) && "c0a".equals(enclosingMethod)) {
                assertEquals("X - ", hct.sortedTypes());
                assertDv(d, 0, MultiLevel.INDEPENDENT_DV, Property.INDEPENDENT);
                assertTrue(d.methodAnalysis().getHiddenContentSelector().isNone());
                LinkedVariables lvs = d.methodAnalysis().getLinkedVariables();
                assertLinked(d, lvs, it(0, ""));

                ParameterAnalysis pa = d.methodAnalysis().getParameterAnalyses().get(0);
                if (pa.getHiddenContentSelector() instanceof HiddenContentSelector.All all) {
                    assertEquals(0, all.getHiddenContentIndex());
                    assertEquals("java.util.function.Consumer.accept(T)",
                            all.hiddenContentTypes().getMethodInfo().fullyQualifiedName);
                } else fail();
                assertLinked(d, pa.getLinkedVariables(), it0("NOT_YET_SET"),
                        it1("consumer:-1,this:-1,x:-1"),
                        it(2, "consumer:4"));
                assertSingleLv(d, pa.getLinkedVariables(), 2, 0, "0-4-*");
                assertDv(d.p(0), 2, MultiLevel.INDEPENDENT_HC_DV, Property.INDEPENDENT);
            }
            if ("accept".equals(d.methodInfo().name) && "c1a".equals(enclosingMethod)) {
                assertEquals(" - ", hct.sortedTypes());
                assertDv(d, 0, MultiLevel.INDEPENDENT_DV, Property.INDEPENDENT);
                assertTrue(d.methodAnalysis().getHiddenContentSelector().isNone());
                LinkedVariables lvs = d.methodAnalysis().getLinkedVariables();
                assertLinked(d, lvs, it(0, ""));

                ParameterAnalysis pa = d.methodAnalysis().getParameterAnalyses().get(0);
                if (pa.getHiddenContentSelector() instanceof HiddenContentSelector.All all) {
                    assertEquals(0, all.getHiddenContentIndex());
                    assertEquals("java.util.function.Consumer.accept(T)",
                            all.hiddenContentTypes().getMethodInfo().fullyQualifiedName);
                } else fail();
                assertLinked(d, pa.getLinkedVariables(), it0("NOT_YET_SET"),
                        it1("consumer:-1,m:-1,this:-1"),
                        it(2, 2, "consumer:-1"),
                        it(3, "consumer:4"));
                assertSingleLv(d, pa.getLinkedVariables(), 3, 0, "0M-4-*M");
                assertDv(d.p(0), 3, MultiLevel.INDEPENDENT_HC_DV, Property.INDEPENDENT);
            }
            if ("f10".equals(d.methodInfo().name)) {
                assertEquals("Linking_1A: - f10:T", hct.toString());
            }
        };

        TypeAnalyserVisitor typeAnalyserVisitor = d -> {
            MethodInfo enclosingMethod = d.typeInspection().enclosingMethod();
            boolean isAnonymous = d.typeInfo().packageNameOrEnclosingType.isRight() && enclosingMethod != null;
            if (isAnonymous) {
                // helps debugging when the numbers of the anonymous types change when commenting out a method
                anonymousOfEnclosing.put(enclosingMethod.name, d.typeInfo().simpleName);
            }
            if (isAnonymous && "p0l".equals(enclosingMethod.name)) {
                assertEquals("X", d.typeInfo().typeResolution.get().hiddenContentTypes().sortedTypes());
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
