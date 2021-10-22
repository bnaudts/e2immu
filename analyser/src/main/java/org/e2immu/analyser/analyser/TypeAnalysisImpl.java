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
import org.e2immu.analyser.model.expression.VariableExpression;
import org.e2immu.analyser.model.variable.FieldReference;
import org.e2immu.analyser.parser.E2ImmuAnnotationExpressions;
import org.e2immu.analyser.parser.Primitives;
import org.e2immu.support.AddOnceSet;
import org.e2immu.support.FlipSwitch;
import org.e2immu.support.SetOnce;
import org.e2immu.support.SetOnceMap;

import java.util.Map;
import java.util.Objects;
import java.util.Set;

public class TypeAnalysisImpl extends AnalysisImpl implements TypeAnalysis {

    private final TypeInfo typeInfo;
    private final Map<FieldReference, Expression> approvedPreconditionsE1;
    private final Map<FieldReference, Expression> approvedPreconditionsE2;

    private final HiddenContentTypes hiddenContentTypes;
    private final Map<String, MethodInfo> aspects;
    private final Set<FieldInfo> eventuallyImmutableFields;
    private final Set<FieldInfo> visibleFields;

    private final boolean immutableCanBeIncreasedByTypeParameters;

    private TypeAnalysisImpl(TypeInfo typeInfo,
                             Map<VariableProperty, Integer> properties,
                             Map<AnnotationExpression, AnnotationCheck> annotations,
                             Map<FieldReference, Expression> approvedPreconditionsE1,
                             Map<FieldReference, Expression> approvedPreconditionsE2,
                             Set<FieldInfo> eventuallyImmutableFields,
                             HiddenContentTypes hiddenContentTypes,
                             Map<String, MethodInfo> aspects,
                             Set<FieldInfo> visibleFields,
                             boolean immutableCanBeIncreasedByTypeParameters) {
        super(properties, annotations);
        this.typeInfo = typeInfo;
        this.approvedPreconditionsE1 = approvedPreconditionsE1;
        this.approvedPreconditionsE2 = approvedPreconditionsE2;
        this.hiddenContentTypes = hiddenContentTypes;
        this.aspects = Objects.requireNonNull(aspects);
        this.eventuallyImmutableFields = eventuallyImmutableFields;
        this.visibleFields = visibleFields;
        this.immutableCanBeIncreasedByTypeParameters = immutableCanBeIncreasedByTypeParameters;
    }

    @Override
    public Boolean immutableCanBeIncreasedByTypeParameters() {
        return immutableCanBeIncreasedByTypeParameters;
    }

    @Override
    public TypeInfo getTypeInfo() {
        return typeInfo;
    }

    @Override
    public boolean approvedPreconditionsE2IsEmpty() {
        return approvedPreconditionsE2.isEmpty();
    }

    @Override
    public boolean containsApprovedPreconditionsE2(FieldReference fieldReference) {
        return approvedPreconditionsE2.containsKey(fieldReference);
    }

    @Override
    public Set<FieldInfo> getEventuallyImmutableFields() {
        return eventuallyImmutableFields;
    }

    @Override
    public Map<String, MethodInfo> getAspects() {
        return aspects;
    }

    @Override
    public int getProperty(VariableProperty variableProperty) {
        return getTypeProperty(variableProperty);
    }

    @Override
    public Location location() {
        return new Location(typeInfo);
    }

    @Override
    public Map<FieldReference, Expression> getApprovedPreconditionsE1() {
        return approvedPreconditionsE1;
    }

    @Override
    public Map<FieldReference, Expression> getApprovedPreconditionsE2() {
        return approvedPreconditionsE2;
    }

    @Override
    public Expression getApprovedPreconditions(boolean e2, FieldReference fieldReference) {
        return e2 ? approvedPreconditionsE2.get(fieldReference) : approvedPreconditionsE1.get(fieldReference);
    }

    @Override
    public boolean approvedPreconditionsIsSet(boolean e2, FieldReference fieldReference) {
        return e2 ? approvedPreconditionsE2.containsKey(fieldReference) : approvedPreconditionsE1.containsKey(fieldReference);
    }

