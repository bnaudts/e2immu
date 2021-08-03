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

package org.e2immu.analyser.model.expression;

import org.e2immu.analyser.analyser.*;
import org.e2immu.analyser.analyser.util.DelayDebugNode;
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.model.expression.util.ExpressionComparator;
import org.e2immu.analyser.model.variable.*;
import org.e2immu.analyser.output.OutputBuilder;
import org.e2immu.analyser.output.Text;
import org.e2immu.analyser.parser.InspectionProvider;
import org.e2immu.analyser.parser.Primitives;
import org.e2immu.analyser.util.SetUtil;

import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/*
 can only be created as the single result value of a non-modifying method

 will be substituted in MethodCall

 Big question: do properties come from the expression, or from the method??
 In the case of Supplier.get() (which is modifying by default), the expression may for example be a parameterized string (t + "abc").
 The string expression is Level 2 immutable, hence not-modified.

 Properties that rely on the return value, should come from the Value. Properties to do with modification, should come from the method.
 */
public class InlinedMethod extends ElementImpl implements Expression {
    private final MethodInfo methodInfo;
    private final Expression expression;
    private final Set<Variable> variablesOfExpression;
    private final boolean containsVariableFields;

    public InlinedMethod(Identifier identifier,
                         MethodInfo methodInfo,
                         Expression expression,
                         Set<Variable> variablesOfExpression,
                         boolean containsVariableFields) {
        super(identifier);
        this.methodInfo = Objects.requireNonNull(methodInfo);
        this.expression = Objects.requireNonNull(expression);
        this.variablesOfExpression = Objects.requireNonNull(variablesOfExpression);
        this.containsVariableFields = containsVariableFields;
    }

    @Override
    public Expression translate(TranslationMap translationMap) {
        Set<Variable> translatedVariables = variablesOfExpression.stream()
                .map(translationMap::translateVariable).collect(Collectors.toUnmodifiableSet());
        // TODO lack of AnalysisProvider, so we copy containsVariableFields rather than computing it
        return new InlinedMethod(identifier, methodInfo, expression.translate(translationMap), translatedVariables,
                containsVariableFields);
    }

    @Override
    public boolean isNumeric() {
        return expression.isNumeric();
    }

    @Override
    public ParameterizedType returnType() {
        return expression.returnType();
    }

    @Override
    public OutputBuilder output(Qualification qualification) {
        return new OutputBuilder().add(new Text("", "/* inline " + methodInfo.name + " */"))
                .add(expression.output(qualification));
    }

    @Override
    public Precedence precedence() {
        return Precedence.TERNARY;
    }

    @Override
    public EvaluationResult evaluate(EvaluationContext evaluationContext, ForwardEvaluationInfo forwardEvaluationInfo) {
        throw new UnsupportedOperationException();
    }

    @Override
    public int order() {
        return ExpressionComparator.ORDER_INLINE_METHOD;
    }

    @Override
    public int internalCompareTo(Expression v) {
        InlinedMethod mv = (InlinedMethod) v;
        return methodInfo.distinguishingName().compareTo(mv.methodInfo.distinguishingName());
    }

    /*
    These values only matter when the InlinedMethod cannot get expanded. In this case, it is simply
    a method call, and the result of the method should be used.
     */
    @Override
    public int getProperty(EvaluationContext evaluationContext, VariableProperty variableProperty, boolean duringEvaluation) {
        return evaluationContext.getAnalyserContext().getMethodAnalysis(methodInfo).getProperty(variableProperty);
    }

