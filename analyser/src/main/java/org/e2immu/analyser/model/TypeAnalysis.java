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

package org.e2immu.analyser.model;

import org.e2immu.analyser.analyser.VariableProperty;
import org.e2immu.analyser.objectflow.ObjectFlow;
import org.e2immu.analyser.parser.E2ImmuAnnotationExpressions;
import org.e2immu.analyser.parser.Messages;
import org.e2immu.analyser.util.AddOnceSet;
import org.e2immu.analyser.util.SetOnce;
import org.e2immu.analyser.util.SetOnceMap;
import org.e2immu.annotation.AnnotationMode;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.e2immu.analyser.util.Logger.LogTarget.RESOLVE;
import static org.e2immu.analyser.util.Logger.log;

public interface TypeAnalysis extends IAnalysis {

    Set<ObjectFlow> getConstantObjectFlows();

    Map<String, Value> getApprovedPreconditions();

    default boolean isEventual() {
        return !getApprovedPreconditions().isEmpty();
    }

    default Set<String> marksRequiredForImmutable() {
        return getApprovedPreconditions().keySet().stream().collect(Collectors.toUnmodifiableSet());
    }

    default String allLabelsRequiredForImmutable() {
        return String.join(",", marksRequiredForImmutable());
    }

    /**
     * @return null when not yet set
     */
    Set<ParameterizedType> getImplicitlyImmutableDataTypes();
}
