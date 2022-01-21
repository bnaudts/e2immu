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

import java.util.List;
import java.util.Objects;
import java.util.Set;

public interface FieldInspection extends Inspection {

    Set<FieldModifier> getModifiers();

    FieldInitialiser getFieldInitialiser();

    default boolean fieldInitialiserIsSet() {
        return getFieldInitialiser() != null;
    }

    FieldModifier getAccess();

    default boolean isStatic() {
        return getModifiers().contains(FieldModifier.STATIC);
    }

    record FieldInitialiser(Expression initialiser,
                            TypeInfo anonymousTypeCreated,
                            MethodInfo implementationOfSingleAbstractMethod,
                            boolean callGetOnSam) {
        public FieldInitialiser {
            Objects.requireNonNull(initialiser);
        }
        public FieldInitialiser(Expression initialiser) {
            this(initialiser, null, null, false);
        }
    }

    default boolean hasFieldInitializer() {
        return getFieldInitialiser() != null;
    }

    interface Builder extends InspectionBuilder<Builder>, FieldInspection {
        Builder addModifier(FieldModifier aStatic);

        Builder setInspectedInitialiserExpression(Expression expression);

        FieldInspection build();

        void setInitialiserExpression(com.github.javaparser.ast.expr.Expression initialiserExpression);

        Builder addAnnotations(List<AnnotationExpression> annotations);

        Builder addModifiers(List<FieldModifier> modifiers);
    }
}