    @Override
    public EvaluationResult reEvaluate(EvaluationContext evaluationContext, Map<Expression, Expression> translation) {
        Set<Variable> targetVariables = translation.values().stream()
                .flatMap(e -> e.variables().stream()).collect(Collectors.toUnmodifiableSet());
        EvaluationContext closure = new EvaluationContextImpl(evaluationContext, targetVariables);
        EvaluationResult result = expression.reEvaluate(closure, translation);
        if (expression instanceof InlinedMethod im) {
            Set<Variable> newVariables = new HashSet<>(result.getExpression().variables());
            boolean haveVariableFields = newVariables.stream()
                    .anyMatch(v -> v instanceof FieldReference fr && evaluationContext.getAnalyserContext()
                            .getFieldAnalysis(fr.fieldInfo).getProperty(VariableProperty.FINAL) == Level.FALSE);
            InlinedMethod newIm = new InlinedMethod(identifier, im.methodInfo(), result.getExpression(),
                    newVariables, haveVariableFields);
            return new EvaluationResult.Builder().compose(result).setExpression(newIm).build();
        }
        return result;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        InlinedMethod that = (InlinedMethod) o;
        return methodInfo.equals(that.methodInfo) && expression.equals(that.expression);
    }

    @Override
    public int hashCode() {
        return Objects.hash(methodInfo, expression);
    }

    @Override
    public String toString() {
        return minimalOutput();
    }

    @Override
    public void visit(Predicate<Expression> predicate) {
        if (predicate.test(this)) {
            expression.visit(predicate);
        }
    }

    @Override
    public List<Variable> variables() {
        return List.copyOf(variablesOfExpression);
    }

    public boolean canBeApplied(EvaluationContext evaluationContext) {
        return !containsVariableFields ||
                evaluationContext.getCurrentType().primaryType().equals(methodInfo.typeInfo.primaryType());
    }

    public MethodInfo methodInfo() {
        return methodInfo;
    }

    @Override
    public NewObject getInstance(EvaluationResult evaluationContext) {
        // TODO verify this
        return expression.getInstance(evaluationContext);
    }

    public Expression expression() {
        return expression;
    }

    public boolean containsVariableFields() {
        return containsVariableFields;
    }

    /*
    We're assuming that the parameters of the method occur in the value, so it's simpler to iterate

     */
    public Map<Expression, Expression> translationMap(EvaluationContext evaluationContext,
                                                      List<Expression> parameters,
                                                      Expression scope,
                                                      TypeInfo typeOfTranslation,
                                                      Identifier identifierOfMethodCall) {
        Map<Expression, Expression> builder = new HashMap<>();
        InspectionProvider inspectionProvider = evaluationContext.getAnalyserContext();

        for (Variable variable : variablesOfExpression) {
            Expression replacement = null;
            boolean replace = true;
            FieldReference fieldReference = null;
            if (variable instanceof ParameterInfo parameterInfo) {
                if (parameterInfo.getMethod() == methodInfo) {
                    if (parameterInfo.parameterInspection.get().isVarArgs()) {
                        replacement = new ArrayInitializer(inspectionProvider, parameters
                                .subList(parameterInfo.index, parameters.size()),
                                parameterInfo.parameterizedType);
                    } else {
                        replacement = parameters.get(parameterInfo.index);
                    }
                } else {
                    replace = false;
                }
            } else if (variable instanceof This) {
                VariableExpression ve;
                if ((ve = scope.asInstanceOf(VariableExpression.class)) != null) {
                    builder.put(new VariableExpression(variable), ve);
                }
            } else if (variable instanceof FieldReference fr) {
                fieldReference = fr;
            } else if (variable instanceof LocalVariableReference lvr
                    && lvr.variableNature() instanceof VariableNature.CopyOfVariableField cvf) {
                fieldReference = cvf.localCopyOf();
            }

            if (fieldReference != null) {
                boolean staticField = fieldReference.fieldInfo.isStatic(inspectionProvider);
                boolean scopeIsThis = fieldReference.scopeIsThis();
                if (!staticField && !scopeIsThis) {
                    replace = false;
                    // the variables of the scope are dealt with separately
                } else if (visibleIn(inspectionProvider, fieldReference.fieldInfo, typeOfTranslation)) {
                    // maybe the final field is linked to a parameter, and we have a value for that parameter?

                    FieldAnalysis fieldAnalysis = evaluationContext.getAnalyserContext().getFieldAnalysis(fieldReference.fieldInfo);
                    int effectivelyFinal = fieldAnalysis.getProperty(VariableProperty.FINAL);
                    if (effectivelyFinal == Level.TRUE) {
                        if (staticField) {
                            replace = false;
                        } else {
                            NewObject newObject = scope.asInstanceOf(NewObject.class);
                            VariableExpression ve;
                            if (newObject == null && (ve = scope.asInstanceOf(VariableExpression.class)) != null) {
                                Expression value = evaluationContext.currentInstance(ve.variable(),
                                        evaluationContext.getInitialStatementTime());
                                newObject = value.asInstanceOf(NewObject.class);
                            }
                            if (newObject != null && newObject.constructor() != null) {
                                // only now we can start to take a look at the parameters
                                int index = indexOfParameterLinkedToFinalField(evaluationContext, newObject.constructor(),
                                        fieldReference.fieldInfo);
                                if (index >= 0) {
                                    replacement = newObject.getParameterExpressions().get(index);
                                }
                            }
                        }
                    }

                    VariableExpression ve;
                    if (replacement == null && (ve = scope.asInstanceOf(VariableExpression.class)) != null) {
                        FieldReference scopeField = new FieldReference(inspectionProvider, fieldReference.fieldInfo,
                                staticField ? null : ve);
                        boolean delayed = evaluationContext.variableIsDelayed(scopeField);
                        if (delayed) {
                            replacement = DelayedVariableExpression.forField(scopeField);
                        } else {
                            replacement = new VariableExpression(scopeField);
                        }
                    }
                }
            }
            if (replace) {
                if (replacement == null) {
                    replacement = NewObject.forGetInstance(Identifier.joined(List.of(identifierOfMethodCall,
                                    Identifier.variable(variable))),
                            inspectionProvider.getPrimitives(), variable.parameterizedType());
                }

                builder.put(new VariableExpression(variable), replacement);
            }
        }

        return Map.copyOf(builder);
    }

