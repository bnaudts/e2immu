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
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.model.expression.util.ExpressionComparator;
import org.e2immu.analyser.model.variable.FieldReference;
import org.e2immu.analyser.model.variable.Variable;
import org.e2immu.analyser.output.OutputBuilder;
import org.e2immu.analyser.output.Text;
import org.e2immu.analyser.parser.Primitives;
import org.e2immu.analyser.util.ListUtil;
import org.e2immu.annotation.E2Container;

import java.util.List;
import java.util.Map;
import java.util.Objects;

@E2Container
public class DelayedVariableExpression implements Expression, IsVariableExpression {
    public final String msg;
    public final String debug;
    public final Variable variable;
    public final CausesOfDelay causesOfDelay;

    public DelayedVariableExpression(String msg,
                                     String debug,
                                     Variable variable,
                                     CauseOfDelay.Cause cause,
                                     CausesOfDelay otherCausesOfDelay) {
        this.msg = msg;
        this.debug = debug;
        this.causesOfDelay = cause == null ? otherCausesOfDelay :
                otherCausesOfDelay.merge(new CausesOfDelay.SimpleSet(new CauseOfDelay.VariableCause(variable, cause)));
        this.variable = variable;
    }

    public static DelayedVariableExpression forParameter(ParameterInfo parameterInfo, CauseOfDelay.Cause cause) {
        return forParameter(parameterInfo, cause, CausesOfDelay.EMPTY);
    }

    public static DelayedVariableExpression forParameter(ParameterInfo parameterInfo,
                                                         CauseOfDelay.Cause cause,
                                                         CausesOfDelay otherCausesOfDelay) {
        return new DelayedVariableExpression("<p:" + parameterInfo.name + ">",
                "<parameter:" + parameterInfo.fullyQualifiedName() + ">", parameterInfo,
                cause, otherCausesOfDelay);
    }

    public static DelayedVariableExpression forField(FieldReference fieldReference, CauseOfDelay.Cause cause) {
        return forField(fieldReference, cause, CausesOfDelay.EMPTY);
    }

    public static DelayedVariableExpression forField(FieldReference fieldReference, CausesOfDelay causesOfDelay) {
        return forField(fieldReference, null, causesOfDelay);
    }

    public static DelayedVariableExpression forField(FieldReference fieldReference,
                                                     CauseOfDelay.Cause cause,
                                                     CausesOfDelay otherCausesOfDelay) {
        return new DelayedVariableExpression("<f:" + fieldReference.fieldInfo.name + ">",
                "<field:" + fieldReference.fullyQualifiedName() + ">", fieldReference, cause, otherCausesOfDelay);
    }

    public static Expression forVariable(Variable variable, CauseOfDelay cause) {
        return forVariable(variable, new CausesOfDelay(cause));
    }

    public static Expression forVariable(Variable variable, CausesOfDelay causesOfDelay) {
        if (variable instanceof FieldReference fieldReference) return forField(fieldReference, causesOfDelay);
        if (variable instanceof ParameterInfo parameterInfo) return forParameter(parameterInfo, causesOfDelay);
        return new DelayedVariableExpression("<v:" + variable.simpleName() + ">",
                "<variable:" + variable.fullyQualifiedName() + ">", variable, causesOfDelay);
    }

    /*
    variable fields have different values according to statement time, but then, at this point we cannot know yet
    whether the field will be variable or not.
    Basics7 shows a case where the local condition manager goes from true to false depending on this equality.
     */

    @Override
    public boolean equals(Object o) {
        if (variable instanceof FieldReference) {
            return this == o;
        }
        return o instanceof DelayedVariableExpression dve && dve.variable.equals(variable);
    }

    @Override
    public Variable variable() {
        return variable;
    }

    @Override
    public int hashCode() {
        return Objects.hash(msg, variable.parameterizedType());
    }

    @Override
    public boolean isNumeric() {
        return Primitives.isNumeric(variable.parameterizedType().typeInfo);
    }

    @Override
    public String toString() {
        return msg;
    }

    @Override
    public ParameterizedType returnType() {
        return variable.concreteReturnType();
    }

    @Override
    public OutputBuilder output(Qualification qualification) {
        return new OutputBuilder().add(new Text(msg, debug));
    }

    @Override
    public Precedence precedence() {
        return Precedence.TOP;
    }

    @Override
    public EvaluationResult evaluate(EvaluationContext evaluationContext, ForwardEvaluationInfo forwardEvaluationInfo) {
        // CONTEXT NOT NULL as soon as possible, also for delayed values...

        EvaluationResult.Builder builder = new EvaluationResult.Builder(evaluationContext);

        if (variable instanceof FieldReference fr && fr.scope != null) {
            // do not continue modification onto This: we want modifications on this only when there's a direct method call
            ForwardEvaluationInfo forward = fr.scopeIsThis() ? forwardEvaluationInfo.copyNotNull() :
                    forwardEvaluationInfo.copyModificationEnsureNotNull();
            EvaluationResult scopeResult = fr.scope.evaluate(evaluationContext, forward);
            builder.compose(scopeResult);
        }

        int cnn = forwardEvaluationInfo.getProperty(VariableProperty.CONTEXT_NOT_NULL);
        if (cnn > MultiLevel.NULLABLE) {
            builder.variableOccursInNotNullContext(variable, this, cnn);
        }
        return builder.setExpression(this).build();
    }

    @Override
    public int order() {
        return ExpressionComparator.ORDER_NO_VALUE;
    }

    @Override
    public DV getProperty(EvaluationContext evaluationContext, VariableProperty variableProperty, boolean duringEvaluation) {
        if (VariableProperty.NOT_NULL_EXPRESSION == variableProperty && Primitives.isPrimitiveExcludingVoid(variable.parameterizedType())) {
            return MultiLevel.EFFECTIVELY_NOT_NULL_DV;
        }
        return causesOfDelay;
    }

    @Override
    public Expression translate(TranslationMap translationMap) {
        Expression replace = translationMap.directExpression(this);
        return replace != null && replace != this ? replace : this;
    }

    @Override
    public LinkedVariables linkedVariables(EvaluationContext evaluationContext) {
        return new LinkedVariables(Map.of(variable, LinkedVariables.STATICALLY_ASSIGNED));
    }

    @Override
    public List<Variable> variables() {
        if (variable instanceof FieldReference fr && fr.scope != null) {
            return ListUtil.concatImmutable(List.of(variable), fr.scope.variables());
        }
        return List.of(variable);
    }

    @Override
    public Identifier getIdentifier() {
        return Identifier.CONSTANT;
    }

    @Override
    public CausesOfDelay causesOfDelay() {
        return causesOfDelay;
    }
}
