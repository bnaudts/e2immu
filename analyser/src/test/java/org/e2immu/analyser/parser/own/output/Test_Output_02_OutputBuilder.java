
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

package org.e2immu.analyser.parser.own.output;

import org.e2immu.analyser.analyser.DV;
import org.e2immu.analyser.analyser.Property;
import org.e2immu.analyser.analyser.VariableInfoContainer;
import org.e2immu.analyser.config.DebugConfiguration;
import org.e2immu.analyser.model.expression.InlinedMethod;
import org.e2immu.analyser.model.variable.FieldReference;
import org.e2immu.analyser.model.variable.ReturnVariable;
import org.e2immu.analyser.output.*;
import org.e2immu.analyser.parser.CommonTestRunner;
import org.e2immu.analyser.visitor.EvaluationResultVisitor;
import org.e2immu.analyser.visitor.MethodAnalyserVisitor;
import org.e2immu.analyser.visitor.StatementAnalyserVariableVisitor;
import org.e2immu.analyser.visitor.StatementAnalyserVisitor;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class Test_Output_02_OutputBuilder extends CommonTestRunner {

    public Test_Output_02_OutputBuilder() {
        super(true);
    }

    @Test
    public void test() throws IOException {
        StatementAnalyserVisitor statementAnalyserVisitor = d -> {
            if ("apply".equals(d.methodInfo().name) && "$4".equals(d.methodInfo().typeInfo.simpleName)) {
                if ("2.0.0".equals(d.statementId())) { // a.add(separator); add is fluent; the identity is there because "a" is the first parameter of apply
                    String expected = d.iteration() == 0 ? "<m:add>" : "nullable instance type OutputBuilder/*@Identity*//*{L a:statically_assigned:0}*/";
                    assertEquals(expected, d.statementAnalysis().stateData().valueOfExpression.get().toString());
                }
            }
        };
        MethodAnalyserVisitor methodAnalyserVisitor = d -> {
            if ("add".equals(d.methodInfo().name)) {
                String typeOfParameter = d.methodInfo().methodInspection.get().getParameters().get(0).parameterizedType.typeInfo.simpleName;
                if("OutputBuilder".equals(typeOfParameter)) {
                    assertDv(d, DV.TRUE_DV, Property.MODIFIED_METHOD);
                } else if("OutputElement".equals(typeOfParameter)) {
                    assertDv(d, DV.TRUE_DV, Property.MODIFIED_METHOD);
                } else fail();
            }
        };
        testSupportAndUtilClasses(List.of(OutputBuilder.class, OutputElement.class, Qualifier.class,
                        FormattingOptions.class, Guide.class, Space.class, TypeName.class),
                4, 4, new DebugConfiguration.Builder()
                     //   .addStatementAnalyserVisitor(statementAnalyserVisitor)
                     //   .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                        .build());
    }

    @Test
    public void testTypeName() throws IOException {
        EvaluationResultVisitor evaluationResultVisitor = d -> {
            if ("write".equals(d.methodInfo().name)) {
                assertEquals("0", d.statementId());
                if (d.iteration() >= 4) {
                    // return variable, this, 4 fields present in the inlined method
                    assertEquals(6, d.evaluationResult().changeData().size());
                }
            }
        };
        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if ("write".equals(d.methodInfo().name)) {
                if (d.variable() instanceof ReturnVariable) {
                    String expected = d.iteration() <= 3 ? "<m:minimal>"
                            : "switch(required){Required.SIMPLE->simpleName;Required.FQN->fullyQualifiedName;Required.QUALIFIED_FROM_PRIMARY_TYPE->fromPrimaryTypeDownwards;}";
                    //   assertEquals(expected, d.currentValue().toString());
                }
            }
        };
        StatementAnalyserVisitor statementAnalyserVisitor = d -> {
            if ("write".equals(d.methodInfo().name)) {
                if (d.iteration() >= 4) {
                    VariableInfoContainer vic = d.statementAnalysis().getVariable("org.e2immu.analyser.output.TypeName.simpleName");

                }
            }
        };
        MethodAnalyserVisitor methodAnalyserVisitor = d -> {
            if ("minimal".equals(d.methodInfo().name)) {
                String expected = d.iteration() <= 3 ? "<m:minimal>"
                        : "/*inline minimal*/switch(required){Required.SIMPLE->simpleName;Required.FQN->fullyQualifiedName;Required.QUALIFIED_FROM_PRIMARY_TYPE->fromPrimaryTypeDownwards;}";
                assertEquals(expected, d.methodAnalysis().getSingleReturnValue().toString());
                if (d.iteration() >= 4) {
                    if (d.methodAnalysis().getSingleReturnValue() instanceof InlinedMethod inlinedMethod) {
                        assertEquals(5, inlinedMethod.getVariablesOfExpression().size());
                    } else fail("Have " + d.methodAnalysis().getSingleReturnValue().getClass());
                }
            }
        };
        testSupportAndUtilClasses(List.of(FormattingOptions.class, TypeName.class),
                2, 1, new DebugConfiguration.Builder()
                        .addEvaluationResultVisitor(evaluationResultVisitor)
                        .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                        .addStatementAnalyserVisitor(statementAnalyserVisitor)
                        .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                        .build());
    }

}
