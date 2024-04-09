package org.e2immu.analyser.parser.modification.testexample;

import org.e2immu.annotation.Independent;
import org.e2immu.annotation.Modified;

import java.util.ArrayList;
import java.util.List;

public class Linking_3 {

    /*
    HCT Expression
    List<Expression> cannot be extended.
     */
    static class ExpressionList {
        List<Expression> expressions = new ArrayList<>();

        void add(Expression expression) {
            this.expressions.add(expression);
        }

        // =0-4-0
        Expression get(int index) {
            return expressions.get(index);
        }
    }

    // HC as type parameter
    static class ExpressionList2<E extends Expression> {
        List<E> expressions = new ArrayList<>();

        void add(E expression) {
            this.expressions.add(expression);
        }

        // =0-4-0
        E get(int index) {
            return expressions.get(index);
        }
    }

    interface Expression {
    }

    static class E1 implements Expression {
        String s;
    }

    static class E2 implements Expression {
        int t;
    }

    private final Expression expression;

    Linking_3(@Independent(hc = true) Expression expression) {
        this.expression = expression;
    }

    @Modified
    void doSomething(String s) {
        if (expression instanceof E1 e1) {
            e1.s = s;
        } else if (expression instanceof E2 e2) {
            e2.t = s.length();
        }
    }
}
