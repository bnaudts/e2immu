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
import org.e2immu.analyser.model.expression.ConstantExpression;
import org.e2immu.analyser.model.expression.Instance;
import org.e2immu.analyser.parser.E2ImmuAnnotationExpressions;
import org.e2immu.analyser.parser.InspectionProvider;
import org.e2immu.analyser.parser.Message;
import org.e2immu.analyser.parser.Messages;

import java.util.stream.Stream;

public class ShallowFieldAnalyser {

    private final InspectionProvider inspectionProvider;
    private final AnalysisProvider analysisProvider;
    private final Messages messages = new Messages();
    private final E2ImmuAnnotationExpressions e2ImmuAnnotationExpressions;

    public ShallowFieldAnalyser(InspectionProvider inspectionProvider,
                                AnalysisProvider analysisProvider,
                                E2ImmuAnnotationExpressions e2) {
        this.inspectionProvider = inspectionProvider;
        this.analysisProvider = analysisProvider;
        e2ImmuAnnotationExpressions = e2;
    }

    public void analyser(FieldInfo fieldInfo, boolean typeIsEnum) {
        FieldAnalysisImpl.Builder fieldAnalysisBuilder = new FieldAnalysisImpl.Builder(inspectionProvider.getPrimitives(),
                AnalysisProvider.DEFAULT_PROVIDER,
                fieldInfo, analysisProvider.getTypeAnalysis(fieldInfo.owner));

        messages.addAll(fieldAnalysisBuilder.fromAnnotationsIntoProperties(Analyser.AnalyserIdentification.FIELD, true,
                fieldInfo.fieldInspection.get().getAnnotations(), e2ImmuAnnotationExpressions));

        FieldInspection fieldInspection = inspectionProvider.getFieldInspection(fieldInfo);
        boolean enumField = typeIsEnum && fieldInspection.isSynthetic();

        // the following code is here to save some @Final annotations in annotated APIs where there already is a `final` keyword.
        fieldAnalysisBuilder.setProperty(Property.FINAL, Level.fromBoolDv(fieldInfo.isExplicitlyFinal() || enumField));

        // unless annotated with something heavier, ...
        DV notNull;
        if (!fieldAnalysisBuilder.properties.isDone(Property.EXTERNAL_NOT_NULL)) {
            notNull = enumField ? MultiLevel.EFFECTIVELY_NOT_NULL_DV : fieldInfo.type.defaultNotNull();
            fieldAnalysisBuilder.setProperty(Property.EXTERNAL_NOT_NULL, notNull);
        } else {
            notNull = fieldAnalysisBuilder.getPropertyFromMapNeverDelay(Property.EXTERNAL_NOT_NULL);
        }

        DV typeIsContainer;
        if (!fieldAnalysisBuilder.properties.isDone(Property.CONTAINER)) {
            if (fieldAnalysisBuilder.bestType == null) {
                typeIsContainer = Level.TRUE_DV;
            } else {
                TypeAnalysis typeAnalysis = analysisProvider.getTypeAnalysisNullWhenAbsent(fieldAnalysisBuilder.bestType);
                if (typeAnalysis != null) {
                    typeIsContainer = typeAnalysis.getProperty(Property.CONTAINER);
                } else {
                    typeIsContainer = Property.CONTAINER.falseDv;
                    if (fieldInfo.isPublic()) {
                        messages.add(Message.newMessage(new Location(fieldInfo), Message.Label.TYPE_ANALYSIS_NOT_AVAILABLE,
                                fieldAnalysisBuilder.bestType.fullyQualifiedName));
                    }
                }
            }
            fieldAnalysisBuilder.setProperty(Property.CONTAINER, typeIsContainer);
        } else {
            typeIsContainer = fieldAnalysisBuilder.properties.getOrDefaultNull(Property.CONTAINER);
        }

        DV annotatedImmutable = fieldAnalysisBuilder.getPropertyFromMapDelayWhenAbsent(Property.IMMUTABLE);
        DV formallyImmutable = fieldInfo.type.defaultImmutable(analysisProvider, false);
        DV immutable = MultiLevel.MUTABLE_DV.maxIgnoreDelay(annotatedImmutable.maxIgnoreDelay(formallyImmutable));
        DV annotatedIndependent = fieldAnalysisBuilder.getPropertyFromMapDelayWhenAbsent(Property.INDEPENDENT);
        DV formallyIndependent = fieldInfo.type.defaultIndependent(analysisProvider);
        DV independent = MultiLevel.DEPENDENT_DV.maxIgnoreDelay(annotatedIndependent.maxIgnoreDelay(formallyIndependent));

        Expression value;
        if (fieldAnalysisBuilder.getProperty(Property.FINAL).valueIsTrue()
                && fieldInfo.fieldInspection.get().fieldInitialiserIsSet()) {
            Expression initialiser = fieldInfo.fieldInspection.get().getFieldInitialiser().initialiser();
            if (initialiser instanceof ConstantExpression<?> constantExpression) {
                value = constantExpression;
            } else {
                value = Instance.forField(fieldInfo, notNull, immutable, typeIsContainer, independent);
            }
        } else {
            value = Instance.forField(fieldInfo, notNull, immutable, typeIsContainer, independent);
        }
        fieldAnalysisBuilder.setValue(value);
        fieldInfo.setAnalysis(fieldAnalysisBuilder.build());
    }

    public Stream<Message> getMessageStream() {
        return messages.getMessageStream();
    }
}
