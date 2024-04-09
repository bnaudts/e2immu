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

package org.e2immu.analyser.analysis.impl;

import org.e2immu.analyser.analyser.*;
import org.e2immu.analyser.analysis.TypeAnalysis;
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.model.expression.VariableExpression;
import org.e2immu.analyser.model.variable.FieldReference;
import org.e2immu.analyser.parser.E2ImmuAnnotationExpressions;
import org.e2immu.analyser.parser.Primitives;
import org.e2immu.annotation.FinalFields;
import org.e2immu.annotation.Independent;
import org.e2immu.annotation.type.UtilityClass;
import org.e2immu.support.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

public class TypeAnalysisImpl extends AnalysisImpl implements TypeAnalysis {
    private static final Logger LOGGER = LoggerFactory.getLogger(TypeAnalysisImpl.class);

    private final TypeInfo typeInfo;
    private final Map<FieldReference, Expression> approvedPreconditionsE1;
    private final Map<FieldReference, Expression> approvedPreconditionsE2;

    private final Map<String, MethodInfo> aspects;
    private final Set<FieldInfo> eventuallyImmutableFields;
    private final Set<FieldInfo> guardedByEventuallyImmutableFields;
    private final Set<FieldInfo> visibleFields;
    private final boolean immutableDeterminedByTypeParameters;
    private final Set<FieldInfo> guardedForContainerProperty;
    private final Set<FieldInfo> guardedForInheritedContainerProperty;

    private TypeAnalysisImpl(TypeInfo typeInfo,
                             Map<Property, DV> properties,
                             Map<AnnotationExpression, AnnotationCheck> annotations,
                             Map<FieldReference, Expression> approvedPreconditionsE1,
                             Map<FieldReference, Expression> approvedPreconditionsE2,
                             Set<FieldInfo> eventuallyImmutableFields,
                             Set<FieldInfo> guardedByEventuallyImmutableFields,
                             Map<String, MethodInfo> aspects,
                             Set<FieldInfo> visibleFields,
                             boolean immutableDeterminedByTypeParameters,
                             Set<FieldInfo> guardedForContainerProperty,
                             Set<FieldInfo> guardedForInheritedContainerProperty) {
        super(properties, annotations);
        this.typeInfo = typeInfo;
        this.approvedPreconditionsE1 = approvedPreconditionsE1;
        this.approvedPreconditionsE2 = approvedPreconditionsE2;
        this.aspects = Objects.requireNonNull(aspects);
        this.eventuallyImmutableFields = eventuallyImmutableFields;
        this.guardedByEventuallyImmutableFields = guardedByEventuallyImmutableFields;
        this.visibleFields = visibleFields;
        this.immutableDeterminedByTypeParameters = immutableDeterminedByTypeParameters;
        this.guardedForContainerProperty = guardedForContainerProperty;
        this.guardedForInheritedContainerProperty = guardedForInheritedContainerProperty;
    }

    @Override
    public String toString() {
        return typeInfo.fullyQualifiedName;
    }

    @Override
    public DV immutableDeterminedByTypeParameters() {
        return DV.fromBoolDv(immutableDeterminedByTypeParameters);
    }

    @Override
    public TypeInfo getTypeInfo() {
        return typeInfo;
    }

    @Override
    public boolean approvedPreconditionsImmutableIsEmpty() {
        return approvedPreconditionsE2.isEmpty();
    }

    @Override
    public boolean containsApprovedPreconditionsImmutable(FieldReference fieldReference) {
        return approvedPreconditionsE2.containsKey(fieldReference);
    }

    @Override
    public Set<FieldInfo> getEventuallyImmutableFields() {
        return eventuallyImmutableFields;
    }

    @Override
    public Set<FieldInfo> getGuardedByEventuallyImmutableFields() {
        return guardedByEventuallyImmutableFields;
    }

    @Override
    public Map<String, MethodInfo> getAspects() {
        return aspects;
    }

    @Override
    public DV getProperty(Property property) {
        return getTypeProperty(property);
    }

    @Override
    public Location location(Stage stage) {
        return typeInfo.newLocation();
    }

