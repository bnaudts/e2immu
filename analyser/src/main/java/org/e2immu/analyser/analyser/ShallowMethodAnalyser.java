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
import org.e2immu.analyser.parser.*;
import org.e2immu.analyser.util.SMapList;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class ShallowMethodAnalyser {
    private final Primitives primitives;
    private final AnalysisProvider analysisProvider;
    private final E2ImmuAnnotationExpressions e2ImmuAnnotationExpressions;
    private final Messages messages = new Messages();

    public ShallowMethodAnalyser(Primitives primitives,
                                 AnalysisProvider analysisProvider,
                                 E2ImmuAnnotationExpressions e2ImmuAnnotationExpressions) {
        this.e2ImmuAnnotationExpressions = e2ImmuAnnotationExpressions;
        this.analysisProvider = analysisProvider;
        this.primitives = primitives;
    }


    public MethodAnalysisImpl.Builder copyAnnotationsIntoMethodAnalysisProperties(MethodInfo methodInfo) {
        Map<WithInspectionAndAnalysis, Map<AnnotationExpression, List<MethodInfo>>> map = collectAnnotations(methodInfo);

        MethodInspection methodInspection = methodInfo.methodInspection.get();

        List<ParameterAnalysis> parameterAnalyses = new ArrayList<>();

        methodInspection.getParameters().forEach(parameterInfo -> {
            ParameterAnalysisImpl.Builder builder = new ParameterAnalysisImpl.Builder(primitives, analysisProvider, parameterInfo);
            messages.addAll(builder.fromAnnotationsIntoProperties(Analyser.AnalyserIdentification.PARAMETER, true,
                    map.getOrDefault(parameterInfo, Map.of()).keySet(), e2ImmuAnnotationExpressions));
            parameterAnalyses.add(builder); // building will take place when the method analysis is built
        });

        MethodAnalysisImpl.Builder methodAnalysisBuilder = new MethodAnalysisImpl.Builder(
                Analysis.AnalysisMode.CONTRACTED, primitives,
                analysisProvider, InspectionProvider.DEFAULT, methodInfo, parameterAnalyses);

        messages.addAll(methodAnalysisBuilder.fromAnnotationsIntoProperties(Analyser.AnalyserIdentification.METHOD,
                true, map.getOrDefault(methodInfo, Map.of()).keySet(), e2ImmuAnnotationExpressions));
        return methodAnalysisBuilder;
    }

    private Map<WithInspectionAndAnalysis, Map<AnnotationExpression, List<MethodInfo>>> collectAnnotations(MethodInfo methodInfo) {
        Map<WithInspectionAndAnalysis, Map<AnnotationExpression, List<MethodInfo>>> map = new HashMap<>();

        Map<AnnotationExpression, List<MethodInfo>> methodMap = new HashMap<>();
        map.put(methodInfo, methodMap);

        Stream.concat(Stream.of(methodInfo), methodInfo.methodResolution.get(methodInfo.fullyQualifiedName).overrides().stream()).forEach(mi -> {

            MethodInspection mii = mi.methodInspection.get();
            mii.getAnnotations().forEach(annotationExpression -> SMapList.add(methodMap, annotationExpression, mi));

            mii.getParameters().forEach(parameterInfo -> {
                Map<AnnotationExpression, List<MethodInfo>> parameterMap = map.computeIfAbsent(parameterInfo, k -> new HashMap<>());
                parameterInfo.parameterInspection.get().getAnnotations().forEach(annotationExpression ->
                        SMapList.add(parameterMap, annotationExpression, mi));
            });
        });

        map.forEach(this::checkContradictions);
        return map;
    }

    private void checkContradictions(WithInspectionAndAnalysis where, Map<AnnotationExpression, List<MethodInfo>> annotations) {
        if (annotations.size() < 2) return;
        checkContradictions(where, annotations, e2ImmuAnnotationExpressions.notModified, e2ImmuAnnotationExpressions.modified);
        checkContradictions(where, annotations, e2ImmuAnnotationExpressions.notNull, e2ImmuAnnotationExpressions.nullable);
    }

    private void checkContradictions(WithInspectionAndAnalysis where, Map<AnnotationExpression, List<MethodInfo>> annotations,
                                     AnnotationExpression left, AnnotationExpression right) {
        List<MethodInfo> leftMethods = annotations.getOrDefault(left, List.of());
        List<MethodInfo> rightMethods = annotations.getOrDefault(right, List.of());
        if (!leftMethods.isEmpty() && !rightMethods.isEmpty()) {
            messages.add(Message.newMessage(new Location(where), Message.Label.CONTRADICTING_ANNOTATIONS,
                    left + " in " + leftMethods.stream().map(mi -> mi.fullyQualifiedName).collect(Collectors.joining("; ")) +
                            "; " + right + " in " + rightMethods.stream().map(mi -> mi.fullyQualifiedName).collect(Collectors.joining("; "))));
        }
    }

    public Stream<Message> getMessageStream() {
        return messages.getMessageStream();
    }
}
