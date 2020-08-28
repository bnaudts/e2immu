/*
 * e2immu-analyser: code analyser for effective and eventual immutability
 * Copyright 2020, Bart Naudts, https://www.e2immu.org
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 */

package org.e2immu.analyser.parser.expr;

import com.github.javaparser.ast.body.Parameter;
import com.github.javaparser.ast.expr.LambdaExpr;
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.model.expression.LambdaBlock;
import org.e2immu.analyser.model.expression.LambdaExpression;
import org.e2immu.analyser.model.expression.UnevaluatedLambdaExpression;
import org.e2immu.analyser.model.expression.UnevaluatedMethodCall;
import org.e2immu.analyser.model.statement.Block;
import org.e2immu.analyser.parser.ExpressionContext;
import org.e2immu.analyser.parser.Primitives;
import org.e2immu.analyser.parser.TypeContext;
import org.e2immu.analyser.parser.VariableContext;
import org.e2immu.annotation.NotNull;

import java.util.*;

import static org.e2immu.analyser.util.Logger.LogTarget.LAMBDA;
import static org.e2immu.analyser.util.Logger.log;

public class ParseLambdaExpr {

    public static Expression parse(ExpressionContext expressionContext, LambdaExpr lambdaExpr, MethodTypeParameterMap singleAbstractMethod) {
        if (singleAbstractMethod == null || !singleAbstractMethod.isSingleAbstractMethod()) {
            return partiallyParse(lambdaExpr);
        }
        log(LAMBDA, "Start parsing lambda {}, single abstract method context {}", lambdaExpr, singleAbstractMethod);

        List<ParameterInfo> parameters = new ArrayList<>();
        VariableContext newVariableContext = VariableContext.dependentVariableContext(expressionContext.variableContext);
        int cnt = 0;
        boolean allDefined = true;
        List<ParameterizedType> types = new ArrayList<>();

        MethodInfo owner = createAnonymousTypeAndApplyMethod(expressionContext.enclosingType);

        for (Parameter parameter : lambdaExpr.getParameters()) {
            ParameterizedType parameterType = null;
            if (parameter.getType() != null && !parameter.getType().asString().isEmpty()) {
                parameterType = ParameterizedType.from(expressionContext.typeContext, parameter.getType());
            }
            if (parameterType == null) {
                // the type hint is an interface with exactly one abstract method (not "default", not static)
                parameterType = singleAbstractMethod.getConcreteTypeOfParameter(cnt);
            }
            // cases
            if (parameterType == null) {
                allDefined = false;
                break;
            }
            types.add(parameterType);
            ParameterInfo parameterInfo = new ParameterInfo(owner, parameterType, parameter.getName().asString(), cnt++);
            parameterInfo.parameterInspection.set(new ParameterInspection.ParameterInspectionBuilder().build());
            parameterInfo.parameterAnalysis.set(new ParameterAnalysis(parameterInfo));
            parameters.add(parameterInfo);
            newVariableContext.add(parameterInfo);
        }
        if (!allDefined) {
            log(LAMBDA, "End parsing lambda, delaying, not all parameters are defined yet");
            throw new UnsupportedOperationException("Not all parameters defined??");
        }
        ExpressionContext newExpressionContext = expressionContext.newVariableContext(newVariableContext, "lambda");

        boolean isExpression = lambdaExpr.getExpressionBody().isPresent();
        if (isExpression) {
            Expression expr = newExpressionContext.parseExpression(lambdaExpr.getExpressionBody());
            if (expr instanceof UnevaluatedMethodCall) {
                log(LAMBDA, "Body results in unevaluated method call, so I can't be evaluated either");
                return partiallyParse(lambdaExpr);
            }
            ParameterizedType inferredReturnType = expr.returnType();
            ParameterizedType functionalType = singleAbstractMethod.inferFunctionalType(types, inferredReturnType);
            ParameterizedType anonymousType = createAnonymousType(expressionContext.enclosingType, owner, parameters, functionalType);
            log(LAMBDA, "End parsing lambda as expression, inferred functional type {}", functionalType);
            return new LambdaExpression(parameters, expr, functionalType, anonymousType);
        }
        Block block = newExpressionContext.parseBlockOrStatement(lambdaExpr.getBody());
        ParameterizedType inferredReturnType = block.mostSpecificReturnType();
        ParameterizedType functionalType = singleAbstractMethod.inferFunctionalType(types, inferredReturnType);
        ParameterizedType anonymousType = createAnonymousType(expressionContext.enclosingType, owner, parameters, functionalType);
        log(LAMBDA, "End parsing lambda as block, inferred functional type {}", functionalType);
        return new LambdaBlock(parameters, block, functionalType, anonymousType);
    }

    // experimental: we look at the parameters, and return an expression which is superficial, with only
    // the return type as functional type of importance
    private static Expression partiallyParse(LambdaExpr lambdaExpr) {
        return new UnevaluatedLambdaExpression(Set.of(lambdaExpr.getParameters().size()), lambdaExpr.getExpressionBody().isPresent() ? true : null);
    }

    private static MethodInfo createAnonymousTypeAndApplyMethod(TypeInfo enclosingType) {
        TypeInfo typeInfo = new TypeInfo("LambdaBlock_" + enclosingType.nextIdentifier());
        TypeAnalysis typeAnalysis = new TypeAnalysis(typeInfo);
        typeInfo.typeAnalysis.set(typeAnalysis);
        return new MethodInfo(typeInfo, "apply", false);
    }

    private static ParameterizedType createAnonymousType(TypeInfo enclosingType,
                                                         MethodInfo methodInfo,
                                                         List<ParameterInfo> parameters,
                                                         ParameterizedType returnType) {
        MethodInspection.MethodInspectionBuilder methodInspectionBuilder = new MethodInspection.MethodInspectionBuilder();
        methodInspectionBuilder.setReturnType(Objects.requireNonNull(returnType));
        methodInspectionBuilder.addParameters(parameters);
        methodInfo.methodInspection.set(methodInspectionBuilder.build(methodInfo));
        MethodAnalysis methodAnalysis = MethodAnalysis.newMethodAnalysisForLambdaBlocks(methodInfo);
        methodInfo.methodAnalysis.set(methodAnalysis);

        TypeInspection.TypeInspectionBuilder typeInspectionBuilder = new TypeInspection.TypeInspectionBuilder();
        typeInspectionBuilder.setTypeNature(TypeNature.INTERFACE);
        typeInspectionBuilder.setEnclosingType(enclosingType);
        typeInspectionBuilder.addAnnotation(Primitives.PRIMITIVES.functionalInterfaceAnnotationExpression);
        typeInspectionBuilder.addMethod(methodInfo);

        TypeInfo typeInfo = methodInfo.typeInfo;
        typeInfo.typeInspection.set(typeInspectionBuilder.build(false, typeInfo));
        typeInfo.typeAnalysis.get().supportDataTypes.set(Set.of());
        // TODO this is the absolute minimum to recognize the type + method as functional interface. More needs to be done
        return typeInfo.asParameterizedType();
    }
}