    private int indexOfParameterLinkedToFinalField(EvaluationContext evaluationContext,
                                                   MethodInfo constructor,
                                                   FieldInfo fieldInfo) {
        int i = 0;
        List<ParameterAnalysis> parameterAnalyses = evaluationContext
                .getParameterAnalyses(constructor).collect(Collectors.toList());
        for (ParameterAnalysis parameterAnalysis : parameterAnalyses) {
            if (!parameterAnalysis.assignedToFieldIsFrozen()) {
                return -2; // delays
            }
            Map<FieldInfo, ParameterAnalysis.AssignedOrLinked> assigned = parameterAnalysis.getAssignedToField();
            ParameterAnalysis.AssignedOrLinked assignedOrLinked = assigned.get(fieldInfo);
            if (assignedOrLinked == ParameterAnalysis.AssignedOrLinked.ASSIGNED) {
                return i;
            }
            i++;
        }
        return -1; // nothing
    }

    private boolean visibleIn(InspectionProvider inspectionProvider, FieldInfo fieldInfo, TypeInfo here) {
        FieldInspection fieldInspection = inspectionProvider.getFieldInspection(fieldInfo);
        return switch (fieldInspection.getAccess()) {
            case PRIVATE -> here.primaryType().equals(fieldInfo.primaryType());
            case PACKAGE -> here.primaryType().packageNameOrEnclosingType.getLeft()
                    .equals(fieldInfo.owner.primaryType().packageNameOrEnclosingType.getLeft());
            case PROTECTED -> here.primaryType().equals(fieldInfo.primaryType()) ||
                    here.hasAsParentClass(inspectionProvider, fieldInfo.owner);
            default -> true;
        };
    }

    private class EvaluationContextImpl extends AbstractEvaluationContextImpl {
        private final EvaluationContext evaluationContext;
        private final Set<Variable> acceptedVariables;

        protected EvaluationContextImpl(EvaluationContext evaluationContext, Set<Variable> targetVariables) {
            super(evaluationContext.getIteration(),
                    ConditionManager.initialConditionManager(evaluationContext.getPrimitives()), null);
            this.evaluationContext = evaluationContext;
            this.acceptedVariables = SetUtil.immutableUnion(variablesOfExpression, targetVariables);
        }

