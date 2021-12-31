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

package org.e2immu.analyser.model;

import org.e2immu.analyser.analyser.AnalyserContext;
import org.e2immu.analyser.analyser.AnalysisProvider;
import org.e2immu.analyser.analyser.DV;
import org.e2immu.analyser.analyser.LinkedVariables;
import org.e2immu.analyser.inspector.MethodResolution;
import org.e2immu.analyser.output.OutputBuilder;
import org.e2immu.analyser.output.OutputMethodInfo;
import org.e2immu.analyser.parser.InspectionProvider;
import org.e2immu.analyser.parser.PrimitivesWithoutParameterizedType;
import org.e2immu.analyser.util.UpgradableBooleanMap;
import org.e2immu.annotation.*;
import org.e2immu.support.SetOnce;

import java.util.*;

@Container
@E2Immutable(after = "TypeAnalyser.analyse()") // and not MethodAnalyser.analyse(), given the back reference
public class MethodInfo implements WithInspectionAndAnalysis {
    public static final String UNARY_MINUS_OPERATOR_INT = "int.-(int)";
    public final Identifier identifier;
    public final TypeInfo typeInfo; // back reference, only @ContextClass after...
    public final String name;
    public final String fullyQualifiedName;
    public final String distinguishingName;
    public final boolean isConstructor;

    public final SetOnce<MethodInspection> methodInspection = new SetOnce<>();
    public final SetOnce<MethodAnalysis> methodAnalysis = new SetOnce<>();
    public final SetOnce<MethodResolution> methodResolution = new SetOnce<>();


    // -- a bit of primitives info

    boolean isUnaryMinusOperatorInt() {
        return UNARY_MINUS_OPERATOR_INT.equals(fullyQualifiedName()) && this.methodInspection.get().getParameters().size() == 1;
    }

    public boolean isBinaryAnd() {
        return this.name.equals("&&");
    }

    public boolean isUnaryNot() {
        return this.name.equals("!");
    }

    public boolean isPostfix() {
        return (this.name.equals("++") || this.name.equals("--")) && returnType().typeInfo != null &&
                returnType().typeInfo.fullyQualifiedName.equals("long");
    }

    @Override
    public Identifier getIdentifier() {
        return identifier;
    }

    public static String dropDollarGetClass(String string) {
        if (string.endsWith("$")) {
            if (!"getClass$".equals(string)) {
                throw new UnsupportedOperationException();
            }
            return "getClass";
        }
        return string;
    }

    /**
     * it is possible to observe a method without being able to see its return type. That does not make
     * the method a constructor... we cannot use the returnTypeObserved == null as isConstructor
     */
    public MethodInfo(Identifier identifier,
                      @NotNull TypeInfo typeInfo, @NotNull String name, String fullyQualifiedName,
                      String distinguishingName, boolean isConstructor) {
        this.identifier = Objects.requireNonNull(identifier);
        this.typeInfo = Objects.requireNonNull(typeInfo);
        this.name = Objects.requireNonNull(name);
        this.fullyQualifiedName = Objects.requireNonNull(fullyQualifiedName);
        this.distinguishingName = Objects.requireNonNull(distinguishingName);
        this.isConstructor = isConstructor;
    }

    @Override
    public TypeInfo getTypeInfo() {
        return typeInfo;
    }

    public boolean hasBeenInspected() {
        return methodInspection.isSet();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        MethodInfo that = (MethodInfo) o;
        return fullyQualifiedName.equals(that.fullyQualifiedName);
    }

    @Override
    public int hashCode() {
        return fullyQualifiedName.hashCode();
    }

    @Override
    public Inspection getInspection() {
        return methodInspection.get();
    }

    @Override
    public void setAnalysis(Analysis analysis) {
        if (analysis instanceof MethodAnalysis ma) {
            methodAnalysis.set(ma);
            Iterator<ParameterAnalysis> it = ma.getParameterAnalyses().iterator();
            for (ParameterInfo parameterInfo : methodInspection.get().getParameters()) {
                if (!it.hasNext()) throw new UnsupportedOperationException();
                ParameterAnalysis parameterAnalysis = it.next();
                parameterInfo.setAnalysis(parameterAnalysis);
            }
        } else throw new UnsupportedOperationException();
    }

