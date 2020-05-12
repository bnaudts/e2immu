package org.e2immu.analyser.model.statement;

import com.google.common.collect.ImmutableList;
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.model.expression.EmptyExpression;
import org.e2immu.analyser.parser.SideEffectContext;
import org.e2immu.analyser.util.Pair;
import org.e2immu.analyser.util.SetUtil;
import org.e2immu.analyser.util.StringUtil;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class TryStatement implements Statement {
    public final List<Expression> resources;
    public final Block tryBlock;
    public final List<Pair<CatchParameter, Block>> catchClauses;
    public final Block finallyBlock;

    public TryStatement(List<Expression> resources,
                        Block tryBlock,
                        List<Pair<CatchParameter, Block>> catchClauses,
                        Block finallyBlock) {
        this.resources = ImmutableList.copyOf(resources);
        this.tryBlock = tryBlock;
        this.catchClauses = ImmutableList.copyOf(catchClauses);
        this.finallyBlock = finallyBlock;
    }

    public static class CatchParameter implements Expression {
        public final LocalVariable localVariable;
        public final List<ParameterizedType> unionOfTypes;

        public CatchParameter(LocalVariable localVariable, List<ParameterizedType> unionOfTypes) {
            this.localVariable = localVariable;
            this.unionOfTypes = ImmutableList.copyOf(unionOfTypes);
        }

        @Override
        public Set<String> imports() {
            return unionOfTypes.stream().map(type -> type.typeInfo.fullyQualifiedName).collect(Collectors.toSet());
        }

        @Override
        public ParameterizedType returnType() {
            return null;
        }

        @Override
        public String expressionString(int indent) {
            return unionOfTypes.stream().map(type -> type.typeInfo.simpleName).collect(Collectors.joining(" | "))
                    + " " + localVariable.name;
        }

        @Override
        public int precedence() {
            return 0;
        }

        @Override
        public Value evaluate(EvaluationContext evaluationContext, EvaluationVisitor visitor) {
            throw new UnsupportedOperationException();
        }
    }

    @Override
    public String statementString(int indent) {
        StringBuilder sb = new StringBuilder();
        StringUtil.indent(sb, indent);
        sb.append("try");
        if (!resources.isEmpty()) {
            sb.append("(");
            sb.append(resources.stream().map(r -> r.expressionString(0)).collect(Collectors.joining("; ")));
            sb.append(")");
        }
        sb.append(tryBlock.statementString(indent));
        for (Pair<CatchParameter, Block> pair : catchClauses) {
            sb.append(" catch(");
            sb.append(pair.k.expressionString(0));
            sb.append(")");
            sb.append(pair.v.statementString(indent));
        }
        if (finallyBlock != Block.EMPTY_BLOCK) {
            sb.append(" finally");
            sb.append(finallyBlock.statementString(indent));
        }
        sb.append("\n");
        return sb.toString();
    }

    @Override
    public Set<String> imports() {
        Set<String> importsOfResources = resources.stream().flatMap(r -> r.imports().stream()).collect(Collectors.toSet());
        Set<String> importsOfCatchParameters = catchClauses.stream().flatMap(c -> c.k.imports().stream()).collect(Collectors.toSet());
        Set<String> importsOfCatchBlocks = catchClauses.stream().flatMap(c -> c.v.imports().stream()).collect(Collectors.toSet());
        return SetUtil.immutableUnion(tryBlock.imports(), finallyBlock.imports(), importsOfResources, importsOfCatchBlocks, importsOfCatchParameters);
    }

    @Override
    public CodeOrganization codeOrganization() {
        CodeOrganization.Builder builder = new CodeOrganization.Builder().addInitialisers(resources)
                .setStatementsExecutedAtLeastOnce(v -> true)
                .setStatements(tryBlock)
                .setNoBlockMayBeExecuted(false); //there's always the main block
        for (Pair<CatchParameter, Block> pair : catchClauses) {
            builder.addSubStatement(new CodeOrganization.Builder().setLocalVariableCreation(pair.k.localVariable)
                    .setStatementsExecutedAtLeastOnce(v -> false)
                    .setStatements(pair.v).build());
        }
        if (finallyBlock != null) {
            builder.addSubStatement(new CodeOrganization.Builder()
                    .setExpression(EmptyExpression.FINALLY_EXPRESSION)
                    .setStatements(finallyBlock)
                    .setStatementsExecutedAtLeastOnce(v -> true)
                    .build());
        }
        return builder.build();
    }

    @Override
    public SideEffect sideEffect(SideEffectContext sideEffectContext) {
        return tryBlock.sideEffect(sideEffectContext);
    }

}