    @Override
    public Map<FieldReference, Expression> getApprovedPreconditionsFinalFields() {
        return approvedPreconditionsE1;
    }

    @Override
    public Map<FieldReference, Expression> getApprovedPreconditionsImmutable() {
        return approvedPreconditionsE2;
    }

    @Override
    public Expression getApprovedPreconditions(boolean e2, FieldReference fieldReference) {
        return e2 ? approvedPreconditionsE2.get(fieldReference) : approvedPreconditionsE1.get(fieldReference);
    }

    @Override
    public CausesOfDelay approvedPreconditionsStatus(boolean e2, FieldReference fieldInfo) {
        return CausesOfDelay.EMPTY;
    }

    @Override
    public CausesOfDelay approvedPreconditionsStatus(boolean e2) {
        return CausesOfDelay.EMPTY;
    }

    @Override
    public FieldInfo translateToVisibleField(FieldReference fieldReference) {
        return translateToVisibleField(visibleFields, fieldReference);
    }

    @Override
    public boolean approvedPreconditionsIsNotEmpty(boolean e2) {
        return e2 ? !approvedPreconditionsE2.isEmpty() : !approvedPreconditionsE1.isEmpty();
    }

    @Override
    public Set<FieldInfo> guardedForContainerProperty() {
        return guardedForContainerProperty;
    }

    @Override
    public CausesOfDelay guardedForContainerPropertyDelays() {
        return CausesOfDelay.EMPTY;
    }

    @Override
    public Set<FieldInfo> guardedForInheritedContainerProperty() {
        return guardedForInheritedContainerProperty;
    }

    @Override
    public CausesOfDelay guardedForInheritedContainerPropertyDelays() {
        return CausesOfDelay.EMPTY;
    }

    @Override
    public Set<FieldInfo> visibleFields() {
        return visibleFields;
    }

    public static class CycleInfo {
        public final AddOnceSet<MethodInfo> nonModified = new AddOnceSet<>();
        public final FlipSwitch modified = new FlipSwitch();

        @Override
        public String toString() {
            return "{" + nonModified.stream().map(m -> m.name).sorted().collect(Collectors.joining(",")) + (modified.isSet() ? "_M" : "") + "}";
        }
    }

    static FieldInfo translateToVisibleField(Set<FieldInfo> visibleFields, FieldReference fieldReference) {
        if (visibleFields.contains(fieldReference.fieldInfo())) return fieldReference.fieldInfo();
        if (fieldReference.scope() instanceof VariableExpression ve && ve.variable() instanceof FieldReference fr) {
            return translateToVisibleField(visibleFields, fr);
        }
        return null; // not one of ours, i
    }

    public static class Builder extends AbstractAnalysisBuilder implements TypeAnalysis {
        public final TypeInfo typeInfo;

        // from label to condition BEFORE (used by @Mark and @Only(before="label"))
        private final SetOnceMap<FieldReference, Expression> approvedPreconditionsE1 = new SetOnceMap<>();
        private final SetOnceMap<FieldReference, Expression> approvedPreconditionsE2 = new SetOnceMap<>();
        private final AddOnceSet<FieldInfo> eventuallyImmutableFields = new AddOnceSet<>();
        private final AddOnceSet<FieldInfo> guardedByEventuallyImmutableFields = new AddOnceSet<>();

        public final SetOnceMap<String, MethodInfo> aspects = new SetOnceMap<>();

        public final SetOnceMap<Set<MethodInfo>, CycleInfo> nonModifiedCountForMethodCallCycle = new SetOnceMap<>();
        public final SetOnce<Boolean> ignorePrivateConstructorsForFieldValues = new SetOnce<>();

        private final Set<FieldInfo> visibleFields;
        public final AnalysisMode analysisMode;

        private final VariableFirstThen<CausesOfDelay, Boolean> immutableDeterminedByTypeParameters;
        private final VariableFirstThen<CausesOfDelay, Set<FieldInfo>> guardedForContainerProperty;
        private final VariableFirstThen<CausesOfDelay, Set<FieldInfo>> guardedForInheritedContainerProperty;

        private CausesOfDelay approvedPreconditionsE1Delays;
        private CausesOfDelay approvedPreconditionsE2Delays;

