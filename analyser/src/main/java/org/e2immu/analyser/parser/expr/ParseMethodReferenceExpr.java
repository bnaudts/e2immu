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

import com.github.javaparser.ast.expr.MethodReferenceExpr;
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.model.expression.*;
import org.e2immu.analyser.parser.ExpressionContext;
import org.e2immu.analyser.parser.Primitives;
import org.e2immu.analyser.parser.TypeContext;

import java.util.*;

import static org.e2immu.analyser.util.Logger.LogTarget.METHOD_CALL;
import static org.e2immu.analyser.util.Logger.isLogEnabled;
import static org.e2immu.analyser.util.Logger.log;

public class ParseMethodReferenceExpr {

    public static Expression parse(ExpressionContext expressionContext, MethodReferenceExpr methodReferenceExpr, MethodTypeParameterMap singleAbstractMethod) {
        if (singleAbstractMethod == null || !singleAbstractMethod.isSingleAbstractMethod()) {
            log(METHOD_CALL, "Start parsing unevaluated method reference {}", methodReferenceExpr);
            return unevaluated(expressionContext, methodReferenceExpr);
        }
        log(METHOD_CALL, "Start parsing method reference {}", methodReferenceExpr);

        Expression scope = expressionContext.parseExpression(methodReferenceExpr.getScope());
        boolean scopeIsAType = scopeIsAType(scope);
        ParameterizedType parameterizedType = scope.returnType();
        String methodName = methodReferenceExpr.getIdentifier();
        boolean constructor = "new".equals(methodName);

        int parametersPresented = singleAbstractMethod.methodInfo.methodInspection.get().parameters.size();
        List<TypeContext.MethodCandidate> methodCandidates;
        String methodNameForErrorReporting;
        if (constructor) {
            if (parameterizedType.arrays > 0) {
                return arrayConstruction(expressionContext, parameterizedType);
            }
            methodNameForErrorReporting = "constructor";
            methodCandidates = expressionContext.typeContext.resolveConstructor(parameterizedType, parametersPresented, parameterizedType.initialTypeParameterMap());
        } else {
            methodCandidates = new ArrayList<>();
            methodNameForErrorReporting = "method " + methodName;

            // the following examples say that you should look for a method with identical number of parameters:
            // e.g. Function<T, R> has R apply(T t), and we present C::new (where C has a constructor C(String s) { .. }
            // e.g. Consumer<T> has void accept(T t), and we present System.out::println
            // e.g. Functional interface LogMethod:  void log(LogTarget logTarget, String message, Object... objects), we present Logger::logViaLogBackClassic

            // but this example says you need to subtract:
            // e.g. Function<T, R> has R apply(T t), and we present Object::toString (the scope is the first argument)
            expressionContext.typeContext.recursivelyResolveOverloadedMethods(parameterizedType,
                    methodName, parametersPresented, scopeIsAType, parameterizedType.initialTypeParameterMap(), methodCandidates);
        }
        if (methodCandidates.isEmpty()) {
            throw new UnsupportedOperationException("Cannot find a candidate for " + methodNameForErrorReporting + " at " + methodReferenceExpr.getBegin());
        }
        if (methodCandidates.size() > 1) {
            Primitives primitives = expressionContext.typeContext.getPrimitives();
            // check types of parameters in SAM
            // see if the method candidate's type fits the SAMs
            for (int i = 0; i < singleAbstractMethod.methodInfo.methodInspection.get().parameters.size(); i++) {
                final int index = i;
                ParameterizedType concreteType = singleAbstractMethod.getConcreteTypeOfParameter(i);
                log(METHOD_CALL, "Have {} candidates, try to weed out based on compatibility of {} with parameter {}",
                        methodCandidates.size(), concreteType.detailedString(), i);
                List<TypeContext.MethodCandidate> copy = new LinkedList<>(methodCandidates);
                copy.removeIf(mc -> {
                    ParameterizedType typeOfMethodCandidate = typeOfMethodCandidate(mc, index, scopeIsAType, constructor);
                    boolean isAssignable = typeOfMethodCandidate.isAssignableFrom(primitives, concreteType);
                    return !isAssignable;
                });
                // only accept of this is an improvement
                // there are situations where this method kills all, as the concrete type
                // can be a type parameter while the method candidates only have concrete types
                if (copy.size() > 0 && copy.size() < methodCandidates.size()) {
                    methodCandidates.retainAll(copy);
                }
                // sort on assignability to parameter "index"
                methodCandidates.sort((mc1, mc2) -> {
                    ParameterizedType typeOfMc1 = typeOfMethodCandidate(mc1, index, scopeIsAType, constructor);
                    ParameterizedType typeOfMc2 = typeOfMethodCandidate(mc2, index, scopeIsAType, constructor);
                    if (typeOfMc1.equals(typeOfMc2)) return 0;
                    return typeOfMc2.isAssignableFrom(primitives, typeOfMc1) ? -1 : 1;
                });
            }
            if (methodCandidates.size() > 1) {
                log(METHOD_CALL, "Trying to weed out those of the same type, static vs instance");
                staticVsInstance(methodCandidates);
                if (methodCandidates.size() > 1) {
                    if (isLogEnabled(METHOD_CALL)) {
                        log(METHOD_CALL, "Still have {}", methodCandidates.size());
                        methodCandidates.forEach(mc -> log(METHOD_CALL, "- {}", mc.method.methodInfo.distinguishingName()));
                    }
                    // method candidates have been sorted; the first one should be the one we're after and others should be
                    // higher in the hierarchy (interfaces, parent classes)
                }
            }
        }
        if (methodCandidates.isEmpty()) {
            throw new UnsupportedOperationException("I've killed all the candidates myself??");
        }
        MethodTypeParameterMap method = methodCandidates.get(0).method;
        List<ParameterizedType> types = new ArrayList<>();
        types.add(parameterizedType);
        method.methodInfo.methodInspection.get().parameters.stream().map(p -> p.parameterizedType).forEach(types::add);
        ParameterizedType functionalType = singleAbstractMethod.inferFunctionalType(types, method.getConcreteReturnType());
        log(METHOD_CALL, "End parsing method reference {}, found {}", methodNameForErrorReporting, method.methodInfo.distinguishingName());
        return new MethodReference(scope, method.methodInfo, functionalType);
    }

