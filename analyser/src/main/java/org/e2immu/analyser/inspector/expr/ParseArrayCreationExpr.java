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

import com.github.javaparser.ast.expr.ArrayCreationExpr;
import org.e2immu.analyser.analyser.AnalysisProvider;
import org.e2immu.analyser.analysis.Analysis;
import org.e2immu.analyser.analysis.MethodAnalysis;
import org.e2immu.analyser.analysis.ParameterAnalysis;
import org.e2immu.analyser.analysis.impl.MethodAnalysisImpl;
import org.e2immu.analyser.analysis.impl.ParameterAnalysisImpl;
import org.e2immu.analyser.inspector.*;
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.model.expression.ArrayInitializer;
import org.e2immu.analyser.model.expression.ConstructorCall;
import org.e2immu.analyser.model.expression.IntConstant;
import org.e2immu.analyser.model.statement.Block;
import org.e2immu.analyser.parser.InspectionProvider;
import org.e2immu.analyser.parser.Primitives;

import java.util.List;
import java.util.stream.Collectors;

public class ParseArrayCreationExpr {
    public static Expression parse(ExpressionContext expressionContext, ArrayCreationExpr arrayCreationExpr) {
        TypeContext typeContext = expressionContext.typeContext();
        ParameterizedType parameterizedType = ParameterizedTypeFactory.from(typeContext, arrayCreationExpr.createdType());
        ArrayInitializer arrayInitializer = arrayCreationExpr.getInitializer().map(i ->
                new ArrayInitializer(typeContext,
                        i.getValues().stream()
                                .map(expressionContext::parseExpressionStartVoid).collect(Collectors.toList()),
                        parameterizedType.copyWithOneFewerArrays())).orElse(null);
        List<Expression> indexExpressions = arrayCreationExpr.getLevels()
                .stream().map(level -> level.getDimension().map(expressionContext::parseExpressionStartVoid)
                        .orElse(new IntConstant(typeContext.getPrimitives(), 0))).collect(Collectors.toList());
        return ConstructorCall.withArrayInitialiser(
                createArrayCreationConstructor(typeContext, parameterizedType),
                parameterizedType, indexExpressions, arrayInitializer);
    }

    // new Type[3]; this method creates the constructor that makes this array, without attaching said constructor to the type
    static MethodInfo createArrayCreationConstructor(TypeContext typeContext, ParameterizedType parameterizedType) {
        MethodInspectionImpl.Builder builder = new MethodInspectionImpl.Builder(parameterizedType.typeInfo)
                .setInspectedBlock(Block.emptyBlock(Identifier.generate()))
                .setReturnType(parameterizedType)
                .addModifier(MethodModifier.PUBLIC);
        for (int i = 0; i < parameterizedType.arrays; i++) {
            ParameterInspectionImpl.Builder p = new ParameterInspectionImpl.Builder(Identifier.generate(),
                    typeContext.getPrimitives().intParameterizedType(), "dim" + i, i);
            builder.addParameter(p);
        }
        MethodInfo constructor = builder.build(typeContext).getMethodInfo();
        constructor.setAnalysis(createEmpty(constructor, typeContext.getPrimitives()));
        constructor.methodResolution.set(new MethodResolution.Builder().build());
        return constructor;
    }

    public static MethodAnalysis createEmpty(MethodInfo methodInfo, Primitives primitives) {
        List<ParameterAnalysis> parameterAnalyses = methodInfo.methodInspection.get().getParameters().stream()
                .map(p -> (ParameterAnalysis) new ParameterAnalysisImpl.Builder(primitives, AnalysisProvider.DEFAULT_PROVIDER, p).build())
                .collect(Collectors.toList());
        MethodAnalysisImpl.Builder builder = new MethodAnalysisImpl.Builder(Analysis.AnalysisMode.CONTRACTED,
                primitives, AnalysisProvider.DEFAULT_PROVIDER, InspectionProvider.DEFAULT,
                methodInfo, parameterAnalyses);
        return (MethodAnalysis) builder.build();
    }
}
