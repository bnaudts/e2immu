package org.e2immu.analyser.model.statement;

import org.e2immu.analyser.model.*;
import org.e2immu.analyser.util.SetUtil;
import org.e2immu.analyser.util.StringUtil;

import java.util.Map;
import java.util.Set;

public class AssertStatement implements Statement {

    public final Expression check;
    public final Expression message; // can be null

    public AssertStatement(Expression check, Expression message) {
        this.check = check;
        this.message = message;
    }

    // we're currently NOT adding message!
    @Override
    public CodeOrganization codeOrganization() {
        return new CodeOrganization.Builder().setExpression(check).build();
    }

    @Override
    public Statement translate(Map<? extends Variable, ? extends Variable> translationMap) {
        return new AssertStatement(check.translate(translationMap), message.translate(translationMap));
    }

    @Override
    public String statementString(int indent) {
        StringBuilder sb = new StringBuilder();
        StringUtil.indent(sb, indent);
        sb.append("assert ");
        sb.append(check.expressionString(0));
        if (message != null) {
            sb.append(", ");
            sb.append(message.expressionString(0));
        }
        sb.append(";\n");
        return sb.toString();
    }

    @Override
    public Set<String> imports() {
        return SetUtil.immutableUnion(check.imports(), message == null ? Set.of() : message.imports());
    }

    @Override
    public Set<TypeInfo> typesReferenced() {
        return SetUtil.immutableUnion(check.typesReferenced(), message == null ? Set.of(): message.typesReferenced());
    }

    @Override
    public SideEffect sideEffect(EvaluationContext evaluationContext) {
        return check.sideEffect(evaluationContext);
    }
}
