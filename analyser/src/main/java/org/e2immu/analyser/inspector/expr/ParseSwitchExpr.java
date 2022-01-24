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

package org.e2immu.analyser.inspector.expr;

import com.github.javaparser.ast.expr.SwitchExpr;
import org.e2immu.analyser.inspector.ExpressionContext;
import org.e2immu.analyser.inspector.ForwardReturnTypeInfo;
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.model.expression.SwitchExpression;
import org.e2immu.analyser.model.expression.util.MultiExpression;
import org.e2immu.analyser.model.statement.ExpressionAsStatement;
import org.e2immu.analyser.model.statement.Structure;
import org.e2immu.analyser.model.statement.SwitchEntry;
import org.e2immu.analyser.model.statement.YieldStatement;
import org.e2immu.analyser.model.variable.FieldReference;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class ParseSwitchExpr {

    public static Expression parse(ExpressionContext expressionContext,
                                   SwitchExpr switchExpr,
                                   ForwardReturnTypeInfo forwardReturnTypeInfo) {
        Expression selector = expressionContext.parseExpressionStartVoid(switchExpr.getSelector());
        ExpressionContext newExpressionContext = expressionContext.newSwitchExpressionContext(forwardReturnTypeInfo);
        TypeInfo enumType = expressionContext.selectorIsEnumType(selector);
        if (enumType != null) {
            TypeInspection enumInspection = expressionContext.typeContext().getTypeInspection(enumType);
            enumInspection.fields()
                    .forEach(fieldInfo -> newExpressionContext.variableContext().add(
                            new FieldReference(expressionContext.typeContext(), fieldInfo)));
        }
        // decide between keeping a switch expression, where each of the cases is an expression,
        // and transforming the switch expression in a lambda/implementation of Function<>
        // where each case block is an if statement, with the yield replaced by return.
        
        List<SwitchEntry> entries = switchExpr.getEntries()
                .stream()
                .map(entry -> newExpressionContext.switchEntry(selector, entry))
                .collect(Collectors.toList());
        // we'll need to deduce the return type from the entries.
        // if the entry is a StatementEntry, it must be either a throws, or an expression as statement
        // in the latter case, we can grab a value.
        // if the entry is a BlockEntry, we must look at the yield statement
        MultiExpression yieldExpressions = new MultiExpression(extractYieldsFromEntries(entries));
        ParameterizedType parameterizedType = yieldExpressions.commonType(expressionContext.typeContext());
        return new SwitchExpression(Identifier.from(switchExpr),
                selector, entries, parameterizedType, yieldExpressions);
    }

    private static Expression[] extractYieldsFromEntries(List<SwitchEntry> entries) {
        return entries.stream().flatMap(e -> extractYields(e).stream()).toArray(Expression[]::new);
    }

    public static List<Expression> extractYields(SwitchEntry e) {
        Structure structure = e.structure;
        while(structure.statements() == null && structure.block() != null) {
           structure = structure.block().structure;
        }
        List<Statement> statements = structure.statements();
        if (statements.size() == 1) {
            Statement statement = statements.get(0);
            if (statement instanceof ExpressionAsStatement eas) {
                return List.of(eas.expression);
            }
            // in all other cases, the yield statement is required
        }
        return statements.stream().flatMap(statement -> extractYields(statement).stream()).toList();
    }

    private static List<Expression> extractYields(Statement statement) {
        List<Expression> yields = new ArrayList<>();
        statement.visit(e -> yields.add(e.expression), YieldStatement.class);
        return yields;
    }
}