    @Override
    public boolean approvedPreconditionsIsFrozen(boolean e2) {
        return true;
    }

    @Override
    public HiddenContentTypes getTransparentTypes() {
        return hiddenContentTypes;
    }

    @Override
    public FieldInfo translateToVisibleField(FieldReference fieldReference) {
        return translateToVisibleField(visibleFields, fieldReference);
    }

    public static class CycleInfo {
        public final AddOnceSet<MethodInfo> nonModified = new AddOnceSet<>();
        public final FlipSwitch modified = new FlipSwitch();
    }

    static FieldInfo translateToVisibleField(Set<FieldInfo> visibleFields, FieldReference fieldReference) {
        if (visibleFields.contains(fieldReference.fieldInfo)) return fieldReference.fieldInfo;
        if (fieldReference.scope instanceof VariableExpression ve && ve.variable() instanceof FieldReference fr) {
            return translateToVisibleField(visibleFields, fr);
        }
        return null; // not one of ours, i
    }

    public static class Builder extends AbstractAnalysisBuilder implements TypeAnalysis {
        public final TypeInfo typeInfo;
        private final AnalyserContext analyserContext;

        // from label to condition BEFORE (used by @Mark and @Only(before="label"))
        private final SetOnceMap<FieldReference, Expression> approvedPreconditionsE1 = new SetOnceMap<>();
        private final SetOnceMap<FieldReference, Expression> approvedPreconditionsE2 = new SetOnceMap<>();
        public final AddOnceSet<FieldInfo> eventuallyImmutableFields = new AddOnceSet<>();

        public final SetOnce<HiddenContentTypes> hiddenContentTypes = new SetOnce<>();

        public final SetOnceMap<String, MethodInfo> aspects = new SetOnceMap<>();

        public final SetOnceMap<Set<MethodInfo>, CycleInfo> nonModifiedCountForMethodCallCycle = new SetOnceMap<>();
        public final SetOnce<Boolean> ignorePrivateConstructorsForFieldValues = new SetOnce<>();

        private final Set<FieldInfo> visibleFields;
        public final AnalysisMode analysisMode;

        public final SetOnce<Boolean> immutableCanBeIncreasedByTypeParameters = new SetOnce<>();

        /*
        analyser context can be null for Primitives, ShallowTypeAnalyser
         */
        public Builder(AnalysisMode analysisMode, Primitives primitives, TypeInfo typeInfo, AnalyserContext analyserContext) {
            super(primitives, typeInfo.simpleName);
            this.typeInfo = typeInfo;
            this.analysisMode = analysisMode;
            this.visibleFields = analyserContext == null ? Set.of() : Set.copyOf(typeInfo.visibleFields(analyserContext));
            this.analyserContext = analyserContext;
        }

        @Override
        public TypeInfo getTypeInfo() {
            return typeInfo;
        }

        @Override
        public AnalysisMode analysisMode() {
            return analysisMode;
        }

        @Override
        public boolean approvedPreconditionsE2IsEmpty() {
            return approvedPreconditionsE2.isEmpty();
        }

        @Override
        public boolean containsApprovedPreconditionsE2(FieldReference fieldReference) {
            return approvedPreconditionsE2.isSet(fieldReference);
        }

        @Override
        public FieldInfo translateToVisibleField(FieldReference fieldReference) {
            return TypeAnalysisImpl.translateToVisibleField(visibleFields, fieldReference);
        }

        @Override
        public Expression getApprovedPreconditions(boolean e2, FieldReference fieldReference) {
            return e2 ? approvedPreconditionsE2.get(fieldReference) : approvedPreconditionsE1.get(fieldReference);
        }

        @Override
        public boolean approvedPreconditionsIsSet(boolean e2, FieldReference fieldReference) {
            return e2 ? approvedPreconditionsE2.isSet(fieldReference) : approvedPreconditionsE1.isSet(fieldReference);
        }

        public void freezeApprovedPreconditionsE1() {
            approvedPreconditionsE1.freeze();
        }

        public boolean approvedPreconditionsIsNotEmpty(boolean e2) {
            return e2 ? !approvedPreconditionsE2.isEmpty() : !approvedPreconditionsE1.isEmpty();
        }

