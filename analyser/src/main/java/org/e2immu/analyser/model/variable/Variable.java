/*
 * e2immu: code analyser for effective and eventual immutability
 * Copyright 2020, Bart Naudts, https://www.e2immu.org
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.e2immu.analyser.model.variable;

import org.e2immu.analyser.analyser.EvaluationContext;
import org.e2immu.analyser.model.ParameterizedType;
import org.e2immu.analyser.model.Qualification;
import org.e2immu.analyser.model.SideEffect;
import org.e2immu.analyser.model.TypeInfo;
import org.e2immu.analyser.output.OutputBuilder;
import org.e2immu.analyser.output.Text;
import org.e2immu.analyser.util.UpgradableBooleanMap;

import java.util.Set;
import java.util.stream.Collectors;

/**
 * groups: FieldInfo, ParameterInfo, LocalVariable
 */

// at some point: @E2Container
public interface Variable {

    static String fullyQualifiedName(Set<Variable> dependencies) {
        if (dependencies == null) return "";
        return dependencies.stream().map(Variable::fullyQualifiedName).collect(Collectors.joining("; "));
    }

    ParameterizedType concreteReturnType();

    ParameterizedType parameterizedType();

    /**
     * @return the most simple name that the variable can take. Used to determine which names have already been taken,
     * so that the analyser can introduce a new variable with a unique name.
     */
    String simpleName();

    String fullyQualifiedName();

    boolean isStatic();

    SideEffect sideEffect(EvaluationContext evaluationContext);

    default UpgradableBooleanMap<TypeInfo> typesReferenced(boolean explicit) {
        return parameterizedType().typesReferenced(explicit);
    }

    default boolean isLocal() {
        return false;
    }

    OutputBuilder output(Qualification qualification);

    static Variable fake() {
        return new Variable() {
            @Override
            public ParameterizedType concreteReturnType() {
                return ParameterizedType.RETURN_TYPE_OF_CONSTRUCTOR;
            }

            @Override
            public ParameterizedType parameterizedType() {
                return ParameterizedType.RETURN_TYPE_OF_CONSTRUCTOR;
            }

            @Override
            public String simpleName() {
                return "fake variable";
            }

            @Override
            public String fullyQualifiedName() {
                return "fake variable";
            }

            @Override
            public boolean isStatic() {
                return false;
            }

            @Override
            public SideEffect sideEffect(EvaluationContext evaluationContext) {
                return null;
            }

            @Override
            public OutputBuilder output(Qualification qualification) {
                return new OutputBuilder().add(new Text("fake variable"));
            }

        };
    }

    /*
    Used to determine which evaluation context the variable belongs to: the normal one, or a closure?
     */
    default TypeInfo getOwningType() {
        return null;
    }

    default String nameInLinkedAnnotation() {
        return simpleName();
    }
}
