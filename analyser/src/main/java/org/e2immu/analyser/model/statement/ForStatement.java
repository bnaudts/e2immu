package org.e2immu.analyser.model.statement;

import com.google.common.collect.ImmutableList;
import org.e2immu.analyser.analyser.NumberedStatement;
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.util.ListUtil;
import org.e2immu.analyser.util.SetUtil;
import org.e2immu.analyser.util.StringUtil;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class ForStatement extends LoopStatement {
    public final List<Expression> initialisers;
    public final List<Expression> updaters;

    /**
     * @param label     the label of the block
     * @param condition Cannot be null, but can be EmptyExpression
     * @param block     cannot be null, but can be EmptyBlock
     */
    public ForStatement(String label, List<Expression> initialisers, Expression condition, List<Expression> updaters, Block block) {
        super(label, condition, block);
        // TODO we can go really far here in analysing the initialiser, condition, and updaters.
        // We should. This will provide a better executedAtLeastOnce predicate.
        this.initialisers = ImmutableList.copyOf(initialisers);
        this.updaters = ImmutableList.copyOf(updaters);
    }

    @Override
    public Statement translate(TranslationMap translationMap) {
        return new ForStatement(label,
                initialisers.stream().map(translationMap::translateExpression).collect(Collectors.toList()),
                translationMap.translateExpression(expression),
                updaters.stream().map(translationMap::translateExpression).collect(Collectors.toList()),
                translationMap.translateBlock(block));
    }

    @Override
    public String statementString(int indent, NumberedStatement numberedStatement) {
        StringBuilder sb = new StringBuilder();
        StringUtil.indent(sb, indent);
        if (label != null) {
            sb.append(label).append(": ");
        }
        sb.append("for(");
        sb.append(initialisers.stream().map(i -> i.expressionString(0)).collect(Collectors.joining(", ")));
        sb.append("; ");
        sb.append(expression.expressionString(0));
        sb.append("; ");
        sb.append(updaters.stream().map(u -> u.expressionString(0)).collect(Collectors.joining(", ")));
        sb.append(")");
        sb.append(block.statementString(indent, NumberedStatement.startOfBlock(numberedStatement, 0)));
        sb.append("\n");
        return sb.toString();
    }

    @Override
    public CodeOrganization codeOrganization() {
        return new CodeOrganization.Builder()
                .setStatementsExecutedAtLeastOnce(v -> false)
                .addInitialisers(initialisers)
                .setExpression(expression)
                .setUpdaters(updaters)
                .setStatements(block).build();
    }

    @Override
    public List<? extends Element> subElements() {
        return ListUtil.immutableConcat(initialisers, List.of(expression), updaters, List.of(block));
    }
}