        public void putInApprovedPreconditionsE1(FieldReference fieldReference, Expression expression) {
            approvedPreconditionsE1.put(fieldReference, expression);
        }

        public boolean approvedPreconditionsIsFrozen(boolean e2) {
            return e2 ? approvedPreconditionsE2.isFrozen() : approvedPreconditionsE1.isFrozen();
        }

        public void freezeApprovedPreconditionsE2() {
            approvedPreconditionsE2.freeze();
        }

        public void putInApprovedPreconditionsE2(FieldReference fieldReference, Expression expression) {
            approvedPreconditionsE2.put(fieldReference, expression);
        }

        @Override
        public boolean aspectsIsSet(String aspect) {
            return aspects.isSet(aspect);
        }

        @Override
        public Map<String, MethodInfo> getAspects() {
            return aspects.toImmutableMap();
        }

        @Override
        public int getProperty(VariableProperty variableProperty) {
            return getTypeProperty(variableProperty);
        }

        @Override
        public Location location() {
            return new Location(typeInfo);
        }

        @Override
        public Map<FieldReference, Expression> getApprovedPreconditionsE1() {
            return approvedPreconditionsE1.toImmutableMap();
        }

        @Override
        public Map<FieldReference, Expression> getApprovedPreconditionsE2() {
            return approvedPreconditionsE2.toImmutableMap();
        }

        @Override
        public HiddenContentTypes getTransparentTypes() {
            if (typeInfo.isPrimaryType()) {
                return hiddenContentTypes.getOrDefaultNull();
            }
            if (analyserContext == null) return HiddenContentTypes.EMPTY; // FIXME
            TypeAnalysis primaryTypeAnalysis = analyserContext.getTypeAnalysis(typeInfo.primaryType());
            return primaryTypeAnalysis.getTransparentTypes();
        }

        @Override
        public Set<FieldInfo> getEventuallyImmutableFields() {
            return eventuallyImmutableFields.toImmutableSet();
        }

        public void transferPropertiesToAnnotations(E2ImmuAnnotationExpressions e2ImmuAnnotationExpressions) {

            // @ExtensionClass
            if (getProperty(VariableProperty.EXTENSION_CLASS) == Level.TRUE) {
                annotations.put(e2ImmuAnnotationExpressions.extensionClass, true);
            }

            // @Finalizer
            if (getProperty(VariableProperty.FINALIZER) == Level.TRUE) {
                annotations.put(e2ImmuAnnotationExpressions.finalizer, true);
            }

            // @UtilityClass
            if (getProperty(VariableProperty.UTILITY_CLASS) == Level.TRUE) {
                annotations.put(e2ImmuAnnotationExpressions.utilityClass, true);
            }

            // @Singleton
            if (getProperty(VariableProperty.SINGLETON) == Level.TRUE) {
                annotations.put(e2ImmuAnnotationExpressions.singleton, true);
            }

            int immutable = getProperty(VariableProperty.IMMUTABLE);
            doImmutableContainer(e2ImmuAnnotationExpressions, immutable, false);

            // @Independent
            int independent = getProperty(VariableProperty.INDEPENDENT);
            doIndependent(e2ImmuAnnotationExpressions, independent, MultiLevel.NOT_INVOLVED, immutable);
        }

        @Override
        public Boolean immutableCanBeIncreasedByTypeParameters() {
            return immutableCanBeIncreasedByTypeParameters.getOrDefaultNull();
        }

        public TypeAnalysis build() {
            return new TypeAnalysisImpl(typeInfo,
                    properties.toImmutableMap(),
                    annotationChecks.toImmutableMap(),
                    approvedPreconditionsE1.toImmutableMap(),
                    approvedPreconditionsE2.toImmutableMap(),
                    eventuallyImmutableFields.toImmutableSet(),
                    hiddenContentTypes.isSet() ? hiddenContentTypes.get() : HiddenContentTypes.EMPTY,
                    getAspects(),
                    visibleFields,
                    immutableCanBeIncreasedByTypeParameters.getOrDefault(false));
        }
    }
}
