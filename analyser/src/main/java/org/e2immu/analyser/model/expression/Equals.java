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

import org.e2immu.analyser.analyser.EvaluationContext;
import org.e2immu.analyser.analyser.EvaluationResult;
import org.e2immu.analyser.model.Expression;
import org.e2immu.analyser.model.Identifier;
import org.e2immu.analyser.model.TranslationMap;
import org.e2immu.analyser.model.expression.util.ExpressionComparator;
import org.e2immu.analyser.parser.Primitives;

import java.util.*;
import java.util.stream.Stream;

public class Equals extends BinaryOperator {

    // public for testing
    public Equals(Identifier identifier, Primitives primitives, Expression lhs, Expression rhs) {
        super(identifier, primitives, lhs, lhs.isNumeric() ? primitives.equalsOperatorInt() : primitives.equalsOperatorObject(),
                rhs, Precedence.EQUALITY);
    }

    @Override
    public Expression translate(TranslationMap translationMap) {
        Expression e = translationMap.translateExpression(this);
        if(e != this) return e;
        Expression tl = lhs.translate(translationMap);
        Expression tr = rhs.translate(translationMap);
        if(tl == lhs && tr == rhs) return this;
        return new Equals(identifier, primitives, tl, tr);
    }

    @Override
    public EvaluationResult reEvaluate(EvaluationContext evaluationContext, Map<Expression, Expression> translation) {
        EvaluationResult reLhs = lhs.reEvaluate(evaluationContext, translation);
        EvaluationResult reRhs = rhs.reEvaluate(evaluationContext, translation);
        EvaluationResult.Builder builder = new EvaluationResult.Builder(evaluationContext).compose(reLhs, reRhs);
        return builder.setExpression(Equals.equals(evaluationContext, reLhs.value(), reRhs.value())).build();
    }

    public static Expression equals(EvaluationContext evaluationContext, Expression l, Expression r) {
        return equals(Identifier.generate(), evaluationContext, l, r, true);
    }

    public static Expression equals(Identifier identifier, EvaluationContext evaluationContext, Expression l, Expression r) {
        return equals(identifier, evaluationContext, l, r, true);
    }

    public static Expression equals(Identifier identifier,
                                    EvaluationContext evaluationContext, Expression l, Expression r, boolean checkForNull) {
        Primitives primitives = evaluationContext.getPrimitives();
        if (l.equals(r)) return new BooleanConstant(primitives, true);

        //if (l.isUnknown() || r.isUnknown()) throw new UnsupportedOperationException();

        if (checkForNull) {
            if (l instanceof NullConstant && evaluationContext.isNotNull0(r, false) ||
                    r instanceof NullConstant && evaluationContext.isNotNull0(l, false))
                return new BooleanConstant(primitives, false);
        }

        if (l instanceof ConstantExpression<?> lc
                && r instanceof ConstantExpression<?> rc
                && !(lc instanceof NullConstant)
                && (!(rc instanceof NullConstant))) {
            return ConstantExpression.equalsExpression(primitives, lc, rc);
        }

        if (l instanceof InlineConditional inlineConditional) {
            Expression result = tryToRewriteConstantEqualsInline(evaluationContext, r, inlineConditional);
            if (result != null) return result;
        }
        if (r instanceof InlineConditional inlineConditional) {
            Expression result = tryToRewriteConstantEqualsInline(evaluationContext, l, inlineConditional);
            if (result != null) return result;
        }

        Expression[] terms = Stream.concat(Sum.expandTerms(evaluationContext, l, false),
                Sum.expandTerms(evaluationContext, r, true)).toArray(Expression[]::new);
        Arrays.sort(terms);
        Expression[] termsOfProducts = Sum.makeProducts(evaluationContext, terms);

        if (termsOfProducts.length == 0) {
            return new BooleanConstant(primitives, true);
        }
        if (termsOfProducts.length == 1) {
            if (termsOfProducts[0] instanceof Numeric) {
                return new BooleanConstant(primitives, false);
            }
            IntConstant zero = new IntConstant(primitives, 0);
            if (termsOfProducts[0] instanceof Negation neg) {
                return new Equals(identifier, primitives, zero, neg.expression);
            }
            // 0 == 3*x --> 0 == x
            if (termsOfProducts[0] instanceof Product p && p.lhs instanceof Numeric) {
                return new Equals(identifier, primitives, zero, p.rhs);
            }
            return new Equals(identifier, primitives, zero, termsOfProducts[0]);
        }
        Expression newLeft;
        Expression newRight;

        // 4 == xx; -4 == -x, ...
        if (termsOfProducts[0] instanceof Numeric numeric) {
            // -4 + -x --> -4 == x
            double d = numeric.doubleValue();
            if (d < 0 && termsOfProducts[1] instanceof Negation) {
                newLeft = termsOfProducts[0];
                newRight = wrapSum(evaluationContext, termsOfProducts, true);
                // 4 + i == 0 --> -4 == i
            } else if (d > 0 && !(termsOfProducts[1] instanceof Negation)) {
                newLeft = IntConstant.intOrDouble(primitives, -d);
                newRight = wrapSum(evaluationContext, termsOfProducts, false);
                // -4 + x == 0 --> 4 == x
            } else if (d < 0) {
                newLeft = IntConstant.intOrDouble(primitives, -d);
                newRight = wrapSum(evaluationContext, termsOfProducts, false);
            } else {
                newLeft = termsOfProducts[0];
                newRight = wrapSum(evaluationContext, termsOfProducts, true);
            }
        } else if (termsOfProducts[0] instanceof Negation neg) {
            newLeft = neg.expression;
            newRight = wrapSum(evaluationContext, termsOfProducts, false);
        } else {
            newLeft = termsOfProducts[0];
            newRight = wrapSum(evaluationContext, termsOfProducts, true);
        }

        // recurse
        return new Equals(identifier, primitives, newLeft, newRight);
    }