    private static ParameterizedType typeOfMethodCandidate(TypeContext.MethodCandidate mc, int index, boolean scopeIsAType, boolean constructor) {
        int param = scopeIsAType && !constructor && !mc.method.methodInfo.isStatic ? index - 1 : index;
        if (param == -1) {
            return mc.method.methodInfo.typeInfo.asParameterizedType();
        } else {
            return mc.method.methodInfo.methodInspection.get().parameters.get(index).parameterizedType;
        }
    }

    private static void staticVsInstance(List<TypeContext.MethodCandidate> methodCandidates) {
        Set<TypeInfo> haveInstance = new HashSet<>();
        methodCandidates.stream().filter(mc -> !mc.method.methodInfo.isStatic).forEach(mc -> haveInstance.add(mc.method.methodInfo.typeInfo));
        methodCandidates.removeIf(mc -> mc.method.methodInfo.isStatic && haveInstance.contains(mc.method.methodInfo.typeInfo));
    }

    private static Expression unevaluated(ExpressionContext expressionContext, MethodReferenceExpr methodReferenceExpr) {
        Expression scope = expressionContext.parseExpression(methodReferenceExpr.getScope());
        ParameterizedType parameterizedType = scope.returnType();
        String methodName = methodReferenceExpr.getIdentifier();
        boolean constructor = "new".equals(methodName);

        List<TypeContext.MethodCandidate> methodCandidates;
        if (constructor) {
            if (parameterizedType.arrays > 0) {
                return arrayConstruction(expressionContext, parameterizedType);
            }
            methodCandidates = expressionContext.typeContext.resolveConstructor(parameterizedType, TypeContext.IGNORE_PARAMETER_NUMBERS, parameterizedType.initialTypeParameterMap());
        } else {
            methodCandidates = new ArrayList<>();
            expressionContext.typeContext.recursivelyResolveOverloadedMethods(parameterizedType,
                    methodName, TypeContext.IGNORE_PARAMETER_NUMBERS, false, parameterizedType.initialTypeParameterMap(), methodCandidates);
        }
        if (methodCandidates.isEmpty()) {
            throw new UnsupportedOperationException("Cannot find a candidate for " + (constructor ? "constructor" : methodName) + " at " + methodReferenceExpr.getBegin());
        }
        Set<Integer> numberOfParameters = new HashSet<>();
        for (TypeContext.MethodCandidate methodCandidate : methodCandidates) {
            log(METHOD_CALL, "Found method reference candidate, this can work: {}", methodCandidate.method.methodInfo.distinguishingName());
            MethodInspection methodInspection = methodCandidate.method.methodInfo.methodInspection.get();
            boolean scopeIsType = scopeIsAType(scope);
            boolean addOne = scopeIsType && !methodCandidate.method.methodInfo.isConstructor && !methodCandidate.method.methodInfo.isStatic; // && !methodInspection.returnType.isPrimitive();
            int n = methodInspection.parameters.size() + (addOne ? 1 : 0);
            if (!numberOfParameters.add(n)) {
                // throw new UnsupportedOperationException("Multiple candidates with the same amount of parameters");
            }
        }
        log(METHOD_CALL, "End parsing unevaluated method reference {}, found parameter set {}", methodReferenceExpr, numberOfParameters);
        return new UnevaluatedLambdaExpression(numberOfParameters, null);
    }

    private static MethodReference arrayConstruction(ExpressionContext expressionContext, ParameterizedType parameterizedType) {
        MethodInfo arrayConstructor = ParseArrayCreationExpr.createArrayCreationConstructor(expressionContext.typeContext, parameterizedType);
        TypeInfo intFunction = expressionContext.typeContext.typeStore.get("java.util.function.IntFunction");
        if (intFunction == null) throw new UnsupportedOperationException("? need IntFunction");
        ParameterizedType intFunctionPt = new ParameterizedType(intFunction, List.of(parameterizedType));
        return new MethodReference(new TypeExpression(parameterizedType), arrayConstructor, intFunctionPt);
    }

    public static boolean scopeIsAType(Expression scope) {
        return !(scope instanceof VariableExpression) && !(scope instanceof FieldAccess);
    }
}
