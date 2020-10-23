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

import org.e2immu.analyser.analyser.check.CheckSize;
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.parser.E2ImmuAnnotationExpressions;
import org.e2immu.analyser.parser.Message;
import org.e2immu.analyser.parser.Messages;
import org.e2immu.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.e2immu.analyser.util.Logger.LogTarget.ANALYSER;
import static org.e2immu.analyser.util.Logger.LogTarget.SIZE;
import static org.e2immu.analyser.util.Logger.log;

public class ParameterAnalyser {
    private final Messages messages = new Messages();
    public final ParameterInfo parameterInfo;
    public final ParameterAnalysis parameterAnalysis;

    private Map<FieldInfo, FieldAnalyser> fieldAnalysers;
    private final E2ImmuAnnotationExpressions e2;

    public ParameterAnalyser(ParameterInfo parameterInfo, AnalyserContext analyserContext) {
        this.e2 = analyserContext.getE2ImmuAnnotationExpressions();
        this.parameterInfo = parameterInfo;
        TypeInfo bestType = parameterInfo.parameterizedType.bestTypeInfo();
        parameterAnalysis = new ParameterAnalysis(parameterInfo, analyserContext.getTypeAnalysis(parameterInfo.owner.typeInfo),
                bestType == null ? null : analyserContext.getTypeAnalysis(bestType));
    }

    public ParameterAnalysis getParameterAnalysis() {
        return parameterAnalysis;
    }

    public void initialize(Map<FieldInfo, FieldAnalyser> fieldAnalysers) {
        this.fieldAnalysers = fieldAnalysers;
    }

    public void check() {
        // before we check, we copy the properties into annotations
        parameterAnalysis.transferPropertiesToAnnotations(e2);

        log(ANALYSER, "Checking parameter {}", parameterInfo.fullyQualifiedName());

        check(NotModified.class, e2.notModified.get());
        check(NotNull.class, List.of(e2.notNull.get(),
                e2.notNull1.get(),
                e2.notNull2.get()));
        check(NotNull1.class, List.of(e2.notNull1.get(), e2.notNull2.get()));
        check(NotNull2.class, e2.notNull2.get());
        check(NotModified1.class, e2.notModified1.get());

        // opposites
        check(Nullable.class, e2.nullable.get());
        check(Modified.class, e2.modified.get());

        CheckSize.checkSizeForParameters(messages, parameterInfo, parameterAnalysis);

        checkWorseThanParent();
    }

    private void checkWorseThanParent() {
        for (VariableProperty variableProperty : VariableProperty.CHECK_WORSE_THAN_PARENT) {
            int valueFromOverrides = parameterInfo.owner.methodAnalysis.get().getOverrides().stream()
                    .map(ma -> ma.methodInfo.methodInspection.get().parameters.get(parameterInfo.index))
                    .mapToInt(pi -> pi.parameterAnalysis.get().getProperty(variableProperty)).max().orElse(Level.DELAY);
            int value = parameterAnalysis.getProperty(variableProperty);
            if (valueFromOverrides != Level.DELAY && value != Level.DELAY) {
                boolean complain = variableProperty == VariableProperty.MODIFIED ? value > valueFromOverrides : value < valueFromOverrides;
                if (complain) {
                    messages.add(Message.newMessage(parameterAnalysis.location, Message.WORSE_THAN_OVERRIDDEN_METHOD_PARAMETER,
                            variableProperty.name + ", parameter " + parameterInfo.name));
                }
            }
        }
    }

    private void check(Class<?> annotation, List<AnnotationExpression> annotationExpressions) {
        parameterInfo.error(parameterAnalysis, annotation, annotationExpressions).ifPresent(mustBeAbsent -> {
            Message error = Message.newMessage(new Location(parameterInfo),
                    mustBeAbsent ? Message.ANNOTATION_UNEXPECTEDLY_PRESENT : Message.ANNOTATION_ABSENT, annotation.getSimpleName());
            messages.add(error);
        });
    }

    private void check(Class<?> annotation, AnnotationExpression annotationExpression) {
        parameterInfo.error(parameterAnalysis, annotation, annotationExpression).ifPresent(mustBeAbsent -> {
            Message error = Message.newMessage(new Location(parameterInfo),
                    mustBeAbsent ? Message.ANNOTATION_UNEXPECTEDLY_PRESENT : Message.ANNOTATION_ABSENT, annotation.getSimpleName());
            messages.add(error);
        });
    }

    /**
     * The goal is to ensure that NOT_NULL and SIZE are not unnecessarily delayed. NOT_MODIFIED will be set by the link computer
     * as soon as possible.
     *
     * @return true if changes were made
     */
    public AnalysisStatus analyse() {
        boolean changed = false;
        boolean delays = false;
        // TODO we need to make a distinction between the field, and whether it has been assigned or not
        if (parameterAnalysis.assignedToField.isSet() && !parameterAnalysis.copiedFromFieldToParameters.isSet()) {
            FieldInfo fieldInfo = parameterAnalysis.assignedToField.get();
            FieldAnalysis fieldAnalysis = fieldAnalysers.get(fieldInfo).fieldAnalysis;
            for (VariableProperty variableProperty : VariableProperty.FROM_FIELD_TO_PARAMETER) {
                int inField = fieldAnalysis.getProperty(variableProperty);
                if (inField != Level.DELAY) {
                    int inParameter = parameterAnalysis.getProperty(variableProperty);
                    if (inField > inParameter && verifySizeNotModified(variableProperty)) {
                        log(ANALYSER, "Copying value {} from field {} to parameter {} for property {}", inField,
                                fieldInfo.fullyQualifiedName(), parameterInfo.fullyQualifiedName(), variableProperty);
                        parameterAnalysis.setProperty(variableProperty, inField);
                        changed = true;
                    }
                } else {
                    log(ANALYSER, "Still delaying copiedFromFieldToParameters because of {}", variableProperty);
                    delays = true;
                }
            }
            if (!delays) {
                log(ANALYSER, "No delays anymore on copying from field to parameter");
                parameterAnalysis.copiedFromFieldToParameters.set(true);
                changed = true;
            }
        }

        return delays ? (changed ? AnalysisStatus.PROGRESS : AnalysisStatus.DELAYS) : AnalysisStatus.DONE;
    }

    /**
     * we only copy SIZE when it is also not MODIFIED!
     */
    private boolean verifySizeNotModified(VariableProperty variableProperty) {
        if (variableProperty != VariableProperty.SIZE) return true;
        int modified = parameterAnalysis.getProperty(VariableProperty.MODIFIED);
        boolean accept = modified != Level.TRUE;
        log(SIZE, "To copy the SIZE property on {}, we look at MODIFIED. Copy? {}", parameterInfo.fullyQualifiedName(), accept);
        return accept;
    }

    public Stream<Message> getMessageStream() {
        return messages.getMessageStream();
    }
}
