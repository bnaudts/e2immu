package org.e2immu.analyser.model;

import com.google.common.collect.ImmutableList;
import org.e2immu.analyser.model.expression.EmptyExpression;
import org.e2immu.analyser.model.statement.Block;
import org.e2immu.annotation.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;
import java.util.stream.Stream;

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
public class CodeOrganization {

    public final List<Expression> initialisers; // try, for   (example: int i=0; )
    public final LocalVariable localVariableCreation; // forEach, catch (int i,  Exception e)
    public final Expression expression; // for, forEach, while, do, return, expression statement, switch primary  (typically, the condition); OR condition for switch entry
    public final ForwardEvaluationInfo forwardEvaluationInfo; // info on the expression to be evaluated
    public final List<Expression> updaters; // for, explicit constructor invocation

    public final List<Statement> statements;
    public final Block block;

    @NotNull
    public final Predicate<Value> statementsExecutedAtLeastOnce;

    public final List<CodeOrganization> subStatements; // catches, finally, switch entries

    // decides if it possible at all that no block (statements, subStatements.statements) will be executed
    // this is possible in if statements without else, switch statements without default, loops that are not while(true), etc. etc.
    public final boolean noBlockMayBeExecuted;

    private CodeOrganization(@NotNull List<Expression> initialisers,
                             LocalVariable localVariableCreation,
                             @NotNull Expression expression,
                             @NotNull ForwardEvaluationInfo forwardEvaluationInfo,
                             @NotNull List<Expression> updaters,
                             Block block,
                             List<Statement> statements,
                             @NotNull Predicate<Value> statementsExecutedAtLeastOnce,
                             List<CodeOrganization> subStatements,
                             boolean noBlockMayBeExecuted) {
        this.initialisers = Objects.requireNonNull(initialisers);
        this.localVariableCreation = localVariableCreation;
        this.expression = Objects.requireNonNull(expression);
        this.forwardEvaluationInfo = Objects.requireNonNull(forwardEvaluationInfo);
        this.updaters = Objects.requireNonNull(updaters);
        this.statements = statements;
        this.block = block;
        if (block != null && statements != null)
            throw new UnsupportedOperationException("Either block, or statements, but not both");
        if(block != null && block.codeOrganization.statements == null) throw new UnsupportedOperationException();
        this.subStatements = Objects.requireNonNull(subStatements);
        this.statementsExecutedAtLeastOnce = statementsExecutedAtLeastOnce;
        this.noBlockMayBeExecuted = noBlockMayBeExecuted;
    }

    public List<Statement> getStatements() {
        if (block != null) return block.codeOrganization.statements;
        return statements == null ? List.of() : statements;
    }

    public boolean haveStatements() {
        if (block != null) return !block.codeOrganization.statements.isEmpty();
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
        private Predicate<Value> statementsExecutedAtLeastOnce;
        private List<Statement> statements;  // switch statement, block itself
        private Block block;
        private final List<CodeOrganization> subStatements = new ArrayList<>(); // catches, finally, switch entries
        private boolean noBlockMayBeExecuted = true;

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

        public Builder addSubStatement(CodeOrganization subStatement) {
            this.subStatements.add(subStatement);
            return this;
        }

        public Builder setUpdaters(List<Expression> updaters) {
            this.updaters.addAll(updaters);
            return this;
        }

        public Builder setStatementsExecutedAtLeastOnce(Predicate<Value> predicate) {
            this.statementsExecutedAtLeastOnce = predicate;
            return this;
        }

        public Builder setNoBlockMayBeExecuted(boolean noBlockMayBeExecuted) {
            this.noBlockMayBeExecuted = noBlockMayBeExecuted;
            return this;
        }

        public Builder setBlock(Block block) {
            this.block = block;
            return this;
        }

        @NotNull
        public CodeOrganization build() {
            return new CodeOrganization(ImmutableList.copyOf(initialisers),
                    localVariableCreation,
                    expression == null ? EmptyExpression.EMPTY_EXPRESSION : expression,
                    forwardEvaluationInfo == null ? ForwardEvaluationInfo.DEFAULT : forwardEvaluationInfo,
                    ImmutableList.copyOf(updaters),
                    block,
                    statements == null ? null : ImmutableList.copyOf(statements),
                    statementsExecutedAtLeastOnce == null ? v -> false : statementsExecutedAtLeastOnce,
                    ImmutableList.copyOf(subStatements),
                    noBlockMayBeExecuted);
        }
    }
}
