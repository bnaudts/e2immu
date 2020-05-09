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

package org.e2immu.analyser.analyser;

import org.e2immu.analyser.model.Level;
import org.e2immu.analyser.model.ParameterInfo;
import org.e2immu.analyser.model.Value;
import org.e2immu.analyser.model.Variable;
import org.e2immu.analyser.model.abstractvalue.VariableValue;
import org.e2immu.analyser.parser.Message;
import org.e2immu.analyser.parser.TypeContext;
import org.e2immu.analyser.util.Lazy;
import org.e2immu.annotation.NotModified;
import org.e2immu.annotation.NotNull;

import java.util.Map;
import java.util.Objects;

import static org.e2immu.analyser.util.Logger.LogTarget.*;
import static org.e2immu.analyser.util.Logger.log;

public class ParameterAnalyser {
    private final TypeContext typeContext;

    public ParameterAnalyser(TypeContext typeContext) {
        this.typeContext = typeContext;
    }

    public void check(ParameterInfo parameterInfo) {
        log(ANALYSER, "Checking parameter {}", parameterInfo.detailedString());

        Lazy<String> where = new Lazy<>(() -> "In method " +
                parameterInfo.parameterInspection.get().owner.fullyQualifiedName() + ", " +
                parameterInfo.detailedString());

        parameterInfo.error(NotModified.class, typeContext.notModified.get()).ifPresent(mustBeAbsent ->
                typeContext.addMessage(Message.Severity.ERROR, where.get() +
                        ": parameter should " + (mustBeAbsent ? "not " : "") + "be marked @NotModified"));

        parameterInfo.error(NotNull.class, typeContext.notNull.get()).ifPresent(mustBeAbsent ->
                typeContext.addMessage(Message.Severity.ERROR, where.get() +
                        ": parameter should " + (mustBeAbsent ? "not " : "") + "be marked @NotNull"));
    }

    public boolean notModified(ParameterInfo parameterInfo, Boolean directContentModification) {
        if (!parameterInfo.parameterAnalysis.isNotModifiedByDefinition()) {
            if (directContentModification != null) {
                boolean notModified = !directContentModification;
                if (parameterInfo.parameterAnalysis.getProperty(VariableProperty.NOT_MODIFIED) == Level.DELAY) {
                    log(NOT_MODIFIED, "Mark {} of {} " + (notModified ? "" : "NOT") + " @NotModified",
                            parameterInfo.detailedString(),
                            parameterInfo.parameterInspection.get().owner.distinguishingName());
                    parameterInfo.parameterAnalysis.setProperty(VariableProperty.NOT_MODIFIED, notModified);
                    return true;
                }
            } else {
                log(DELAYED, "Delaying setting parameter not modified on {}", parameterInfo.detailedString());
            }
        }
        return false;
    }

}