    @Override
    public Analysis getAnalysis() {
        return methodAnalysis.get();
    }

    @Override
    public boolean hasBeenAnalysed() {
        return methodAnalysis.isSet();
    }

    @Override
    public UpgradableBooleanMap<TypeInfo> typesReferenced() {
        if (!hasBeenInspected()) return UpgradableBooleanMap.of();
        MethodInspection inspection = methodInspection.get();
        UpgradableBooleanMap<TypeInfo> constructorTypes = isConstructor ? UpgradableBooleanMap.of() :
                inspection.getReturnType().typesReferenced(true);
        UpgradableBooleanMap<TypeInfo> parameterTypes =
                inspection.getParameters().stream()
                        .flatMap(p -> p.typesReferenced(true).stream()).collect(UpgradableBooleanMap.collector());
        UpgradableBooleanMap<TypeInfo> annotationTypes =
                inspection.getAnnotations().stream().flatMap(ae -> ae.typesReferenced().stream()).collect(UpgradableBooleanMap.collector());
        UpgradableBooleanMap<TypeInfo> analysedAnnotationTypes =
                hasBeenAnalysed() ? methodAnalysis.get().getAnnotationStream()
                        .filter(e -> e.getValue().isVisible())
                        .flatMap(e -> e.getKey().typesReferenced().stream())
                        .collect(UpgradableBooleanMap.collector()) : UpgradableBooleanMap.of();
        UpgradableBooleanMap<TypeInfo> exceptionTypes =
                inspection.getExceptionTypes().stream().flatMap(et -> et.typesReferenced(true).stream()).collect(UpgradableBooleanMap.collector());
        UpgradableBooleanMap<TypeInfo> bodyTypes = hasBeenInspected() ?
                inspection.getMethodBody().typesReferenced() : UpgradableBooleanMap.of();
        UpgradableBooleanMap<TypeInfo> companionMethodTypes = inspection.getCompanionMethods().values().stream()
                .flatMap(cm -> cm.typesReferenced().stream()).collect(UpgradableBooleanMap.collector());
        return UpgradableBooleanMap.of(constructorTypes, parameterTypes, analysedAnnotationTypes,
                annotationTypes, exceptionTypes, companionMethodTypes, bodyTypes);
    }

    @Override
    public TypeInfo primaryType() {
        return typeInfo.primaryType();
    }

    public OutputBuilder output(Qualification qualification) {
        return output(qualification, AnalyserContext.NULL_IF_NOT_SET);
    }

    public OutputBuilder output(Qualification qualification, AnalysisProvider analysisProvider) {
        return OutputMethodInfo.output(this, qualification, analysisProvider);
    }

    public String fullyQualifiedName() {
        return fullyQualifiedName;
    }

    public String distinguishingName() {
        return distinguishingName;
    }

    public ParameterizedType returnType() {
        return methodInspection.get().getReturnType();
    }

    public Optional<AnnotationExpression> hasInspectedAnnotation(String annotationFQN) {
        if (!hasBeenInspected()) return Optional.empty();
        return (getInspection().getAnnotations().stream()
                .filter(ae -> ae.typeInfo().fullyQualifiedName.equals(annotationFQN))).findFirst();
    }

