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

import org.e2immu.analyser.analyser.AbstractAnalysisBuilder;
import org.e2immu.analyser.analyser.AnalysisImpl;
import org.e2immu.analyser.analyser.AnalysisProvider;
import org.e2immu.analyser.analyser.VariableProperty;
import org.e2immu.analyser.parser.E2ImmuAnnotationExpressions;
import org.e2immu.analyser.parser.Primitives;
import org.e2immu.support.FlipSwitch;
import org.e2immu.support.SetOnceMap;

import java.util.Map;

public class ParameterAnalysisImpl extends AnalysisImpl implements ParameterAnalysis {

    private final ParameterInfo parameterInfo;
    public final Map<FieldInfo, AssignedOrLinked> assignedToField;

    private ParameterAnalysisImpl(ParameterInfo parameterInfo,
                                  Map<VariableProperty, Integer> properties,
                                  Map<AnnotationExpression, AnnotationCheck> annotations,
                                  Map<FieldInfo, AssignedOrLinked> assignedToField) {
        super(properties, annotations);
        this.parameterInfo = parameterInfo;
        this.assignedToField = assignedToField;
    }

    @Override
    public int getProperty(VariableProperty variableProperty) {
        return getParameterProperty(AnalysisProvider.DEFAULT_PROVIDER, parameterInfo, variableProperty);
    }

    @Override
    public Location location() {
        return new Location(parameterInfo);
    }

    public static class Builder extends AbstractAnalysisBuilder implements ParameterAnalysis {
        private final ParameterInfo parameterInfo;
        private final SetOnceMap<FieldInfo, AssignedOrLinked> assignedToField = new SetOnceMap<>();
        private final FlipSwitch delaysOnFieldsResolved = new FlipSwitch();
        public final Location location;
        private final AnalysisProvider analysisProvider;

        public Builder(Primitives primitives, AnalysisProvider analysisProvider, ParameterInfo parameterInfo) {
            super(primitives, parameterInfo.simpleName());
            this.parameterInfo = parameterInfo;
            this.location = new Location(parameterInfo);
            this.analysisProvider = analysisProvider;
        }

        public ParameterInfo getParameterInfo() {
            return parameterInfo;
        }

        @Override
        public boolean isAssignedToFieldDelaysResolved() {
            return delaysOnFieldsResolved.isSet();
        }

        public void resolveFieldDelays() {
            if (!delaysOnFieldsResolved.isSet()) delaysOnFieldsResolved.set();
        }

        @Override
        public int getProperty(VariableProperty variableProperty) {
            return getParameterProperty(analysisProvider, parameterInfo, variableProperty);
        }

        @Override
        public Location location() {
            return location;
        }

        @Override
        public Map<FieldInfo, AssignedOrLinked> getAssignedToField() {
            return assignedToField.toImmutableMap();
        }

        @Override
        public Analysis build() {
            return new ParameterAnalysisImpl(parameterInfo, properties.toImmutableMap(),
                    annotationChecks.toImmutableMap(), getAssignedToField());
        }

        public void transferPropertiesToAnnotations(AnalysisProvider analysisProvider, E2ImmuAnnotationExpressions e2ImmuAnnotationExpressions) {

            // no annotations can be added to primitives
            if (Primitives.isPrimitiveExcludingVoid(parameterInfo.parameterizedType)) return;

            // @NotModified, @Modified
            // implicitly @NotModified when E2Immutable
            int modified = getProperty(VariableProperty.MODIFIED_VARIABLE);
            if (parameterInfo.parameterizedType.isE2Immutable(analysisProvider) != Boolean.TRUE) {
                AnnotationExpression ae = modified == Level.FALSE ? e2ImmuAnnotationExpressions.notModified :
                        e2ImmuAnnotationExpressions.modified;
                annotations.put(ae, true);
            }

            // @NotNull
            doNotNull(e2ImmuAnnotationExpressions, getProperty(VariableProperty.NOT_NULL_PARAMETER));

            // @Independent1; @Independent, @Dependent not shown
            int independentType = parameterInfo.parameterizedType.defaultIndependent(analysisProvider);
            int independent = getProperty(VariableProperty.INDEPENDENT);
            if (independent == MultiLevel.INDEPENDENT && independentType < MultiLevel.INDEPENDENT) {
                annotations.put(e2ImmuAnnotationExpressions.independent, true);
            } else if (independent == MultiLevel.INDEPENDENT_1 && independentType < MultiLevel.INDEPENDENT_1) {
                annotations.put(e2ImmuAnnotationExpressions.independent1, true);
            }
        }

        public boolean addAssignedToField(FieldInfo fieldInfo, AssignedOrLinked assignedOrLinked) {
            if (!assignedToField.isSet(fieldInfo)) {
                assignedToField.put(fieldInfo, assignedOrLinked);
                return true;
            }
            return false;
        }

        public void freezeAssignedToField() {
            assignedToField.freeze();
        }

        public boolean assignedToFieldIsFrozen() {
            return assignedToField.isFrozen();
        }
    }

}
