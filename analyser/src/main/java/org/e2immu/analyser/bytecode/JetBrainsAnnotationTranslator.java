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

package org.e2immu.analyser.bytecode;

import org.e2immu.analyser.annotationxml.model.Annotation;
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.model.expression.FieldAccess;
import org.e2immu.analyser.model.expression.MemberValuePair;
import org.e2immu.analyser.model.expression.TypeExpression;
import org.e2immu.analyser.model.expression.VariableExpression;
import org.e2immu.analyser.parser.Primitives;
import org.e2immu.analyser.parser.TypeContext;

import java.util.List;

public class JetBrainsAnnotationTranslator {
    private static final String ORG_JETBRAINS_ANNOTATIONS_NOTNULL = "org.jetbrains.annotations.NotNull";
    private static final String E2IMMU = "org.e2immu.annotation";

    private final TypeContext typeContext;

    public JetBrainsAnnotationTranslator(TypeContext typeContext) {
        this.typeContext = typeContext;
    }

    public <T> void mapAnnotations(List<Annotation> annotations, BuilderWithAnnotations<T> builderWithAnnotations) {
        for (Annotation annotation : annotations) {
            mapAnnotation(annotation, builderWithAnnotations);
        }
    }

    private <T> void mapAnnotation(Annotation annotation, BuilderWithAnnotations<T> builderWithAnnotations) {
        if (ORG_JETBRAINS_ANNOTATIONS_NOTNULL.equals(annotation.name)) {
            if (builderWithAnnotations instanceof ParameterInspection.ParameterInspectionBuilder) {
                builderWithAnnotations.addAnnotation(typeContext.notNull.get());
            }
        } else if (annotation.name.startsWith(E2IMMU)) {
            builderWithAnnotations.addAnnotation(toAnnotationExpression(annotation));
        }
    }

    private AnnotationExpression toAnnotationExpression(Annotation annotation) {
        TypeInfo typeInfo = typeContext.getFullyQualified(annotation.name, true);
        MemberValuePair contractExpression = new MemberValuePair("type",
                new VariableExpression(
                        new FieldReference(
                                Primitives.PRIMITIVES.annotationTypeContract, null)));
        return AnnotationExpression.fromAnalyserExpressions(typeInfo, List.of(contractExpression));
    }
}
