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

import com.github.javaparser.ast.stmt.BlockStmt;
import org.e2immu.analyser.analyser.AnnotationParameters;
import org.e2immu.analyser.model.statement.Block;
import org.e2immu.analyser.parser.InspectionProvider;
import org.e2immu.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Set;

@Container
public interface MethodInspection extends Inspection {

    @NotNull
    String getFullyQualifiedName();

    @NotNull
    String getDistinguishingName();

    @NotNull
    MethodInfo getMethodInfo(); // backlink, container... will become contextclass+immutable eventually

    @NotNull
    ParameterizedType getReturnType(); // ContextClass

    @NotNull
    Block getMethodBody();

    @NotNull1
    List<ParameterInfo> getParameters();

    /*
    These are the modifiers that were found in the source code or byte code; do not use them to
    compute essential properties of the method! E.g., an abstract method in an interface may not contain
    the ABSTRACT method modifier.
     */
    @NotNull1
    Set<MethodModifier> getParsedModifiers();

    @NotNull1
    List<TypeParameter> getTypeParameters();

    @NotNull1
    List<ParameterizedType> getExceptionTypes();

    @NotNull
    Map<CompanionMethodName, MethodInfo> getCompanionMethods();

    boolean isStatic();

    boolean isDefault();

    boolean isVarargs();

    /**
     * Returns the minimally required modifiers needed in the output for this method. Avoids being verbose!!
     */
    List<MethodModifier> minimalModifiers();

    boolean isCompactConstructor();

    boolean isStaticBlock();

    default ParameterizedType formalParameterType(int index) {
        int formalParams = getParameters().size();
        if (index < formalParams - 1 || index < formalParams && !isVarargs()) {
            return getParameters().get(index).parameterizedType;
        }
        return getParameters().get(formalParams - 1).parameterizedType.copyWithOneFewerArrays();
    }

    default boolean hasContractedFinalizer() {
        return getAnnotations().stream()
                .filter(ae -> {
                    AnnotationParameters ap = ae.e2ImmuAnnotationParameters();
                    return ap != null && ap.contract();
                })
                .anyMatch(ae -> ae.typeInfo().fullyQualifiedName.equals(Finalizer.class.getCanonicalName()));
    }

    boolean isAbstract();

    default boolean isFactoryMethod() {
        assert isStatic();
        if (getParameters().isEmpty()) return false;
        return getReturnType().typeInfo != null && getReturnType().typeInfo == getMethodInfo().typeInfo;
    }

    default boolean isVoid() {
        return getReturnType().isVoid();
    }

    /**
     * in a functional interface, we need exactly one non-static, non-default method, but you can always
     * add equals() or hashCode() or any other method from java.lang.Object() to the overload list...
     *
     * @return true for equals, hashCode etc.
     */
    default boolean isOverloadOfJLOMethod() {
        if ("equals".equals(getMethodInfo().name) && getParameters().size() == 1) return true;
        if ("hashCode".equals(getMethodInfo().name) && getParameters().size() == 0) return true;
        return "toString".equals(getMethodInfo().name) && getParameters().size() == 0;
    }

    boolean isSynchronized();

    boolean isFinal();

    default boolean isPubliclyAccessible() {
        return isPubliclyAccessible(InspectionProvider.DEFAULT);
    }

    default boolean isPubliclyAccessible(InspectionProvider inspectionProvider) {
        if (!isPublic()) return false;
        TypeInspection typeInspection = inspectionProvider.getTypeInspection(getMethodInfo().typeInfo);
        return typeInspection.isPublic();
    }

    interface Builder extends InspectionBuilder<Builder>, MethodInspection {

        @Fluent
        Builder addParameter(ParameterInspection.Builder pib);

        void readyToComputeFQN(InspectionProvider inspectionProvider);

        @Fluent
        Builder addExceptionType(ParameterizedType pt);

        @Fluent
        Builder addModifier(MethodModifier from);

        @Fluent
        Builder addTypeParameter(TypeParameter tp);

        List<ParameterInspection.Builder> getParameterBuilders();

        @Modified
        void makeParametersImmutable();

        @Fluent
        Builder addCompanionMethods(Map<CompanionMethodName, MethodInspection.Builder> companionMethods);

        @Fluent
        Builder setReturnType(ParameterizedType pt);

        @Fluent
        Builder setBlock(BlockStmt blockStmt);

        @Modified
        @NotNull
        MethodInspection build(InspectionProvider inspectionProvider);

        @Nullable
        MethodInfo methodInfo();

        @NotNull
        TypeInfo owner();

        @NotNull
        String name();

        boolean isConstructor();

        void copyFrom(MethodInspection parent);

        @Fluent
        Builder setInspectedBlock(Block body);

        @Fluent
        Builder setStatic(boolean b);

        ParameterInspection.Builder newParameterInspectionBuilder(Identifier generate, int i);

        ParameterInspection.Builder newParameterInspectionBuilder(Identifier generate,
                                                                  ParameterizedType concreteTypeOfParameter,
                                                                  String name, int index);

        @Fluent
        Builder setAbstractMethod();

        @Fluent
        Builder computeAccess(InspectionProvider inspectionProvider);

        @Fluent
        Builder setAccess(Access access);
    }
}
