package org.e2immu.analyser.analyser;

import org.e2immu.analyser.model.*;
import org.e2immu.analyser.model.statement.ReturnStatement;
import org.e2immu.analyser.model.value.UnknownValue;
import org.e2immu.analyser.parser.Message;
import org.e2immu.analyser.parser.TypeContext;
import org.e2immu.analyser.util.SetUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

import static org.e2immu.analyser.util.Logger.LogTarget.*;
import static org.e2immu.analyser.util.Logger.log;

public class ComputeLinking {
    private static final Logger LOGGER = LoggerFactory.getLogger(ComputeLinking.class);

    public final ParameterAnalyser parameterAnalyser;
    public final TypeContext typeContext;

    public ComputeLinking(TypeContext typeContext, ParameterAnalyser parameterAnalyser) {
        this.parameterAnalyser = parameterAnalyser;
        this.typeContext = typeContext;
    }

    // we need a recursive structure because local variables can be defined in blocks, a little later,
    // they disappear again. But, we should also be able to add properties simply for a block, so that those
    // properties disappear when that level disappears

    public boolean computeVariablePropertiesOfMethod(List<NumberedStatement> statements, MethodInfo methodInfo,
                                                     VariableProperties methodProperties) {
        boolean changes = false;
        try {
            MethodAnalysis methodAnalysis = methodInfo.methodAnalysis;
            StatementAnalyser statementAnalyser = new StatementAnalyser(typeContext, methodInfo);
            NumberedStatement startStatement = statements.get(0);
            if (statementAnalyser.computeVariablePropertiesOfBlock(startStatement, methodProperties)) changes = true;

            if (!methodAnalysis.localMethodsCalled.isSet()) {
                methodAnalysis.localMethodsCalled.set(statementAnalyser.computeLocalMethodsCalled(statements));
            }
            if (!methodAnalysis.returnStatements.isSet()) {
                methodAnalysis.returnStatements.set(statementAnalyser.extractReturnStatements(startStatement));
            }

            // this method computes, unless delayed, the values for
            // - fieldAssignments
            // - fieldAssignmentValues
            // - fieldsRead
            if (computeFieldAssignmentsFieldsRead(methodInfo, methodProperties)) changes = true;

            // this method computes, unless delayed, the values for
            // - linksComputed
            // - variablesLinkedToFieldsAndParameters
            // - fieldsLinkedToFieldsAndVariables
            if (establishLinks(methodInfo, methodAnalysis, methodProperties)) changes = true;
            if (!methodInfo.isConstructor && updateVariablesLinkedToMethodResult(statements, methodInfo, methodAnalysis))
                changes = true;

            if (computeContentModifications(methodInfo, methodAnalysis, methodProperties)) changes = true;
            if (checkParameterAssignmentError(methodInfo, methodProperties)) changes = true;

            return changes;
        } catch (RuntimeException rte) {
            LOGGER.warn("Caught exception in linking computation, method {}", methodInfo.fullyQualifiedName());
            throw rte;
        }
    }

    /*
     Relies on
     - numberedStatement.linkedVariables, which should return us all the variables involved in the return statement
            it does so by computing the linkedVariables of the evaluation of the expression in the return statement
     - for fields among the linkedVariables: fieldAnalysis.variablesLinkedToMe,
       which in turn depends on fieldAssignments and fieldsLinkedToFieldsAndVariables of ALL OTHER methods
     - for local variables: variablesLinkedToFieldsAndParameters for this method

     sets variablesLinkedToMethodResult, and @Linked on or off dependent on whether the set is empty or not
    */