        @Override
        public void internalAllDoneCheck() {
            super.internalAllDoneCheck();
            assert approvedPreconditionsE2.isFrozen();
            assert approvedPreconditionsE1.isFrozen();
            assert immutableDeterminedByTypeParameters.isSet();
        }

        private static CausesOfDelay initialDelay(TypeInfo typeInfo) {
            return typeInfo.delay(CauseOfDelay.Cause.INITIAL_VALUE);
        }


        @Override
        public Set<FieldInfo> visibleFields() {
            return visibleFields;
        }

        /*
                analyser context can be null for Primitives, ShallowTypeAnalyser
                 */
        public Builder(AnalysisMode analysisMode, Primitives primitives, TypeInfo typeInfo, AnalyserContext analyserContext) {
            super(primitives, typeInfo.simpleName);
            this.typeInfo = typeInfo;
            this.analysisMode = analysisMode;
            this.visibleFields = analyserContext == null ? Set.of() : Set.copyOf(typeInfo.visibleFields(analyserContext));
            CausesOfDelay initialDelay = initialDelay(typeInfo);
            immutableDeterminedByTypeParameters = new VariableFirstThen<>(initialDelay);
            approvedPreconditionsE2Delays = initialDelay;
            approvedPreconditionsE1Delays = initialDelay;
            guardedForContainerProperty = new VariableFirstThen<>(initialDelay);
            guardedForInheritedContainerProperty = new VariableFirstThen<>(initialDelay);
        }

        @Override
        public String toString() {
            return typeInfo.fullyQualifiedName;
        }

        @Override
        protected void writeTypeEventualFields(String after) {
            for (String fieldName : after.split(",")) {
                FieldInfo fieldInfo = getTypeInfo().getFieldByName(fieldName.trim(), false);
                if (fieldInfo != null) {
                    eventuallyImmutableFields.add(fieldInfo);
                } else {
                    LOGGER.warn("Could not find field {} in type {}, is supposed to be eventual", fieldName,
                            typeInfo.fullyQualifiedName);
                }
            }
        }

