package org.e2immu.analyser.model.statement;

import org.e2immu.analyser.analyser.NumberedStatement;
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.util.StringUtil;

import java.util.List;

public class AssertStatement extends StatementWithStructure {

    public final Expression message; // can be null

    public AssertStatement(Expression check, Expression message) {
        // IMPORTANT NOTE: we're currently NOT adding message!
        // we regard it as external to the code
        super(new CodeOrganization.Builder().setExpression(check).build());
        this.message = message;
    }

    @Override
    public Statement translate(TranslationMap translationMap) {
        return new AssertStatement(translationMap.translateExpression(codeOrganization.expression), message);
    }

    @Override
    public String statementString(int indent, NumberedStatement numberedStatement) {
        StringBuilder sb = new StringBuilder();
        StringUtil.indent(sb, indent);
        sb.append("assert ");
        sb.append(codeOrganization.expression.expressionString(0));
        if (message != null) {
            sb.append(", ");
            sb.append(message.expressionString(0));
        }
        sb.append(";\n");
        return sb.toString();
    }

    @Override
    public List<? extends Element> subElements() {
        return List.of(codeOrganization.expression);
    }
}
