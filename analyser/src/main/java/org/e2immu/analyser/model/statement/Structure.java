/*
 * e2immu: code analyser for effective and eventual immutability
 * Copyright 2020, Bart Naudts, https://www.e2immu.org
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.e2immu.analyser.model.statement;

import com.google.common.collect.ImmutableList;
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.model.expression.EmptyExpression;
import org.e2immu.annotation.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.BiPredicate;

/**
 * <ul>
 *     <li>expression as statement: E - -: LVs, no special LVs, eval E, no blocks</li>
 *     <li>block: - B -: no LVs, no special LVs, no eval, 1 block</li>
 *     <li>forEach: E -B -, one special LV, no LVs, eval E, one block</li>
 *     <li>for: EEE - B -, no special LVs, expressions with LVs, action expressions, eval expression, one block</li>
 *     <li>if: E - B - [^E-B]: no special LVs, no LV, eval E, one block, one special block</li>
 *     <li>switch: E - [ EEE-B EEE-B ]:  no special LVs, no LV, eval E, lots of conditional blocks</li>
 *     <li>try: EEE - B - [E-B E-B True-B]</li>
 * </ul>
 */
public class Structure {

    public final List<Expression> initialisers; // try, for   (example: int i=0; )
    public final LocalVariable localVariableCreation; // forEach, catch (int i,  Exception e)
    public final Expression expression; // for, forEach, while, do, return, expression statement, switch primary  (typically, the condition); OR condition for switch entry
    public final ForwardEvaluationInfo forwardEvaluationInfo; // info on the expression to be evaluated
    public final List<Expression> updaters; // for, explicit constructor invocation

    public final List<Statement> statements;
    public final Block block;

    @NotNull
    public final BiPredicate<Value, EvaluationContext> statementsExecutedAtLeastOnce;

    public final List<Structure> subStatements; // catches, finally, switch entries

    public final boolean createVariablesInsideBlock;
    public final boolean expressionIsCondition;

    private Structure(@NotNull List<Expression> initialisers,
                      LocalVariable localVariableCreation,
                      @NotNull Expression expression,
                      @NotNull ForwardEvaluationInfo forwardEvaluationInfo,
                      @NotNull List<Expression> updaters,
                      Block block,
                      List<Statement> statements,
                      @NotNull BiPredicate<Value, EvaluationContext> statementsExecutedAtLeastOnce,
                      List<Structure> subStatements,
                      boolean createVariablesInsideBlock,
                      boolean expressionIsCondition) {
        this.initialisers = Objects.requireNonNull(initialisers);
        this.localVariableCreation = localVariableCreation;
        this.expression = Objects.requireNonNull(expression);
        this.forwardEvaluationInfo = Objects.requireNonNull(forwardEvaluationInfo);
        this.updaters = Objects.requireNonNull(updaters);
        this.statements = statements;
        this.block = block;
        if (block != null && statements != null)
            throw new UnsupportedOperationException("Either block, or statements, but not both");
        if (block != null && block.structure.statements == null) throw new UnsupportedOperationException();
        this.subStatements = Objects.requireNonNull(subStatements);
        this.statementsExecutedAtLeastOnce = statementsExecutedAtLeastOnce;
        this.createVariablesInsideBlock = createVariablesInsideBlock;
        this.expressionIsCondition = expressionIsCondition;
    }

    public List<Statement> getStatements() {
        if (block != null) return block.structure.statements;
        return statements == null ? List.of() : statements;
    }

    public boolean haveStatements() {
        if (block != null) return !block.structure.statements.isEmpty();
        return statements != null && !statements.isEmpty();
    }

    public boolean haveNonEmptyBlock() {
        return block != null && block != Block.EMPTY_BLOCK;
    }


    public static class Builder {
        private final List<Expression> initialisers = new ArrayList<>(); // try, for   (example: int i=0; )
        private LocalVariable localVariableCreation; // forEach, catch (int i,  Exception e)
        private Expression expression; // for, forEach, while, do, return, expression statement, switch primary  (typically, the condition); OR condition for switch entry
        private ForwardEvaluationInfo forwardEvaluationInfo;
        private final List<Expression> updaters = new ArrayList<>(); // for
        private BiPredicate<Value, EvaluationContext> statementsExecutedAtLeastOnce;
        private List<Statement> statements;  // switch statement, block itself
        private Block block;
        private final List<Structure> subStatements = new ArrayList<>(); // catches, finally, switch entries
        private boolean createVariablesInsideBlock;
        private boolean expressionIsCondition;

        public Builder setExpressionIsCondition(boolean expressionIsCondition) {
            this.expressionIsCondition = expressionIsCondition;
            return this;
        }

        public Builder setCreateVariablesInsideBlock(boolean createVariablesInsideBlock) {
            this.createVariablesInsideBlock = createVariablesInsideBlock;
            return this;
        }

        public Builder setExpression(Expression expression) {
            this.expression = expression;
            return this;
        }

        public Builder setForwardEvaluationInfo(ForwardEvaluationInfo forwardEvaluationInfo) {
            this.forwardEvaluationInfo = forwardEvaluationInfo;
            return this;
        }

        public Builder addInitialisers(List<Expression> initialisers) {
            this.initialisers.addAll(initialisers);
            return this;
        }

        public Builder setLocalVariableCreation(LocalVariable localVariableCreation) {
            this.localVariableCreation = localVariableCreation;
            return this;
        }

        public Builder setStatements(List<Statement> statements) {
            this.statements = statements;
            return this;
        }

        public Builder addSubStatement(Structure subStatement) {
            this.subStatements.add(subStatement);
            return this;
        }

        public Builder setUpdaters(List<Expression> updaters) {
            this.updaters.addAll(updaters);
            return this;
        }

        public Builder setStatementsExecutedAtLeastOnce(BiPredicate<Value, EvaluationContext> predicate) {
            this.statementsExecutedAtLeastOnce = predicate;
            return this;
        }

        public Builder setBlock(Block block) {
            this.block = block;
            return this;
        }

        @NotNull
        public Structure build() {
            return new Structure(ImmutableList.copyOf(initialisers),
                    localVariableCreation,
                    expression == null ? EmptyExpression.EMPTY_EXPRESSION : expression,
                    forwardEvaluationInfo == null ? ForwardEvaluationInfo.DEFAULT : forwardEvaluationInfo,
                    ImmutableList.copyOf(updaters),
                    block,
                    statements == null ? null : ImmutableList.copyOf(statements),
                    statementsExecutedAtLeastOnce == null ? (v, ec) -> false : statementsExecutedAtLeastOnce,
                    ImmutableList.copyOf(subStatements),
                    createVariablesInsideBlock,
                    expressionIsCondition);
        }
    }
}