    private boolean updateVariablesLinkedToMethodResult(List<NumberedStatement> numberedStatements,
                                                        MethodInfo methodInfo, MethodAnalysis methodAnalysis) {

        if (methodAnalysis.variablesLinkedToMethodResult.isSet()) return false;

        Set<Variable> variables = new HashSet<>();
        for (NumberedStatement numberedStatement : numberedStatements) {
            if (numberedStatement.statement instanceof ReturnStatement) {
                if (numberedStatement.variablesLinkedToReturnValue.isSet()) { // this implies the statement is a return statement
                    for (Variable variable : numberedStatement.variablesLinkedToReturnValue.get()) {
                        Set<Variable> dependencies;
                        if (variable instanceof FieldReference) {
                            if (!((FieldReference) variable).fieldInfo.fieldAnalysis.variablesLinkedToMe.isSet()) {
                                log(DELAYED, "Dependencies of {} have not yet been established", variable.detailedString());
                                return false;
                            }
                            dependencies = SetUtil.immutableUnion(((FieldReference) variable).fieldInfo.fieldAnalysis.variablesLinkedToMe.get(),
                                    Set.of(variable));
                        } else if (variable instanceof ParameterInfo) {
                            dependencies = Set.of(variable);
                        } else if (variable instanceof LocalVariableReference) {
                            if (!methodAnalysis.variablesLinkedToFieldsAndParameters.isSet()) {
                                log(DELAYED, "Delaying variables linked to method result, local variable's linkage not yet known");
                                return false;
                            }
                            dependencies = methodAnalysis.variablesLinkedToFieldsAndParameters.get().getOrDefault(variable, Set.of());
                        } else {
                            dependencies = Set.of();
                        }
                        log(LINKED_VARIABLES, "Dependencies of {} are [{}]", variable.detailedString(), Variable.detailedString(dependencies));
                        variables.addAll(dependencies);
                    }
                } else {
                    log(DELAYED, "Not yet ready to compute linked variables of result of method {}", methodInfo.fullyQualifiedName());
                    return false;
                }
            }
        }
        methodAnalysis.variablesLinkedToMethodResult.set(variables);
        methodAnalysis.setProperty(VariableProperty.LINKED, !variables.isEmpty());
        log(LINKED_VARIABLES, "Set variables linked to result of {} to [{}]", methodInfo.fullyQualifiedName(), Variable.detailedString(variables));
        return true;
    }

    /*
      goal: we need to establish that in this method, recursively, a given field is linked to one or more fields or parameters
      we need to find out if a parameter is linked, recursively, to another field or parameter
      local variables need to be taken out of the loop

      in essence: moving from the dependency graph to the MethodAnalysis.variablesLinkedToFieldsAndParameters data structure
      gets rid of local vars and follows links transitively

      To answer how this method deals with unevaluated links (links that can do better when one of their components are != NO_VALUE)
      two dependency graphs have been created: a best-case one where some annotations on the current type have been discovered
      already, and a worst-case one where we do not take them into account.

      Why? if a method is called, as part of the value, and we do not yet know anything about the independence (@Independent) of that method,
      the outcome of linkedVariables() can be seriously different. If there is a difference between the transitive
      closures of best and worst, we should delay.

      On top of this, fields whose @Final status has not been set yet, are represented (as currentValues in the evaluation context)
      by VariableValues with a special boolean flag, instead of NO_VALUES.
      This allows us to delay computations without completely losing the dependency structure as constructed up by method calls.
      It is that dependency structure that we need to be able to distinguish between best and worst case.

    */

    private static boolean establishLinks(MethodInfo methodInfo, MethodAnalysis methodAnalysis, VariableProperties methodProperties) {
        if (methodAnalysis.variablesLinkedToFieldsAndParameters.isSet()) return false;

        // final fields need to have a value set; all the others act as local variables
        boolean someFieldsReadOrAssignedHaveNotBeenEvaluated = methodProperties.variableProperties().stream()
                .anyMatch(av -> av.variable instanceof FieldReference &&
                        Level.haveTrueAt(av.getProperty(VariableProperty.IMMUTABLE), Level.E1IMMUTABLE) &&
                        !((FieldReference) av.variable).fieldInfo.fieldAnalysis.effectivelyFinalValue.isSet());
        if (someFieldsReadOrAssignedHaveNotBeenEvaluated) {
            log(DELAYED, "Some effectively final fields have not yet been evaluated -- delaying establishing links");
            return false;
        }
        if (!methodProperties.dependencyGraphBestCase.equalTransitiveTerminals(methodProperties.dependencyGraphWorstCase)) {
            log(DELAYED, "Best and worst case dependency graph transitive terminal sets differ -- delaying establishing links");
            return false;
        }
        AtomicBoolean changes = new AtomicBoolean();
        Map<Variable, Set<Variable>> variablesLinkedToFieldsAndParameters = new HashMap<>();

        methodProperties.dependencyGraphBestCase.visit((variable, dependencies) -> {
            Set<Variable> fieldAndParameterDependencies = new HashSet<>(methodProperties.dependencyGraphBestCase.dependencies(variable));
            fieldAndParameterDependencies.removeIf(v -> !(v instanceof FieldReference) && !(v instanceof ParameterInfo));
            if (dependencies != null) {
                dependencies.stream().filter(d -> d instanceof ParameterInfo).forEach(fieldAndParameterDependencies::add);
            }
            fieldAndParameterDependencies.remove(variable); // removing myself
            variablesLinkedToFieldsAndParameters.put(variable, fieldAndParameterDependencies);
            log(DEBUG_LINKED_VARIABLES, "Set terminals of {} in {} to [{}]", variable.detailedString(),
                    methodInfo.fullyQualifiedName(), Variable.detailedString(fieldAndParameterDependencies));

            if (variable instanceof FieldReference) {
                methodInfo.methodAnalysis.fieldsLinkedToFieldsAndVariables.put(variable, fieldAndParameterDependencies);
                changes.set(true);
                log(LINKED_VARIABLES, "Decided on links of {} in {} to [{}]", variable.detailedString(),
                        methodInfo.fullyQualifiedName(), Variable.detailedString(fieldAndParameterDependencies));
            }
        });
        log(LINKED_VARIABLES, "Set variablesLinkedToFieldsAndParameters to true for {}", methodInfo.fullyQualifiedName());
        methodAnalysis.variablesLinkedToFieldsAndParameters.set(variablesLinkedToFieldsAndParameters);
        return true;
    }