        protected EvaluationContextImpl(EvaluationContextImpl parent, ConditionManager conditionManager) {
            super(parent.iteration, conditionManager, null);
            this.evaluationContext = parent.evaluationContext;
            this.acceptedVariables = parent.acceptedVariables;
        }

        private void ensureVariableIsKnown(Variable variable) {
            assert variable instanceof This || acceptedVariables.contains(variable) :
                    "there should be no other variables in this expression: " +
                            variable + " is not in accepted variables\n" + acceptedVariables +
                            " of expression\n" + expression + "\nvariablesOfExpression is " + variablesOfExpression;
        }

        @Override
        public TypeInfo getCurrentType() {
            return evaluationContext.getCurrentType();
        }

        @Override
        public MethodAnalyser getCurrentMethod() {
            return evaluationContext.getCurrentMethod();
        }

        @Override
        public StatementAnalyser getCurrentStatement() {
            return null;
        }

        @Override
        public Location getLocation() {
            return evaluationContext.getLocation();
        }

        @Override
        public Location getLocation(Identifier identifier) {
            return evaluationContext.getLocation(identifier);
        }

        @Override
        public Primitives getPrimitives() {
            return evaluationContext.getPrimitives();
        }

        @Override
        public EvaluationContext child(Expression condition) {
            return child(condition, false);
        }

        @Override
        public EvaluationContext dropConditionManager() {
            ConditionManager cm = ConditionManager.initialConditionManager(getPrimitives());
            return new EvaluationContextImpl(this, cm);
        }

        @Override
        public EvaluationContext child(Expression condition, boolean disableEvaluationOfMethodCallsUsingCompanionMethods) {
            Set<Variable> conditionIsDelayed = isDelayedSet(condition);
            return new EvaluationContextImpl(this,
                    conditionManager.newAtStartOfNewBlockDoNotChangePrecondition(getPrimitives(), condition, conditionIsDelayed));
        }

        @Override
        public EvaluationContext childState(Expression state) {
            Set<Variable> stateIsDelayed = isDelayedSet(state);
            return new EvaluationContextImpl(this, conditionManager.addState(state, stateIsDelayed));
        }

        @Override
        public Expression currentValue(Variable variable, int statementTime, ForwardEvaluationInfo forwardEvaluationInfo) {
            ensureVariableIsKnown(variable);
            return new VariableExpression(variable);
        }

        @Override
        public AnalyserContext getAnalyserContext() {
            return evaluationContext.getAnalyserContext();
        }

        @Override
        public Stream<ParameterAnalysis> getParameterAnalyses(MethodInfo methodInfo) {
            return evaluationContext.getParameterAnalyses(methodInfo);
        }

        @Override
        public int getProperty(Expression value, VariableProperty variableProperty, boolean duringEvaluation, boolean ignoreStateInConditionManager) {
            return evaluationContext.getProperty(value, variableProperty, duringEvaluation, ignoreStateInConditionManager);
        }

        @Override
        public int getProperty(Variable variable, VariableProperty variableProperty) {
            ensureVariableIsKnown(variable);
            return variableProperty.falseValue; // FIXME
        }

        @Override
        public int getPropertyFromPreviousOrInitial(Variable variable, VariableProperty variableProperty, int statementTime) {
            ensureVariableIsKnown(variable);
            return variableProperty.falseValue; // FIXME
        }

        @Override
        public boolean notNullAccordingToConditionManager(Variable variable) {
            return notNullAccordingToConditionManager(variable, fr -> {
                throw new UnsupportedOperationException("there should be no local copies of field references!");
            });
        }

        @Override
        public LinkedVariables linkedVariables(Variable variable) {
            ensureVariableIsKnown(variable);
            return LinkedVariables.EMPTY;
        }

        @Override
        public Map<VariableProperty, Integer> getValueProperties(Expression value) {
            return evaluationContext.getValueProperties(value);
        }