    @Override
    public Optional<AnnotationExpression> hasInspectedAnnotation(Class<?> annotation) {
        String annotationFQN = annotation.getName();
        return hasInspectedAnnotation(annotationFQN);
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public String toString() {
        return fullyQualifiedName();
    }

    public boolean isNotOverridingAnyOtherMethod() {
        return methodResolution.get().overrides().isEmpty();
    }

    public boolean isPrivate() {
        return methodInspection.get().getModifiers().contains(MethodModifier.PRIVATE);
    }

    public boolean isPrivate(InspectionProvider inspectionProvider) {
        return inspectionProvider.getMethodInspection(this).getModifiers().contains(MethodModifier.PRIVATE);
    }

    public boolean isVoid() {
        return returnType().isVoidOrJavaLangVoid();
    }

    public boolean isSynchronized() {
        return methodInspection.get().getModifiers().contains(MethodModifier.SYNCHRONIZED);
    }

    public boolean isAbstract() {
        if (typeInfo.typeInspection.get().isInterface()) {
            return !methodInspection.get().getModifiers().contains(MethodModifier.DEFAULT);
        }
        return methodInspection.get().getModifiers().contains(MethodModifier.ABSTRACT);
    }

    public boolean isNotATestMethod() {
        return hasInspectedAnnotation("org.junit.Test").isEmpty() &&
                hasInspectedAnnotation("org.junit.jupiter.api.Test").isEmpty();
    }

    public boolean noReturnValue() {
        return isVoid() || isConstructor;
    }

    public boolean hasReturnValue() {
        return !noReturnValue();
    }

    public boolean shallowAnalysis() {
        assert methodInspection.isSet();
        return !hasStatements();
    }

    public boolean explicitlyEmptyMethod() {
        if (hasStatements()) return false;
        boolean empty = !typeInfo.shallowAnalysis() && !isAbstract();
        assert !empty || noReturnValue();
        return empty;
    }

    public boolean hasStatements() {
        return !methodInspection.get().getMethodBody().isEmpty();
    }

    public boolean partOfCallCycle() {
        Set<MethodInfo> reached = methodResolution.get("Method " + fullyQualifiedName).methodsOfOwnClassReached();
        return reached.size() > 1 && reached.contains(this);
    }

    public boolean isCompanionMethod() {
        return CompanionMethodName.extract(name) != null;
    }

    @Override
    public MethodInfo getMethod() {
        return this;
    }

    @Override
    public String niceClassName() {
        return "Method";
    }

    // helper for tests
    public ParameterAnalysis parameterAnalysis(int index) {
        return methodInspection.get().getParameters().get(index).parameterAnalysis.get();
    }

    public boolean analysisAccessible(InspectionProvider inspectionProvider) {
        TypeInspection typeInspection = inspectionProvider.getTypeInspection(typeInfo);
        if (typeInspection.inspector() == Inspector.BYTE_CODE_INSPECTION) {
            MethodInspection methodInspection = inspectionProvider.getMethodInspection(this);
            return methodInspection.isPublic(inspectionProvider);
        }
        return true; // by hand, java parsing
    }

    /*
     The one method dealing with the parameters={} parameter in @Independent1, @Dependent on parameters
     */
    public Map<Integer, Map<Integer, DV>> crossLinks(InspectionProvider inspectionProvider) {
        List<ParameterInfo> parameters = inspectionProvider.getMethodInspection(this).getParameters();
        if (parameters.size() == 0) return Map.of();
        Map<Integer, Map<Integer, DV>> map = new HashMap<>();
        for (ParameterInfo parameterInfo : parameters) {
            ParameterInspection pi = parameterInfo.parameterInspection.get();
            Optional<AnnotationExpression> opt = pi.getAnnotations().stream()
                    .filter(a -> Independent1.class.getCanonicalName().equals(a.typeInfo().fullyQualifiedName) ||
                            Dependent.class.getCanonicalName().equals(a.typeInfo().fullyQualifiedName))
                    .findFirst();
            if (opt.isPresent()) {
                AnnotationExpression ae = opt.get();
                int[] refs = ae.extractIntArray("parameters");
                if (refs.length > 0) {
                    DV value = ae.typeInfo().simpleName.equals("Dependent") ? LinkedVariables.DEPENDENT_DV : LinkedVariables.INDEPENDENT1_DV;
                    for (int r : refs) {
                        Map<Integer, DV> subMap = map.computeIfAbsent(parameterInfo.index, i -> new HashMap<>());
                        subMap.put(r, value);
                    }
                }
            }
        }
        return map;
    }
}
