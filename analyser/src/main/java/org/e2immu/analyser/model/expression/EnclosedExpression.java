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
import org.e2immu.analyser.inspector.TypeContext;
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.model.expression.util.ExpressionComparator;
import org.e2immu.analyser.model.impl.BaseExpression;
import org.e2immu.analyser.output.OutputBuilder;
import org.e2immu.analyser.output.Symbol;
import org.e2immu.analyser.parser.InspectionProvider;
import org.e2immu.analyser.parser.Primitives;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;

public class EnclosedExpression extends BaseExpression implements Expression {

    private final Expression inner;

    public EnclosedExpression(Identifier identifier, Expression inner) {
        super(identifier, inner.getComplexity());
        this.inner = inner;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        EnclosedExpression that = (EnclosedExpression) o;
        return inner.equals(that.inner);
    }

    @Override
    public int hashCode() {
        return Objects.hash(inner);
    }

    @Override
    public Expression translate(InspectionProvider inspectionProvider, TranslationMap translationMap) {
        Expression translated = translationMap.translateExpression(this);
        if (translated != this) return translated;

        Expression translatedInner = inner.translate(inspectionProvider, translationMap);
        if (translatedInner == inner) return this;
        return new EnclosedExpression(identifier, translatedInner);
    }

    @Override
    public int order() {
        return inner.order();
    }

    @Override
    public int internalCompareTo(Expression v) throws ExpressionComparator.InternalError {
        return inner.compareTo(((EnclosedExpression) v).inner);
    }

    @Override
    public ParameterizedType returnType() {
        return inner.returnType();
    }

    @Override
    public String toString() {
        return minimalOutput();
    }

    @Override
    public OutputBuilder output(Qualification qualification) {
        return new OutputBuilder().add(Symbol.LEFT_PARENTHESIS).add(inner.output(qualification)).add(Symbol.RIGHT_PARENTHESIS);
    }

    @Override
    public Precedence precedence() {
        return Precedence.TOP;
    }

    @Override
    public List<? extends Element> subElements() {
        return List.of(inner);
    }

    @Override
    public EvaluationResult evaluate(EvaluationResult context, ForwardEvaluationInfo forwardEvaluationInfo) {
        return inner.evaluate(context, forwardEvaluationInfo);
    }

    @Override
    public CausesOfDelay causesOfDelay() {
        return inner.causesOfDelay();
    }

    @Override
    public Expression mergeDelays(CausesOfDelay causesOfDelay) {
        if (inner.isDelayed()) {
            return new EnclosedExpression(identifier, inner.mergeDelays(causesOfDelay));
        }
        return this;
    }

    public Expression inner() {
        return inner;
    }

    @Override
    public boolean isInstanceOf(Class<? extends Expression> clazz) {
        return inner.isInstanceOf(clazz);
    }

    @Override
    public boolean isNullConstant() {
        return inner.isNullConstant();
    }

    @Override
    public <T extends Element> T asInstanceOf(Class<T> clazz) {
        return inner.asInstanceOf(clazz);
    }

    @Override
    public Set<ParameterizedType> erasureTypes(TypeContext typeContext) {
        return inner.erasureTypes(typeContext);
    }

    @Override
    public void visit(Predicate<Element> predicate) {
        inner.visit(predicate);
    }

    @Override
    public void visit(Visitor visitor) {
        if (visitor.beforeExpression(this)) {
            inner.visit(visitor);
        }
        visitor.afterExpression(this);
    }

    @Override
    public DV getProperty(EvaluationResult context, Property property, boolean duringEvaluation) {
        return context.evaluationContext().getProperty(inner, property, duringEvaluation, false);
    }

    @Override
    public LinkedVariables linkedVariables(EvaluationResult context) {
        return inner.linkedVariables(context);
    }

    @Override
    public Double numericValue() {
        return inner.numericValue();
    }

    @Override
    public Expression extractConditions(Primitives primitives) {
        return inner.extractConditions(primitives);
    }

    @Override
    public Expression applyCondition(Expression newState) {
        return inner.applyCondition(newState);
    }
}