    private static Expression wrapSum(EvaluationContext evaluationContext,
                                      Expression[] termsOfProducts,
                                      boolean negate) {
        if (termsOfProducts.length == 2) {
            return negate ? Negation.negate(evaluationContext, termsOfProducts[1]) : termsOfProducts[1];
        }
        return wrapSum(evaluationContext, termsOfProducts, 1, termsOfProducts.length, negate);
    }

    private static Expression wrapSum(EvaluationContext evaluationContext,
                                      Expression[] termsOfProducts,
                                      int start, int end,
                                      boolean negate) {
        if (end - start == 2) {
            Expression s1 = termsOfProducts[start];
            Expression t1 = negate ? Negation.negate(evaluationContext, s1) : s1;
            Expression s2 = termsOfProducts[start + 1];
            Expression t2 = negate ? Negation.negate(evaluationContext, s2) : s2;
            return Sum.sum(evaluationContext, t1, t2);
        }
        Expression t1 = wrapSum(evaluationContext, termsOfProducts, start, end - 1, negate);
        Expression s2 = termsOfProducts[end - 1];
        Expression t2 = negate ? Negation.negate(evaluationContext, s2) : s2;
        return Sum.sum(evaluationContext, t1, t2);
    }

    // (a ? null: b) == null with guaranteed b != null --> !a
    // (a ? x: b) == null with guaranteed b != null --> !a&&x==null

    // GENERAL:
    // (a ? x: y) == c  ; if y != c, guaranteed, then the result is a&&x==c
    // (a ? x: y) == c  ; if x != c, guaranteed, then the result is !a&&y==c

