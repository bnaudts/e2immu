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

import com.google.common.collect.ImmutableSet;
import org.e2immu.analyser.analyser.check.CheckConstant;
import org.e2immu.analyser.analyser.check.CheckLinks;
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.model.abstractvalue.VariableValue;
import org.e2immu.analyser.model.expression.EmptyExpression;
import org.e2immu.analyser.parser.Message;
import org.e2immu.analyser.parser.TypeContext;
import org.e2immu.analyser.util.SetOnceMap;
import org.e2immu.annotation.E1Immutable;
import org.e2immu.annotation.Linked;
import org.e2immu.annotation.NotModified;
import org.e2immu.annotation.NotNull;

import java.util.HashSet;
import java.util.Set;

import static org.e2immu.analyser.model.value.UnknownValue.NO_VALUE;
import static org.e2immu.analyser.model.value.UnknownValue.UNKNOWN_VALUE;
import static org.e2immu.analyser.util.Logger.LogTarget.*;
import static org.e2immu.analyser.util.Logger.log;


public class FieldAnalyser {
    private final TypeContext typeContext;

    public FieldAnalyser(TypeContext typeContext) {
        this.typeContext = typeContext;
    }

    public boolean analyse(FieldInfo fieldInfo, Variable thisVariable, VariableProperties fieldProperties) {
        log(ANALYSER, "Analysing field {}", fieldInfo.fullyQualifiedName());

        boolean changes = false;
        TypeInspection typeInspection = fieldInfo.owner.typeInspection.get();
        SetOnceMap<AnnotationExpression, Boolean> annotations = fieldInfo.fieldAnalysis.annotations;

        // STEP 1: THE INITIALISER

        Value value;
        boolean haveInitialiser;
        if (fieldInfo.fieldInspection.get().initialiser.isSet()) {
            FieldInspection.FieldInitialiser fieldInitialiser = fieldInfo.fieldInspection.get().initialiser.get();
            if (fieldInitialiser.initialiser != EmptyExpression.EMPTY_EXPRESSION) {
                FieldReference fieldReference = new FieldReference(fieldInfo, fieldInfo.isStatic() ? null : thisVariable);
                VariableProperties localVariableProperties;
                if (fieldInitialiser.implementationOfSingleAbstractMethod == null) {
                    localVariableProperties = fieldProperties;
                } else {
                    localVariableProperties = fieldProperties.copyWithCurrentMethod(fieldInitialiser.implementationOfSingleAbstractMethod);
                }
                value = fieldInitialiser.initialiser.evaluate(localVariableProperties, EvaluationVisitor.NO_VISITOR);
                log(FINAL, "Set initialiser of field {} to {}", fieldInfo.fullyQualifiedName(), value);
                fieldProperties.setValue(fieldReference, value);
                haveInitialiser = true;
            } else {
                value = NO_VALUE; // initialiser set, but to empty expression
                haveInitialiser = false;
            }
        } else {
            value = NO_VALUE;
            haveInitialiser = true;
        }

        // STEP 2: EFFECTIVELY FINAL: @Final

        if (!annotations.isSet(typeContext.e1Immutable.get())) {
            boolean isExplicitlyFinal = fieldInfo.isExplicitlyFinal();
            if (isExplicitlyFinal) {
                annotations.put(typeContext.e1Immutable.get(), true);
                log(FINAL, "Mark field {} as effectively final, because explicitly so, value {}",
                        fieldInfo.fullyQualifiedName(), value);
                changes = true;
            } else {
                Boolean isModifiedOutsideConstructors = typeInspection.methods.stream()
                        .filter(m -> !m.isPrivate() || m.isCalledFromNonPrivateMethod())
                        .map(m -> m.methodAnalysis.fieldAssignments.getOtherwiseNull(fieldInfo))
                        .reduce(false, TypeAnalyser.TERNARY_OR);

                if (isModifiedOutsideConstructors == null) {
                    log(DELAYED, "Cannot yet conclude if {} is effectively final", fieldInfo.fullyQualifiedName());
                } else {
                    annotations.put(typeContext.e1Immutable.get(), !isModifiedOutsideConstructors);
                    log(FINAL, "Mark field {} as " + (isModifiedOutsideConstructors ? "not " : "") +
                            "effectively final, not modified outside constructors", fieldInfo.fullyQualifiedName());
                    changes = true;
                }
            }
        }

        // STEP 3: EFFECTIVELY FINAL VALUE, and @Constant

        if (fieldInfo.isE1Immutable(typeContext) == Boolean.TRUE && !fieldInfo.fieldAnalysis.effectivelyFinalValue.isSet()) {
            // find the constructors where the value is set; if they're all set to the same value,
            // we can set the initial value; also take into account the value of the initialiser, if it is there
            Value consistentValue = value;
            for (MethodInfo method : typeInspection.methodsAndConstructors()) {
                if (method.methodAnalysis.fieldAssignments.getOtherwiseNull(fieldInfo) == Boolean.TRUE) {
                    if (method.methodAnalysis.fieldAssignmentValues.isSet(fieldInfo)) {
                        Value assignment = method.methodAnalysis.fieldAssignmentValues.get(fieldInfo);
                        if (consistentValue == NO_VALUE) consistentValue = assignment;
                        else if (!consistentValue.equals(assignment)) {
                            log(CONSTANT, "Cannot set consistent value for field {}, have {} and {}",
                                    fieldInfo.fullyQualifiedName(), consistentValue, assignment);
                            fieldInfo.fieldAnalysis.effectivelyFinalValue.set(UNKNOWN_VALUE);
                            annotations.put(typeContext.constant.get(), false);
                            return true;
                        }
                    } else {
                        log(DELAYED, "Delay consistent value for field {}", fieldInfo.fullyQualifiedName());
                        consistentValue = NO_VALUE;
                        break;
                    }
                }
            }
            if (consistentValue != NO_VALUE) {
                Value valueToSet;
                if (consistentValue instanceof org.e2immu.analyser.model.Constant) {
                    valueToSet = consistentValue;
                    AnnotationExpression constantAnnotation = CheckConstant.createConstantAnnotation(typeContext, value);
                    annotations.put(constantAnnotation, true);
                    log(CONSTANT, "Added @Constant annotation on field {}", fieldInfo.fullyQualifiedName());
                } else {
                    valueToSet = new VariableValue(new FieldReference(fieldInfo, thisVariable));
                    annotations.put(typeContext.constant.get(), false);
                    log(CONSTANT, "Marked that field {} cannot be @Constant", fieldInfo.fullyQualifiedName());
                }
                fieldInfo.fieldAnalysis.effectivelyFinalValue.set(valueToSet);
                log(CONSTANT, "Setting initial value of effectively final of field {} to {}",
                        fieldInfo.fullyQualifiedName(), consistentValue);
                changes = true;
            }
        }

        // STEP 4: @NotNull

        if (!annotations.isSet(typeContext.notNull.get())) {
            Boolean isNotNullValue;
            // to avoid chicken and egg problems we do not look at effectivelyFinalValue, because that one replaces
            // the real value with a generic VariableValue, relying on @NotNull
            boolean allAssignmentValuesDefined = typeInspection.constructorAndMethodStream().allMatch(m ->
                    m.methodAnalysis.fieldAssignments.isSet(fieldInfo) &&
                            (!m.methodAnalysis.fieldAssignments.get(fieldInfo) || m.methodAnalysis.fieldAssignmentValues.isSet(fieldInfo)));
            if (allAssignmentValuesDefined) {
                Boolean allAssignmentValuesNotNull = typeInspection.constructorAndMethodStream()
                        .filter(m -> m.methodAnalysis.fieldAssignments.get(fieldInfo) && m.methodAnalysis.fieldAssignmentValues.isSet(fieldInfo))
                        .map(m -> m.methodAnalysis.fieldAssignmentValues.get(fieldInfo).isNotNull(fieldProperties))
                        .reduce(true, TypeAnalyser.TERNARY_AND);
                if (allAssignmentValuesNotNull == null) {
                    isNotNullValue = null; // delay
                } else {
                    if(!haveInitialiser) {
                        isNotNullValue = allAssignmentValuesNotNull;
                    } else {
                        if (value == NO_VALUE) {
                            isNotNullValue = null; // delay
                        } else {
                            Boolean initialiserIsNotNull = value.isNotNull(fieldProperties);
                            if (initialiserIsNotNull == null) {
                                isNotNullValue = null; // delay
                            } else {
                                // this is the real one!
                                isNotNullValue = initialiserIsNotNull && allAssignmentValuesNotNull;
                            }
                        }
                    }
                }
            } else {
                isNotNullValue = null; // delay
            }
            if (isNotNullValue == null) {
                log(DELAYED, "Delaying @NotNull on field {}", fieldInfo.fullyQualifiedName());
            } else {
                annotations.put(typeContext.notNull.get(), isNotNullValue);
                log(NOT_NULL, "Mark field {} as " + (isNotNullValue ? "" : "NOT ") + "@NotNull",
                        fieldInfo.fullyQualifiedName(), isNotNullValue);
                changes = true;
            }
        }


        // STEP 5: @NotModified

        FieldReference fieldReference = new FieldReference(fieldInfo, fieldProperties.thisVariable);
        if (!annotations.isSet(typeContext.notModified.get())) {

            // first check if we're dealing with fields of ENUM's; they're not modifiable at all
            if (fieldInfo.owner.typeInspection.get().typeNature == TypeNature.ENUM) {
                annotations.put(typeContext.notModified.get(), true);
                log(MODIFY_CONTENT, "Mark field {} of enum as @NotModified", fieldInfo.fullyQualifiedName());
                changes = true;
            } else if (fieldInfo.isE1Immutable(typeContext) == Boolean.FALSE) {
                annotations.put(typeContext.notModified.get(), false);
                log(MODIFY_CONTENT, "Mark field {} as NOT @NotModified, because it is not @Final", fieldInfo.fullyQualifiedName());
                changes = true;
            } else if (fieldInfo.isE1Immutable(typeContext) == Boolean.TRUE) {
                boolean allContentModificationsDefined = typeInspection.constructorAndMethodStream().allMatch(m ->
                        m.methodAnalysis.fieldRead.isSet(fieldInfo) &&
                                (!m.methodAnalysis.fieldRead.get(fieldInfo) || m.methodAnalysis.contentModifications.isSet(fieldReference)));
                if (allContentModificationsDefined) {
                    boolean notModified = typeInspection.constructorAndMethodStream()
                            .filter(m -> m.methodAnalysis.fieldRead.get(fieldInfo))
                            .noneMatch(m -> m.methodAnalysis.contentModifications.get(fieldReference));
                    annotations.put(typeContext.notModified.get(), notModified);
                    log(MODIFY_CONTENT, "Mark field {} as " + (notModified ? "" : "not ") +
                            "@NotModified", fieldInfo.fullyQualifiedName());
                    changes = true;
                } else {
                    log(DELAYED, "Cannot yet conclude if field {}'s contents have been modified, not all read or defined",
                            fieldInfo.fullyQualifiedName());
                }
            }
        }

        // STEP 6: @Linked, variablesLinkedToMe

        if (!fieldInfo.fieldAnalysis.variablesLinkedToMe.isSet()) {
            boolean allDefined = typeInspection.constructorAndMethodStream()
                    .allMatch(m -> m.methodAnalysis.fieldAssignments.isSet(fieldInfo) &&
                            (!m.methodAnalysis.fieldAssignments.get(fieldInfo) ||
                                    m.methodAnalysis.fieldsLinkedToFieldsAndVariables.isSet(fieldReference)));
            if (allDefined) {
                Set<Variable> links = new HashSet<>();
                typeInspection.constructorAndMethodStream().forEach(m -> {
                    if (m.methodAnalysis.fieldsLinkedToFieldsAndVariables.isSet(fieldReference))
                        links.addAll(m.methodAnalysis.fieldsLinkedToFieldsAndVariables.get(fieldReference));
                });
                fieldInfo.fieldAnalysis.variablesLinkedToMe.set(ImmutableSet.copyOf(links));
                log(LINKED_VARIABLES, "Set links of {} to [{}]", fieldInfo.fullyQualifiedName(), Variable.detailedString(links));

                AnnotationExpression linkAnnotation = CheckLinks.createLinkAnnotation(typeContext, links);
                annotations.put(linkAnnotation, !links.isEmpty());
                changes = true;
            }
        }
        return changes;
    }