    private boolean computeContentModifications(MethodInfo methodInfo, MethodAnalysis methodAnalysis, VariableProperties methodProperties) {
        if (!methodAnalysis.variablesLinkedToFieldsAndParameters.isSet()) return false;

        boolean changes = false;
        for (AboutVariable aboutVariable : methodProperties.variableProperties()) {
            Set<Variable> linkedVariables = allVariablesLinkedToIncludingMyself(methodAnalysis.variablesLinkedToFieldsAndParameters.get(), aboutVariable.variable);
            for (Variable linkedVariable : linkedVariables) {
                if (linkedVariable instanceof FieldReference) {
                    if (!methodAnalysis.contentModifications.isSet(linkedVariable)) {
                        FieldInfo fieldInfo = ((FieldReference) linkedVariable).fieldInfo;
                        Boolean directContentModification = summarizeModification(methodProperties, linkedVariables, false);
                        boolean directlyModifiedField = directContentModification == Boolean.TRUE
                                && methodAnalysis.fieldRead.isSet(fieldInfo) // it is a field local to us, or it has been read
                                && methodAnalysis.fieldRead.get(fieldInfo); // if local, it will be set, but it has to be true
                        log(DEBUG_MODIFY_CONTENT, "Mark that the content of {} has {}been modified in {}",
                                linkedVariable.detailedString(),
                                directContentModification == null ? "?? " :
                                        directContentModification ? "" : "NOT ",
                                methodInfo.fullyQualifiedName());
                        methodAnalysis.contentModifications.put(linkedVariable, directlyModifiedField);
                        changes = true;
                    }
                } else if (linkedVariable instanceof ParameterInfo) {
                    Boolean directContentModification = summarizeModification(methodProperties, linkedVariables, true);
                    parameterAnalyser.notModified((ParameterInfo) linkedVariable, directContentModification);
                }
            }
        }
        return changes;
    }

    private Boolean summarizeModification(VariableProperties methodProperties, Set<Variable> linkedVariables, boolean lookAtFields) {
        boolean hasDelays = false;
        for (Variable variable : linkedVariables) {

            int contentModification = methodProperties.getProperty(variable, VariableProperty.CONTENT_MODIFIED);
            if (Level.haveTrueAt(contentModification, 1)) return true;
            if (Level.haveTrueAt(contentModification, 0)) hasDelays = true;

            // This piece of code is really required for parameters that are linked to fields,
            // which end up not @NotModified, so that the parameter should also not be @NotModified
            if (lookAtFields || !(variable instanceof FieldReference)) {
                int notModified = methodProperties.getProperty(variable, VariableProperty.NOT_MODIFIED);
                if (notModified == Level.FALSE) return true;
                if (notModified == Level.DELAY) hasDelays = true;
            }
        }
        return hasDelays ? null : false;
    }

    private static Set<Variable> allVariablesLinkedToIncludingMyself(Map<Variable, Set<Variable>> variablesLinkedToFieldsAndParameters,
                                                                     Variable variable) {
        Set<Variable> result = new HashSet<>();
        recursivelyAddLinkedVariables(variablesLinkedToFieldsAndParameters, variable, result);
        return result;
    }

