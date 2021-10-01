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

package org.e2immu.analyser.analyser;

import org.e2immu.analyser.model.*;
import org.e2immu.analyser.parser.E2ImmuAnnotationExpressions;
import org.e2immu.analyser.parser.Message;
import org.e2immu.analyser.parser.Primitives;
import org.e2immu.analyser.util.SMapList;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.IntSupplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class ShallowMethodAnalyser extends MethodAnalyser {

    public ShallowMethodAnalyser(MethodInfo methodInfo,
                                 MethodAnalysisImpl.Builder methodAnalysis,
                                 List<ParameterAnalysis> parameterAnalyses,
                                 AnalyserContext analyserContext) {
        super(methodInfo, methodAnalysis, List.of(), parameterAnalyses, Map.of(), false, analyserContext);
    }

    @Override
    public void initialize() {
        // no-op
    }


    @Override
    public AnalysisStatus analyse(int iteration, EvaluationContext closure) {
        Map<WithInspectionAndAnalysis, Map<AnnotationExpression, List<MethodInfo>>> map = collectAnnotations();
        E2ImmuAnnotationExpressions e2 = analyserContext.getE2ImmuAnnotationExpressions();
        boolean explicitlyEmpty = methodInfo.explicitlyEmptyMethod();

        parameterAnalyses.forEach(parameterAnalysis -> {
            ParameterAnalysisImpl.Builder builder = (ParameterAnalysisImpl.Builder) parameterAnalysis;
            messages.addAll(builder.fromAnnotationsIntoProperties(Analyser.AnalyserIdentification.PARAMETER, true,
                    map.getOrDefault(builder.getParameterInfo(), Map.of()).keySet(), e2));
            if (explicitlyEmpty) {
                builder.setProperty(VariableProperty.MODIFIED_VARIABLE, Level.FALSE);
                builder.setProperty(VariableProperty.INDEPENDENT, MultiLevel.INDEPENDENT);
            }
        });

        messages.addAll(methodAnalysis.fromAnnotationsIntoProperties(Analyser.AnalyserIdentification.METHOD,
                true, map.getOrDefault(methodInfo, Map.of()).keySet(), e2));

        // IMPROVE reading preconditions from AAPI...
        methodAnalysis.precondition.set(Precondition.empty(analyserContext.getPrimitives()));
        methodAnalysis.preconditionForEventual.set(Optional.empty());

        if (explicitlyEmpty) {
            int modified = methodInfo.isConstructor ? Level.TRUE : Level.FALSE;
            methodAnalysis.setProperty(VariableProperty.MODIFIED_METHOD, modified);
            methodAnalysis.setProperty(VariableProperty.INDEPENDENT, MultiLevel.INDEPENDENT);
            computePropertiesAfterParameters();
        } else {
            computeMethodPropertyIfNecessary(VariableProperty.MODIFIED_METHOD, this::computeModifiedMethod);

            parameterAnalyses.forEach(parameterAnalysis -> {
                ParameterAnalysisImpl.Builder builder = (ParameterAnalysisImpl.Builder) parameterAnalysis;
                computeParameterIndependent(builder);
            });

            computePropertiesAfterParameters();

            computeMethodPropertyIfNecessary(VariableProperty.INDEPENDENT, this::computeMethodIndependent);
            checkMethodIndependent();
        }
        return AnalysisStatus.DONE;
    }


    private void computePropertiesAfterParameters() {
        computeMethodPropertyIfNecessary(VariableProperty.IMMUTABLE, this::computeMethodImmutable);
        computeMethodPropertyIfNecessary(VariableProperty.NOT_NULL_EXPRESSION, this::computeMethodNotNull);
        computeMethodPropertyIfNecessary(VariableProperty.CONTAINER, this::computeMethodContainer);
        computeMethodPropertyIfNecessary(VariableProperty.FLUENT, () -> bestOfOverridesOrWorstValue(VariableProperty.FLUENT));
        computeMethodPropertyIfNecessary(VariableProperty.IDENTITY, () -> bestOfOverridesOrWorstValue(VariableProperty.IDENTITY));
        computeMethodPropertyIfNecessary(VariableProperty.FINALIZER, () -> bestOfOverridesOrWorstValue(VariableProperty.FINALIZER));
        computeMethodPropertyIfNecessary(VariableProperty.CONSTANT, () -> bestOfOverridesOrWorstValue(VariableProperty.CONSTANT));
    }

    private int bestOfOverridesOrWorstValue(VariableProperty variableProperty) {
        int best = bestOfOverrides(variableProperty);
        return Math.max(variableProperty.falseValue, best);
    }

    private int computeMethodContainer() {
        ParameterizedType returnType = methodInfo.returnType();
        if (returnType.arrays > 0 || Primitives.isPrimitiveExcludingVoid(returnType) || returnType.isUnboundTypeParameter()) {
            return Level.TRUE;
        }
        if (returnType == ParameterizedType.RETURN_TYPE_OF_CONSTRUCTOR) return Level.DELAY; // no decision
        TypeInfo bestType = returnType.bestTypeInfo();
        if (bestType == null) return Level.TRUE; // unbound type parameter
        TypeAnalysis typeAnalysis = analyserContext.getTypeAnalysisNullWhenAbsent(bestType);
        int fromReturnType = typeAnalysis == null ? Level.DELAY : typeAnalysis.getProperty(VariableProperty.CONTAINER);
        int bestOfOverrides = bestOfOverrides(VariableProperty.CONTAINER);
        return Math.max(Level.FALSE, Math.max(bestOfOverrides, fromReturnType));
    }

    private int computeModifiedMethod() {
        if (methodInfo.isConstructor) return Level.TRUE;
        return Math.max(Level.FALSE, bestOfOverrides(VariableProperty.MODIFIED_METHOD));
    }

    private void computeMethodPropertyIfNecessary(VariableProperty variableProperty, IntSupplier computer) {
        int inMap = methodAnalysis.getPropertyFromMapDelayWhenAbsent(variableProperty);
        if (inMap == Level.DELAY) {
            int computed = computer.getAsInt();
            if (computed > Level.DELAY) {
                methodAnalysis.setProperty(variableProperty, computed);
            }
        }
    }

    private int computeMethodImmutable() {
        ParameterizedType returnType = methodInspection.getReturnType();
        int immutable = returnType.defaultImmutable(analyserContext);
        if (immutable == ParameterizedType.TYPE_ANALYSIS_NOT_AVAILABLE) {
            messages.add(Message.newMessage(new Location(methodInfo), Message.Label.TYPE_ANALYSIS_NOT_AVAILABLE,
                    returnType.toString()));
            return MultiLevel.MUTABLE;
        }
        return immutable;
    }

    private void computeParameterIndependent(ParameterAnalysisImpl.Builder builder) {
        int inMap = builder.getPropertyFromMapDelayWhenAbsent(VariableProperty.INDEPENDENT);
        if (inMap == Level.DELAY) {
            int value;
            ParameterizedType type = builder.getParameterInfo().parameterizedType;
            if (Primitives.isPrimitiveExcludingVoid(type)) {
                value = MultiLevel.INDEPENDENT;
            } else {
                // @Modified needs to be marked explicitly
                int modifiedMethod = methodAnalysis.getPropertyFromMapDelayWhenAbsent(VariableProperty.MODIFIED_METHOD);
                if (modifiedMethod == Level.TRUE) {
                    TypeInfo bestType = type.bestTypeInfo();
                    if (ParameterizedType.isUnboundTypeParameterOrJLO(bestType)) {
                        value = MultiLevel.DEPENDENT_1;
                    } else {
                        TypeAnalysis typeAnalysis = analyserContext.getTypeAnalysisNullWhenAbsent(bestType);
                        if (typeAnalysis != null) {
                            int immutable = typeAnalysis.getProperty(VariableProperty.IMMUTABLE);
                            if (immutable == MultiLevel.EFFECTIVELY_E2IMMUTABLE) {
                                int independent = analyserContext.getTypeAnalysis(bestType).getProperty(VariableProperty.INDEPENDENT);
                                if (independent == MultiLevel.INDEPENDENT) {
                                    value = MultiLevel.INDEPENDENT;
                                } else {
                                    value = MultiLevel.DEPENDENT_1;
                                }
                            } else {
                                value = MultiLevel.DEPENDENT;
                            }
                        } else {
                            value = MultiLevel.DEPENDENT;
                        }
                    }
                } else {
                    value = MultiLevel.INDEPENDENT;
                }
            }
            int override = builder.getParameterPropertyCheckOverrides(analyserContext, builder.getParameterInfo(), VariableProperty.INDEPENDENT);
            int finalValue = Math.max(override, value);
            builder.setProperty(VariableProperty.INDEPENDENT, finalValue);
        }
    }

    private void checkMethodIndependent() {
        int finalValue = methodAnalysis.getProperty(VariableProperty.INDEPENDENT);
        int overloads = methodInfo.methodResolution.get().overrides().stream()
                .filter(mi -> mi.methodInspection.get().isPublic())
                .map(analyserContext::getMethodAnalysis)
                .mapToInt(ma -> ma.getProperty(VariableProperty.INDEPENDENT))
                .min().orElse(finalValue);
        if (finalValue < overloads) {
            messages.add(Message.newMessage(new Location(methodInfo),
                    Message.Label.METHOD_HAS_LOWER_VALUE_FOR_INDEPENDENT, MultiLevel.niceIndependent(finalValue) + " instead of " +
                            MultiLevel.niceIndependent(overloads)));
        }
    }

    private int computeMethodIndependent() {
        int worstOverParameters = parameterAnalyses.stream()
                .mapToInt(pa -> pa.getParameterProperty(analyserContext,
                        ((ParameterAnalysisImpl.Builder)pa).getParameterInfo(), VariableProperty.INDEPENDENT))
                .min().orElse(MultiLevel.INDEPENDENT);
        int returnValue;
        if (methodInfo.isConstructor || methodInfo.isVoid()) {
            returnValue = MultiLevel.INDEPENDENT;
        } else {
            TypeInfo bestType = methodInfo.returnType().bestTypeInfo();
            if (ParameterizedType.isUnboundTypeParameterOrJLO(bestType)) {
                // unbound type parameter T, or unbound with array T[], T[][]
                returnValue = MultiLevel.DEPENDENT_1;
            } else {
                if (Primitives.isPrimitiveExcludingVoid(bestType)) {
                    returnValue = MultiLevel.INDEPENDENT;
                } else {
                    int immutable = methodAnalysis.getProperty(VariableProperty.IMMUTABLE);
                    if (immutable == MultiLevel.EFFECTIVELY_E2IMMUTABLE) {

                        TypeAnalysis typeAnalysis = analyserContext.getTypeAnalysisNullWhenAbsent(bestType);
                        if (typeAnalysis != null) {
                            returnValue = typeAnalysis.getProperty(VariableProperty.INDEPENDENT);
                        } else {
                            messages.add(Message.newMessage(new Location(methodInfo),
                                    Message.Label.TYPE_ANALYSIS_NOT_AVAILABLE, bestType.fullyQualifiedName));
                            returnValue = MultiLevel.DEPENDENT;
                        }

                    } else {
                        returnValue = MultiLevel.DEPENDENT;
                    }
                }
            }
        }
        int computed = Math.min(worstOverParameters, returnValue);
        // typeIndependent is set by hand in AnnotatedAPI files
        int typeIndependent = analyserContext.getTypeAnalysis(methodInfo.typeInfo).getProperty(VariableProperty.INDEPENDENT);
        return Math.max(MultiLevel.DEPENDENT, Math.max(computed, typeIndependent));
    }

    private int computeMethodNotNull() {
        if (methodInfo.isConstructor || methodInfo.isVoid()) return Level.DELAY; // no decision!
        if (Primitives.isPrimitiveExcludingVoid(methodInfo.returnType())) {
            return MultiLevel.EFFECTIVELY_NOT_NULL;
        }
        int fluent = methodAnalysis.getProperty(VariableProperty.FLUENT);
        if (fluent == Level.TRUE) return MultiLevel.EFFECTIVELY_NOT_NULL;
        return Math.max(MultiLevel.NULLABLE, bestOfOverrides(VariableProperty.NOT_NULL_EXPRESSION));
    }

    private int bestOfOverrides(VariableProperty variableProperty) {
        int bestOfOverrides = Level.DELAY;
        for (MethodAnalysis override : methodAnalysis.getOverrides(analyserContext)) {
            int overrideAsIs = override.getPropertyFromMapDelayWhenAbsent(variableProperty);
            bestOfOverrides = Math.max(bestOfOverrides, overrideAsIs);
        }
        return bestOfOverrides;
    }

    private Map<WithInspectionAndAnalysis, Map<AnnotationExpression, List<MethodInfo>>> collectAnnotations() {
        Map<WithInspectionAndAnalysis, Map<AnnotationExpression, List<MethodInfo>>> map = new HashMap<>();

        Map<AnnotationExpression, List<MethodInfo>> methodMap = new HashMap<>();
        map.put(methodInfo, methodMap);

        Stream.concat(Stream.of(methodInfo), methodInfo.methodResolution.get(methodInfo.fullyQualifiedName).overrides().stream()).forEach(mi -> {

            MethodInspection mii = mi.methodInspection.get();
            mii.getAnnotations().forEach(annotationExpression -> SMapList.add(methodMap, annotationExpression, mi));

            mii.getParameters().forEach(parameterInfo -> {
                Map<AnnotationExpression, List<MethodInfo>> parameterMap = map.computeIfAbsent(parameterInfo, k -> new HashMap<>());
                parameterInfo.parameterInspection.get().getAnnotations().forEach(annotationExpression ->
                        SMapList.add(parameterMap, annotationExpression, mi));
            });
        });

        map.forEach(this::checkContradictions);
        return map;
    }

    private void checkContradictions(WithInspectionAndAnalysis where,
                                     Map<AnnotationExpression, List<MethodInfo>> annotations) {
        if (annotations.size() < 2) return;
        E2ImmuAnnotationExpressions e2 = analyserContext.getE2ImmuAnnotationExpressions();
        checkContradictions(where, annotations, e2.notModified, e2.modified);
        checkContradictions(where, annotations, e2.notNull, e2.nullable);
    }

    private void checkContradictions(WithInspectionAndAnalysis where,
                                     Map<AnnotationExpression, List<MethodInfo>> annotations,
                                     AnnotationExpression left,
                                     AnnotationExpression right) {
        List<MethodInfo> leftMethods = annotations.getOrDefault(left, List.of());
        List<MethodInfo> rightMethods = annotations.getOrDefault(right, List.of());
        if (!leftMethods.isEmpty() && !rightMethods.isEmpty()) {
            messages.add(Message.newMessage(new Location(where), Message.Label.CONTRADICTING_ANNOTATIONS,
                    left + " in " + leftMethods.stream()
                            .map(mi -> mi.fullyQualifiedName).collect(Collectors.joining("; ")) +
                            "; " + right + " in " + rightMethods.stream()
                            .map(mi -> mi.fullyQualifiedName).collect(Collectors.joining("; "))));
        }
    }

    @Override
    public void write() {
        // everything contracted, nothing to write
    }

    @Override
    public List<VariableInfo> getFieldAsVariable(FieldInfo fieldInfo, boolean b) {
        return List.of();
    }

    @Override
    public void check() {
        // everything contracted, nothing to check
    }

    public Stream<Message> getMessageStream() {
        return messages.getMessageStream();
    }

    @Override
    public Stream<PrimaryTypeAnalyser> getLocallyCreatedPrimaryTypeAnalysers() {
        return Stream.empty();
    }

    @Override
    public Stream<VariableInfo> getFieldAsVariableStream(FieldInfo fieldInfo, boolean includeLocalCopies) {
        return Stream.empty();
    }

    @Override
    public StatementAnalyser findStatementAnalyser(String index) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void logAnalysisStatuses() {
        // nothing here
    }

    @Override
    public AnalyserComponents<String, ?> getAnalyserComponents() {
        throw new UnsupportedOperationException("Shallow method analyser has no analyser components");
    }

    @Override
    public void makeImmutable() {
        // nothing here
    }

    @Override
    protected String where(String componentName) {
        throw new UnsupportedOperationException();
    }
}