    public void check(FieldInfo fieldInfo) {
        log(ANALYSER, "Checking field {}", fieldInfo.fullyQualifiedName());

        // TODO check the correct field name
        fieldInfo.error(Linked.class, typeContext.linked.get()).ifPresent(mustBeAbsent ->
                typeContext.addMessage(Message.Severity.ERROR, "Field " + fieldInfo.fullyQualifiedName() +
                        " should " + (mustBeAbsent ? "not " : "") + "be marked @Linked"));

        fieldInfo.error(NotModified.class, typeContext.notModified.get()).ifPresent(mustBeAbsent ->
                typeContext.addMessage(Message.Severity.ERROR, "Field " + fieldInfo.fullyQualifiedName() +
                        " should " + (mustBeAbsent ? "not " : "") + "be marked @NotModified"));

        fieldInfo.error(NotNull.class, typeContext.notNull.get()).ifPresent(mustBeAbsent ->
                typeContext.addMessage(Message.Severity.ERROR, "Field " + fieldInfo.fullyQualifiedName() +
                        " should " + (mustBeAbsent ? "not " : "") + "be marked @NotNull"));

        fieldInfo.error(E1Immutable.class, typeContext.e1Immutable.get()).ifPresent(mustBeAbsent ->
                typeContext.addMessage(Message.Severity.ERROR, "Field " + fieldInfo.fullyQualifiedName() +
                        " should " + (mustBeAbsent ? "not " : "") + "be marked @E1Immutable"));

        if (fieldInfo.fieldInspection.get().modifiers.contains(FieldModifier.PRIVATE)) {
            if (!fieldInfo.isStatic()) {
                boolean readInMethods = fieldInfo.owner.typeInspection.get().methods.stream()
                        .anyMatch(m -> m.methodAnalysis.fieldRead.getOtherwiseNull(fieldInfo) == Boolean.TRUE);
                if (!readInMethods) {
                    typeContext.addMessage(Message.Severity.ERROR, "Private field " + fieldInfo.fullyQualifiedName() +
                            " is not read outside constructors");
                }
            }
        } else if (fieldInfo.fieldAnalysis.annotations.getOtherwiseNull(typeContext.e1Immutable.get()) != Boolean.TRUE) {
            typeContext.addMessage(Message.Severity.ERROR, "Non-private field " + fieldInfo.fullyQualifiedName() +
                    " is not effectively final (@E1Immutable)");
        }

        if (fieldInfo.fieldAnalysis.effectivelyFinalValue.isSet()) {
            Value fieldValue = fieldInfo.fieldAnalysis.effectivelyFinalValue.get();
            CheckConstant.checkConstant(fieldValue, fieldInfo.type, fieldInfo.fieldInspection.get().annotations,
                    (valueToTest, typeMsg) -> typeContext.addMessage(Message.Severity.ERROR, "Field " + fieldInfo.fullyQualifiedName() +
                            ": expected @Constant value " + valueToTest + " of type " + typeMsg + ", got " + fieldValue));
        }
    }
}