    private static void recursivelyAddLinkedVariables(Map<Variable, Set<Variable>> variablesLinkedToFieldsAndParameters,
                                                      Variable variable,
                                                      Set<Variable> result) {
        if (result.contains(variable)) return;
        result.add(variable);
        Set<Variable> linked = variablesLinkedToFieldsAndParameters.get(variable);
        if (linked != null) {
            for (Variable v : linked) recursivelyAddLinkedVariables(variablesLinkedToFieldsAndParameters, v, result);
        }
        // reverse linking
        List<Variable> reverse = variablesLinkedToFieldsAndParameters.entrySet()
                .stream().filter(e -> e.getValue().contains(variable)).map(Map.Entry::getKey).collect(Collectors.toList());
        reverse.forEach(v -> recursivelyAddLinkedVariables(variablesLinkedToFieldsAndParameters, v, result));
    }

    private boolean checkParameterAssignmentError(MethodInfo methodInfo, VariableProperties methodProperties) {
        boolean changes = false;
        for (AboutVariable aboutVariable : methodProperties.variableProperties()) {
            if (aboutVariable.variable instanceof ParameterInfo) {
                ParameterInfo parameterInfo = (ParameterInfo) aboutVariable.variable;
                boolean assigned = Level.haveTrueAt(methodProperties.getProperty(parameterInfo, VariableProperty.ASSIGNED), 1);
                if (assigned && !methodInfo.methodAnalysis.parameterAssignments.isSet(parameterInfo)) {
                    typeContext.addMessage(Message.Severity.ERROR,
                            "Parameter " + aboutVariable.name + " should not be assigned to");
                    methodInfo.methodAnalysis.parameterAssignments.put(parameterInfo, true);
                    changes = true;
                }
            }
        }
        return changes;
    }

    private static boolean computeFieldAssignmentsFieldsRead(MethodInfo methodInfo, VariableProperties methodProperties) {
        boolean changes = false;
        MethodAnalysis methodAnalysis = methodInfo.methodAnalysis;
        for (AboutVariable aboutVariable : methodProperties.variableProperties()) {
            Variable variable = aboutVariable.variable;
            if (variable instanceof FieldReference) {
                FieldInfo fieldInfo = ((FieldReference) variable).fieldInfo;
                int assigned = aboutVariable.getProperty(VariableProperty.ASSIGNED);

                boolean isAssigned = Level.haveTrueAt(assigned, 0);
                boolean isAssignedMultipleTimes = Level.haveTrueAt(assigned, 1);

                if (!methodAnalysis.fieldAssignments.isSet(fieldInfo)) {
                    methodAnalysis.fieldAssignments.put(fieldInfo, isAssigned);
                    log(ASSIGNMENT, "Mark that {} is assigned to? {} in {}", fieldInfo.name, isAssigned, methodInfo.fullyQualifiedName());
                    changes = true;
                }
                Value currentValue = aboutVariable.getCurrentValue();
                if (currentValue != UnknownValue.NO_VALUE && isAssigned &&
                        !isAssignedMultipleTimes &&
                        !methodAnalysis.fieldAssignmentValues.isSet(fieldInfo)) {
                    log(ASSIGNMENT, "Single assignment of field {} to {}", fieldInfo.fullyQualifiedName(), currentValue);
                    methodAnalysis.fieldAssignmentValues.put(fieldInfo, currentValue);
                    changes = true;
                }
                boolean read = Level.haveTrueAt(aboutVariable.getProperty(VariableProperty.READ), 0);
                if (read && !methodAnalysis.fieldRead.isSet(fieldInfo)) {
                    log(ASSIGNMENT, "Mark that field {} has been read", variable.detailedString());
                    methodAnalysis.fieldRead.put(fieldInfo, true);
                    changes = true;
                }
            }
        }

        for (FieldInfo fieldInfo : methodInfo.typeInfo.typeInspection.get().fields) {
            if (!methodAnalysis.fieldAssignments.isSet(fieldInfo)) {
                methodAnalysis.fieldAssignments.put(fieldInfo, false);
                changes = true;
                log(ASSIGNMENT, "Mark field {} not assigned in {}, not present", fieldInfo.fullyQualifiedName(), methodInfo.name);
            }
            if (!methodAnalysis.fieldRead.isSet(fieldInfo)) {
                methodAnalysis.fieldRead.put(fieldInfo, false);
                log(ASSIGNMENT, "Mark field {} as ignore/not read in {}, not present", fieldInfo.fullyQualifiedName(), methodInfo.name);
                changes = true;
            }
        }
        return changes;
    }
}