        @Override
        public Map<VariableProperty, Integer> getValueProperties(Expression value, boolean ignoreConditionInConditionManager) {
            return evaluationContext.getValueProperties(value, ignoreConditionInConditionManager);
        }

        // FIXME nullable
        @Override
        public NewObject currentInstance(Variable variable, int statementTime) {
            ensureVariableIsKnown(variable);
            return NewObject.forInlinedMethod(identifier, evaluationContext.getPrimitives(),
                    variable.parameterizedType(), MultiLevel.NULLABLE);
        }

        @Override
        public boolean disableEvaluationOfMethodCallsUsingCompanionMethods() {
            return evaluationContext.disableEvaluationOfMethodCallsUsingCompanionMethods();
        }

        @Override
        public int getInitialStatementTime() {
            return evaluationContext.getInitialStatementTime();
        }

        @Override
        public int getFinalStatementTime() {
            return evaluationContext.getFinalStatementTime();
        }

        @Override
        public boolean allowedToIncrementStatementTime() {
            return evaluationContext.allowedToIncrementStatementTime();
        }

        @Override
        public Expression replaceLocalVariables(Expression expression) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Expression acceptAndTranslatePrecondition(Expression rest) {
            return evaluationContext.acceptAndTranslatePrecondition(rest);
        }

        @Override
        public boolean isPresent(Variable variable) {
            return variablesOfExpression.contains(variable);
        }

        @Override
        public List<PrimaryTypeAnalyser> getLocalPrimaryTypeAnalysers() {
            return evaluationContext.getLocalPrimaryTypeAnalysers();
        }

        @Override
        public Stream<Map.Entry<String, VariableInfoContainer>> localVariableStream() {
            return evaluationContext.localVariableStream();
        }

        @Override
        public MethodAnalysis findMethodAnalysisOfLambda(MethodInfo methodInfo) {
            return evaluationContext.findMethodAnalysisOfLambda(methodInfo);
        }

        @Override
        public LinkedVariables getStaticallyAssignedVariables(Variable variable, int statementTime) {
            return evaluationContext.getStaticallyAssignedVariables(variable, statementTime);
        }

        @Override
        public boolean variableIsDelayed(Variable variable) {
            return false; // nothing can be delayed here
        }

        @Override
        public boolean isDelayed(Expression expression) {
            return false; // nothing can be delayed here
        }

        @Override
        public Set<Variable> isDelayedSet(Expression expression) {
            return null; // nothing can be delayed here
        }

        @Override
        public This currentThis() {
            return evaluationContext.currentThis();
        }

        @Override
        public Boolean isCurrentlyLinkedToField(Expression objectValue) {
            return evaluationContext.isCurrentlyLinkedToField(objectValue);
        }

        @Override
        public boolean cannotBeModified(Expression value) {
            return evaluationContext.cannotBeModified(value);
        }

        @Override
        public MethodInfo concreteMethod(Variable variable, MethodInfo methodInfo) {
            return evaluationContext.concreteMethod(variable, methodInfo);
        }

        @Override
        public String statementIndex() {
            return evaluationContext.statementIndex();
        }

        @Override
        public boolean firstAssignmentOfFieldInConstructor(Variable variable) {
            return evaluationContext.firstAssignmentOfFieldInConstructor(variable);
        }

        @Override
        public boolean hasBeenAssigned(Variable variable) {
            return evaluationContext.hasBeenAssigned(variable);
        }

        @Override
        public boolean foundDelay(String where, String delayFqn) {
            return evaluationContext.foundDelay(where, delayFqn);
        }

        @Override
        public boolean translatedDelay(String where, String delayFromFqn, String newDelayFqn) {
            return evaluationContext.translatedDelay(where, delayFromFqn, newDelayFqn);
        }

        @Override
        public boolean createDelay(String where, String delayFqn) {
            return evaluationContext.createDelay(where, delayFqn);
        }

        @Override
        public Stream<DelayDebugNode> streamNodes() {
            return Stream.of();
        }
    }
}
