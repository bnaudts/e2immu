package org.e2immu.analyser.model.statement;

import com.google.common.collect.ImmutableList;
import org.e2immu.analyser.analyser.NumberedStatement;
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.model.expression.EmptyExpression;
import org.e2immu.analyser.util.ListUtil;
import org.e2immu.analyser.util.Pair;
import org.e2immu.analyser.util.StringUtil;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class TryStatement extends StatementWithStructure {
    public final List<Expression> resources;
    public final List<Pair<CatchParameter, Block>> catchClauses;
    public final Block finallyBlock;
    private final List<? extends Element> subElements;

    public TryStatement(List<Expression> resources,
                        Block tryBlock,
                        List<Pair<CatchParameter, Block>> catchClauses,
                        Block finallyBlock) {
        super(codeOrganization(resources, tryBlock, catchClauses, finallyBlock));
        this.resources = ImmutableList.copyOf(resources);
        this.catchClauses = ImmutableList.copyOf(catchClauses);
        this.finallyBlock = finallyBlock;
        subElements = ListUtil.immutableConcat(List.of(tryBlock), catchClauses.stream().map(Pair::getV).collect(Collectors.toList()),
                finallyBlock == Block.EMPTY_BLOCK ? List.of() : List.of(finallyBlock));
    }

    private static CodeOrganization codeOrganization(List<Expression> resources,
                                                     Block tryBlock,
                                                     List<Pair<CatchParameter, Block>> catchClauses,
                                                     Block finallyBlock) {
        CodeOrganization.Builder builder = new CodeOrganization.Builder().addInitialisers(resources)
                .setStatementsExecutedAtLeastOnce(v -> true)
                .setBlock(tryBlock)
                .setNoBlockMayBeExecuted(false); //there's always the main block
        for (Pair<CatchParameter, Block> pair : catchClauses) {
            builder.addSubStatement(new CodeOrganization.Builder().setLocalVariableCreation(pair.k.localVariable)
                    .setStatementsExecutedAtLeastOnce(v -> false)
                    .setBlock(pair.v).build());
        }
        if (finallyBlock != null) {
            builder.addSubStatement(new CodeOrganization.Builder()
                    .setExpression(EmptyExpression.FINALLY_EXPRESSION)
                    .setBlock(finallyBlock)
                    .setStatementsExecutedAtLeastOnce(v -> true)
                    .build());
        }
        return builder.build();
    }

    @Override
    public Statement translate(TranslationMap translationMap) {
        return new TryStatement(resources.stream().map(translationMap::translateExpression).collect(Collectors.toList()),
                translationMap.translateBlock(codeOrganization.block),
                catchClauses.stream().map(p -> new Pair<>(
                        TranslationMap.ensureExpressionType(p.k.translate(translationMap), CatchParameter.class),
                        translationMap.translateBlock(p.v))).collect(Collectors.toList()),
                translationMap.translateBlock(finallyBlock));
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
        public Set<TypeInfo> typesReferenced() {
            return unionOfTypes.stream().flatMap(pt -> pt.typesReferenced().stream()).collect(Collectors.toSet());
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
        public Value evaluate(EvaluationContext evaluationContext, EvaluationVisitor visitor, ForwardEvaluationInfo forwardEvaluationInfo) {
            throw new UnsupportedOperationException();
        }

    }

    @Override
    public String statementString(int indent, NumberedStatement ns) {
        StringBuilder sb = new StringBuilder();
        StringUtil.indent(sb, indent);
        sb.append("try");
        if (!resources.isEmpty()) {
            sb.append("(");
            sb.append(resources.stream().map(r -> r.expressionString(0)).collect(Collectors.joining("; ")));
            sb.append(")");
        }
        sb.append(codeOrganization.block.statementString(indent, NumberedStatement.startOfBlock(ns, 0)));
        int i = 1;
        for (Pair<CatchParameter, Block> pair : catchClauses) {
            sb.append(" catch(");
            sb.append(pair.k.expressionString(0));
            sb.append(")");
            sb.append(pair.v.statementString(indent, NumberedStatement.startOfBlock(ns, i)));
            i++;
        }
        if (finallyBlock != Block.EMPTY_BLOCK) {
            sb.append(" finally");
            sb.append(finallyBlock.statementString(indent, NumberedStatement.startOfBlock(ns, i)));
        }
        sb.append("\n");
        return sb.toString();
    }

    @Override
    public SideEffect sideEffect(EvaluationContext evaluationContext) {
        return codeOrganization.block.sideEffect(evaluationContext);
    }

    @Override
    public List<? extends Element> subElements() {
        return subElements;
    }
}