    // see test ConditionalChecks_7; TestEqualsConstantInline
    public static Expression tryToRewriteConstantEqualsInline(EvaluationContext evaluationContext,
                                                              Expression c,
                                                              InlineConditional inlineConditional) {
        if (c instanceof InlineConditional inline2) {
            // silly check a1?b1:c1 == a1?b2:c2 === b1 == b2 && c1 == c2
            if (inline2.condition.equals(inlineConditional.condition)) {
                return And.and(evaluationContext,
                        Equals.equals(evaluationContext, inlineConditional.ifTrue, inline2.ifTrue),
                        Equals.equals(evaluationContext, inlineConditional.ifFalse, inline2.ifFalse));
            }
            return null;
        }

        boolean ifTrueGuaranteedNotEqual;
        boolean ifFalseGuaranteedNotEqual;

        Expression recursively1;
        if (inlineConditional.ifTrue instanceof InlineConditional inlineTrue) {
            recursively1 = tryToRewriteConstantEqualsInline(evaluationContext, c, inlineTrue);
            ifTrueGuaranteedNotEqual = recursively1 != null && recursively1.isBoolValueFalse();
        } else {
            recursively1 = null;
            if (c instanceof NullConstant) {
                ifTrueGuaranteedNotEqual = evaluationContext.isNotNull0(inlineConditional.ifTrue, false);
            } else {
                ifTrueGuaranteedNotEqual = Equals.equals(evaluationContext, inlineConditional.ifTrue, c).isBoolValueFalse();
            }
        }

        if (ifTrueGuaranteedNotEqual) {
            Expression notCondition = Negation.negate(evaluationContext, inlineConditional.condition);
            return And.and(evaluationContext,
                    notCondition, Equals.equals(evaluationContext, inlineConditional.ifFalse, c));
        }

        Expression recursively2;
        if (inlineConditional.ifFalse instanceof InlineConditional inlineFalse) {
            recursively2 = tryToRewriteConstantEqualsInline(evaluationContext, c, inlineFalse);
            ifFalseGuaranteedNotEqual = recursively2 != null && recursively2.isBoolValueFalse();
        } else {
            recursively2 = null;
            if (c instanceof NullConstant) {
                ifFalseGuaranteedNotEqual = evaluationContext.isNotNull0(inlineConditional.ifFalse, false);
            } else {
                ifFalseGuaranteedNotEqual = Equals.equals(evaluationContext, inlineConditional.ifFalse, c).isBoolValueFalse();
            }
        }

        if (ifFalseGuaranteedNotEqual) {
            return And.and(evaluationContext,
                    inlineConditional.condition, Equals.equals(evaluationContext, inlineConditional.ifTrue, c));
        }

        // we try to do something with recursive results
        if (recursively1 != null && recursively2 != null) {
            Expression notCondition = Negation.negate(evaluationContext, inlineConditional.condition);
            return Or.or(evaluationContext, And.and(evaluationContext, inlineConditional.condition, recursively1),
                    And.and(evaluationContext, notCondition, recursively2));
        }
        return null;
    }

    // (a ? null: b) != null --> !a

    // GENERAL:
    // (a ? x: y) != c  ; if y == c, guaranteed, then the result is a&&x!=c
    // (a ? x: y) != c  ; if x == c, guaranteed, then the result is !a&&y!=c

    // see test ConditionalChecks_7; TestEqualsConstantInline
    public static Expression tryToRewriteConstantEqualsInlineNegative(EvaluationContext evaluationContext,
                                                                      Expression c,
                                                                      InlineConditional inlineConditional) {
        if (c instanceof InlineConditional inline2) {
            // silly check a1?b1:c1 != a1?b2:c2 === b1 != b2 || c1 != c2
            if (inline2.condition.equals(inlineConditional.condition)) {
                return Or.or(evaluationContext,
                        Negation.negate(evaluationContext, Equals.equals(evaluationContext, inlineConditional.ifTrue, inline2.ifTrue)),
                        Negation.negate(evaluationContext, Equals.equals(evaluationContext, inlineConditional.ifFalse, inline2.ifFalse)));
            }
            return null;
        }

        boolean ifTrueGuaranteedEqual;
        boolean ifFalseGuaranteedEqual;

        if (c instanceof NullConstant) {
            ifTrueGuaranteedEqual = inlineConditional.ifTrue instanceof NullConstant;
            ifFalseGuaranteedEqual = inlineConditional.ifFalse instanceof NullConstant;
        } else {
            ifTrueGuaranteedEqual = Equals.equals(evaluationContext, inlineConditional.ifTrue, c).isBoolValueTrue();
            ifFalseGuaranteedEqual = Equals.equals(evaluationContext, inlineConditional.ifFalse, c).isBoolValueTrue();
        }
        if (ifTrueGuaranteedEqual) {
            Expression notCondition = Negation.negate(evaluationContext, inlineConditional.condition);
            return And.and(evaluationContext,
                    notCondition, Negation.negate(evaluationContext, Equals.equals(evaluationContext, inlineConditional.ifFalse, c)));
        }
        if (ifFalseGuaranteedEqual) {
            return And.and(evaluationContext, inlineConditional.condition,
                    Negation.negate(evaluationContext, Equals.equals(evaluationContext, inlineConditional.ifTrue, c)));
        }
        return null;
    }

    @Override
    public int order() {
        return ExpressionComparator.ORDER_EQUALS;
    }
}
