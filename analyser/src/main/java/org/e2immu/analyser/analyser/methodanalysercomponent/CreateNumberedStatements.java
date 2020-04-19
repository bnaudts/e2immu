package org.e2immu.analyser.analyser.methodanalysercomponent;

import com.google.common.collect.ImmutableList;
import org.e2immu.analyser.analyser.NumberedStatement;
import org.e2immu.analyser.model.CodeOrganization;
import org.e2immu.analyser.model.HasStatements;
import org.e2immu.analyser.model.Statement;
import org.e2immu.analyser.model.statement.Block;
import org.e2immu.analyser.parser.SideEffectContext;
import org.e2immu.annotation.NotModified;
import org.e2immu.annotation.NullNotAllowed;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Stack;

public class CreateNumberedStatements {

    public static NumberedStatement recursivelyCreateNumberedStatements(List<Statement> statements,
                                                                        Stack<Integer> indices,
                                                                        List<NumberedStatement> numberedStatements,
                                                                        SideEffectContext sideEffectContext) {
        int statementIndex = 0;
        NumberedStatement first = null;
        NumberedStatement previous = null;
        for (Statement statement : statements) {
            NumberedStatement numberedStatement = new NumberedStatement(sideEffectContext, statement, join(indices, statementIndex));
            numberedStatements.add(numberedStatement);
            if (previous != null) previous.next.set(Optional.of(numberedStatement));
            previous = numberedStatement;
            if (first == null) first = numberedStatement;
            indices.push(statementIndex);

            int blockIndex = 0;
            List<NumberedStatement> blocks = new ArrayList<>();
            CodeOrganization codeOrganization = statement.codeOrganization();
            if (codeOrganization.statements != Block.EMPTY_BLOCK) {
                blockIndex = createBlock(indices, numberedStatements, sideEffectContext, blockIndex, blocks, codeOrganization.statements);
            }
            for (CodeOrganization subStatements : codeOrganization.subStatements) {
                if (subStatements.statements != Block.EMPTY_BLOCK) {
                    blockIndex = createBlock(indices, numberedStatements, sideEffectContext, blockIndex, blocks, subStatements.statements);
                }
            }
            numberedStatement.blocks.set(ImmutableList.copyOf(blocks));
            indices.pop();

            ++statementIndex;
        }
        if (previous != null)
            previous.next.set(Optional.empty());
        return first;
    }

    private static int createBlock(Stack<Integer> indices, List<NumberedStatement> numberedStatements,
                                   SideEffectContext sideEffectContext, int blockIndex,
                                   List<NumberedStatement> blocks, HasStatements statements) {
        indices.push(blockIndex);
        NumberedStatement firstOfBlock =
                recursivelyCreateNumberedStatements(statements.getStatements(), indices, numberedStatements, sideEffectContext);
        blocks.add(firstOfBlock);
        indices.pop();
        return blockIndex + 1;
    }

    @NotModified
    private static int[] join(@NotModified @NullNotAllowed List<Integer> baseIndices, int index) {
        int[] res = new int[baseIndices.size() + 1];
        int i = 0;
        for (Integer bi : baseIndices) res[i++] = bi;
        res[i] = index;
        return res;
    }

}
