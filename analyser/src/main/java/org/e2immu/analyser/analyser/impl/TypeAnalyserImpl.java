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

package org.e2immu.analyser.analyser.impl;

import org.e2immu.analyser.analyser.*;
import org.e2immu.analyser.analyser.check.CheckImmutable;
import org.e2immu.analyser.analyser.check.CheckIndependent;
import org.e2immu.analyser.analyser.nonanalyserimpl.LocalAnalyserContext;
import org.e2immu.analyser.analysis.Analysis;
import org.e2immu.analyser.analysis.impl.TypeAnalysisImpl;
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.parser.E2ImmuAnnotationExpressions;
import org.e2immu.analyser.parser.Message;
import org.e2immu.annotation.NotModified;

import java.util.Objects;

import static org.e2immu.analyser.analyser.AnalysisStatus.DONE;

public abstract class TypeAnalyserImpl extends AbstractAnalyser implements TypeAnalyser {
    public final TypeInfo primaryType;
    public final TypeInfo typeInfo;
    public final TypeInspection typeInspection;
    public final TypeAnalysisImpl.Builder typeAnalysis;

    public TypeAnalyserImpl(@NotModified TypeInfo typeInfo,
                            TypeInfo primaryType,
                            AnalyserContext analyserContextInput,
                            Analysis.AnalysisMode analysisMode) {
        super("Type " + typeInfo.simpleName, new LocalAnalyserContext(analyserContextInput));
        this.typeInfo = typeInfo;
        this.primaryType = primaryType;
        typeInspection = typeInfo.typeInspection.get(typeInfo.fullyQualifiedName);

        typeAnalysis = new TypeAnalysisImpl.Builder(analysisMode, analyserContext.getPrimitives(), typeInfo,
                analyserContext);
    }

    @Override
    public TypeInfo getPrimaryType() {
        return primaryType;
    }

    @Override
    public TypeInfo getTypeInfo() {
        return typeInfo;
    }

    @Override
    public TypeAnalysisImpl.Builder getTypeAnalysis() {
        return typeAnalysis;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        TypeAnalyserImpl that = (TypeAnalyserImpl) o;
        return typeInfo.equals(that.typeInfo);
    }

    @Override
    public int hashCode() {
        return Objects.hash(typeInfo);
    }

    @Override
    public WithInspectionAndAnalysis getMember() {
        return typeInfo;
    }

    @Override
    public Analysis getAnalysis() {
        return typeAnalysis;
    }

    @Override
    public void check() {
        if (typeInfo.typePropertiesAreContracted() || isUnreachable()) return;

        E2ImmuAnnotationExpressions e2 = analyserContext.getE2ImmuAnnotationExpressions();

        internalCheckImmutableIndependent(); // do not run when program is partial, some data may not be available

        check(typeInfo, e2.utilityClass);
        check(typeInfo, e2.extensionClass);
        check(typeInfo, e2.container);
        check(typeInfo, e2.singleton);

        analyserResultBuilder.add(CheckIndependent.check(typeInfo, e2.independent, typeAnalysis));

        analyserResultBuilder.add(CheckImmutable.check(typeInfo, e2.finalFields, typeAnalysis, null));
        analyserResultBuilder.add(CheckImmutable.check(typeInfo, e2.immutable, typeAnalysis, null));
        analyserResultBuilder.add(CheckImmutable.check(typeInfo, e2.immutableContainer, typeAnalysis, null));
    }

    private void internalCheckImmutableIndependent() {
        DV independent = typeAnalysis.getProperty(Property.INDEPENDENT);
        DV immutable = typeAnalysis.getProperty(Property.IMMUTABLE);
        assert MultiLevel.independentConsistentWithImmutable(independent, immutable);
    }

    private void check(TypeInfo typeInfo, AnnotationExpression annotationKey) {
        typeInfo.error(typeAnalysis, annotationKey).ifPresent(mustBeAbsent -> {
            Message error = Message.newMessage(typeInfo.newLocation(),
                    mustBeAbsent ? Message.Label.ANNOTATION_UNEXPECTEDLY_PRESENT
                            : Message.Label.ANNOTATION_ABSENT, annotationKey.typeInfo().simpleName);
            analyserResultBuilder.add(error);
        });
    }

    @Override
    public void write() {
        E2ImmuAnnotationExpressions e2 = analyserContext.getE2ImmuAnnotationExpressions();
        typeAnalysis.transferPropertiesToAnnotations(e2);
    }


    protected AnalysisStatus analyseImmutableDeterminedByTypeParameters() {
        DV dv = typeAnalysis.immutableDeterminedByTypeParameters();
        if (dv.isDone()) {
            typeAnalysis.setImmutableDeterminedByTypeParameters(dv.valueIsTrue());
            return DONE;
        }
        CausesOfDelay hiddenContentStatus = typeAnalysis.hiddenContentDelays();
        if (hiddenContentStatus.isDelayed()) {
            typeAnalysis.setImmutableDeterminedByTypeParameters(hiddenContentStatus);
            return hiddenContentStatus;
        }

        // those hidden content types that are type parameters of the type (not of methods)
        boolean res = typeAnalysis.getHiddenContentTypes().types().stream()
                .anyMatch(pt -> pt.typeParameter != null && pt.typeParameter.getOwner().isLeft());
        typeAnalysis.setImmutableDeterminedByTypeParameters(res);
        return DONE;
    }

}