        @Override
        public void setAspect(String aspect, MethodInfo mainMethod) {
            aspects.put(aspect, mainMethod);
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
        public boolean approvedPreconditionsImmutableIsEmpty() {
            return approvedPreconditionsE2.isEmpty();
        }

        @Override
        public boolean containsApprovedPreconditionsImmutable(FieldReference fieldReference) {
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
        public CausesOfDelay approvedPreconditionsStatus(boolean e2, FieldReference fieldReference) {
            assert fieldReference != null;
            return e2 ? (approvedPreconditionsE2.isSet(fieldReference) ? CausesOfDelay.EMPTY :
                    fieldReference.fieldInfo().delay(CauseOfDelay.Cause.APPROVED_PRECONDITIONS)) :
                    (approvedPreconditionsE1.isSet(fieldReference) ? CausesOfDelay.EMPTY :
                            fieldReference.fieldInfo().delay(CauseOfDelay.Cause.APPROVED_PRECONDITIONS));
        }

        @Override
        public CausesOfDelay approvedPreconditionsStatus(boolean e2) {
            return e2 ? (approvedPreconditionsE2.isFrozen() ? CausesOfDelay.EMPTY : approvedPreconditionsE2Delays)
                    : (approvedPreconditionsE1.isFrozen() ? CausesOfDelay.EMPTY : approvedPreconditionsE1Delays);
        }

        public void freezeApprovedPreconditionsFinalFields() {
            approvedPreconditionsE1.freeze();
        }

        @Override
        public boolean approvedPreconditionsIsNotEmpty(boolean e2) {
            return e2 ? !approvedPreconditionsE2.isEmpty() : !approvedPreconditionsE1.isEmpty();
        }

        public void putInApprovedPreconditionsE1(FieldReference fieldReference, Expression expression) {
            assert fieldReference != null;
            assert expression != null;
            approvedPreconditionsE1.put(fieldReference, expression);
        }

        public void freezeApprovedPreconditionsImmutable() {
            approvedPreconditionsE2.freeze();
        }

        public void putInApprovedPreconditionsImmutable(FieldReference fieldReference, Expression expression) {
            assert fieldReference != null;
            assert expression != null;
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
        public DV getProperty(Property property) {
            return getTypeProperty(property);
        }

        @Override
        public Location location(Stage stage) {
            return typeInfo.newLocation();
        }

        @Override
        public Map<FieldReference, Expression> getApprovedPreconditionsFinalFields() {
            return approvedPreconditionsE1.toImmutableMap();
        }

        @Override
        public Map<FieldReference, Expression> getApprovedPreconditionsImmutable() {
            return approvedPreconditionsE2.toImmutableMap();
        }

        @Override
        public Set<FieldInfo> getEventuallyImmutableFields() {
            return eventuallyImmutableFields.toImmutableSet();
        }

        @Override
        public Set<FieldInfo> getGuardedByEventuallyImmutableFields() {
            return guardedByEventuallyImmutableFields.toImmutableSet();
        }

        public void transferPropertiesToAnnotations(E2ImmuAnnotationExpressions e2ImmuAnnotationExpressions) {

            boolean extensionOrUtility;
            // @ExtensionClass
            if (getProperty(Property.EXTENSION_CLASS).valueIsTrue()) {
                addAnnotation(e2ImmuAnnotationExpressions.extensionClass);
                addAnnotation(E2ImmuAnnotationExpressions.create(primitives, UtilityClass.class, E2ImmuAnnotationExpressions.IMPLIED, true));
                extensionOrUtility = true;
            } else if (getProperty(Property.UTILITY_CLASS).valueIsTrue()) { // @UtilityClass
                addAnnotation(e2ImmuAnnotationExpressions.utilityClass);
                extensionOrUtility = true;
            } else {
                extensionOrUtility = false;
            }

            // @Finalizer
            if (getProperty(Property.FINALIZER).valueIsTrue()) {
                addAnnotation(e2ImmuAnnotationExpressions.finalizer);
            }

            // @Singleton
            if (getProperty(Property.SINGLETON).valueIsTrue()) {
                addAnnotation(e2ImmuAnnotationExpressions.singleton);
            }

            DV immutable = getProperty(Property.IMMUTABLE);
            DV container = getProperty(Property.CONTAINER);
            doImmutableContainer(e2ImmuAnnotationExpressions, immutable, container, !extensionOrUtility,
                    true, null, false);

            // @Independent
            DV independent = getProperty(Property.INDEPENDENT);
            boolean addedIndependent = doIndependent(e2ImmuAnnotationExpressions, independent,
                    MultiLevel.NOT_INVOLVED_DV, immutable);

            DV modified = getProperty(Property.MODIFIED_METHOD);

            /*
            if 2 of the 3 key properties of an immutable type are present, but not the third, add it in "absent" mode
             */
            if (MultiLevel.EFFECTIVELY_FINAL_FIELDS_DV.equals(immutable) && modified.valueIsFalse()
                && independent.equals(MultiLevel.DEPENDENT_DV) && !addedIndependent) {
                addAnnotation(E2ImmuAnnotationExpressions.create(primitives,
                        Independent.class, E2ImmuAnnotationExpressions.ABSENT, true));
            }
            if (modified.valueIsFalse() && MultiLevel.isAtLeastIndependentHC(independent)
                && immutable.equals(MultiLevel.MUTABLE_DV)) {
                addAnnotation(E2ImmuAnnotationExpressions.create(primitives,
                        FinalFields.class, E2ImmuAnnotationExpressions.ABSENT, true));
            }
            if (MultiLevel.isAtLeastIndependentHC(independent) && MultiLevel.EFFECTIVELY_FINAL_FIELDS_DV.equals(immutable)
                && modified.valueIsTrue()) {
                addAnnotation(e2ImmuAnnotationExpressions.modified);
            }
        }

        @Override
        public DV immutableDeterminedByTypeParameters() {
            return immutableDeterminedByTypeParameters.isFirst() ? immutableDeterminedByTypeParameters.getFirst()
                    : DV.fromBoolDv(immutableDeterminedByTypeParameters.get());
        }

        public void setImmutableDeterminedByTypeParameters(CausesOfDelay causes) {
            immutableDeterminedByTypeParameters.setFirst(causes);
        }

        public void setImmutableDeterminedByTypeParameters(Boolean b) {
            immutableDeterminedByTypeParameters.set(b);
        }

        public TypeAnalysis build() {
            return new TypeAnalysisImpl(typeInfo,
                    properties.toImmutableMap(),
                    annotationChecks.toImmutableMap(),
                    approvedPreconditionsE1.toImmutableMap(),
                    approvedPreconditionsE2.toImmutableMap(),
                    eventuallyImmutableFields.toImmutableSet(),
                    guardedByEventuallyImmutableFields.toImmutableSet(),
                    getAspects(),
                    visibleFields,
                    immutableDeterminedByTypeParameters.getOrDefault(false),
                    guardedForContainerProperty(),
                    guardedForInheritedContainerProperty());
        }

        public void setApprovedPreconditionsE1Delays(CausesOfDelay causes) {
            approvedPreconditionsE1Delays = causes;
        }

        public void setApprovedPreconditionsImmutableDelays(CausesOfDelay causes) {
            approvedPreconditionsE2Delays = causes;
        }

        private static final Set<Property> ACCEPTED = Set.of(Property.IMMUTABLE, Property.PARTIAL_IMMUTABLE,
                Property.CONTAINER, Property.FINALIZER,
                Property.INDEPENDENT, Property.EXTENSION_CLASS, Property.UTILITY_CLASS, Property.SINGLETON,
                Property.MODIFIED_METHOD, Property.STATIC_SIDE_EFFECTS);

        @Override
        public void setProperty(Property property, DV i) {
            assert ACCEPTED.contains(property) : "Do not accept " + property + " on types";
            super.setProperty(property, i);
        }

        public Set<FieldInfo> nonFinalFieldsNotApprovedOrGuarded(List<FieldReference> nonFinalFields) {
            return nonFinalFields.stream()
                    .filter(fr -> !approvedPreconditionsE1.isSet(fr)
                                  && !guardedByEventuallyImmutableFields.contains(fr.fieldInfo()))
                    .map(FieldReference::fieldInfo)
                    .collect(Collectors.toUnmodifiableSet());
        }

        public boolean eventuallyImmutableFieldNotYetSet(FieldInfo fieldInfo) {
            return !eventuallyImmutableFields.contains(fieldInfo);
        }

        public void addEventuallyImmutableField(FieldInfo fieldInfo) {
            assert fieldInfo != null;
            eventuallyImmutableFields.add(fieldInfo);
        }

        @Override
        public String markLabelFromType() {
            return isEventual() ? markLabel() : "";
        }

        public void addGuardedByEventuallyImmutableField(FieldInfo fieldInfo) {
            assert fieldInfo != null;
            if (!guardedByEventuallyImmutableFields.contains(fieldInfo)) {
                guardedByEventuallyImmutableFields.add(fieldInfo);
            }
        }

        public void setGuardedForContainerPropertyDelay(CausesOfDelay causes) {
            guardedForContainerProperty.setFirst(causes);
        }

        public void setGuardedForContainerProperty(Set<FieldInfo> fields) {
            guardedForContainerProperty.set(fields);
        }

        @Override
        public CausesOfDelay guardedForContainerPropertyDelays() {
            return guardedForContainerProperty.getFirstOrDefault(CausesOfDelay.EMPTY);
        }

        @Override
        public Set<FieldInfo> guardedForContainerProperty() {
            return guardedForContainerProperty.getOrDefault(Set.of());
        }

        public void setGuardedForInheritedContainerPropertyDelay(CausesOfDelay causes) {
            guardedForInheritedContainerProperty.setFirst(causes);
        }

        public void setGuardedForInheritedContainerProperty(Set<FieldInfo> fields) {
            guardedForInheritedContainerProperty.set(fields);
        }


        @Override
        public CausesOfDelay guardedForInheritedContainerPropertyDelays() {
            return guardedForInheritedContainerProperty.getFirstOrDefault(CausesOfDelay.EMPTY);
        }

        @Override
        public Set<FieldInfo> guardedForInheritedContainerProperty() {
            return guardedForInheritedContainerProperty.getOrDefault(Set.of());
        }
    }
}
