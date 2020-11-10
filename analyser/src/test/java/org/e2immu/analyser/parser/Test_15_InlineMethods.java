package org.e2immu.analyser.parser;

import org.e2immu.analyser.config.DebugConfiguration;
import org.e2immu.analyser.config.TypeContextVisitor;
import org.e2immu.analyser.model.MethodInfo;
import org.junit.Assert;
import org.junit.Test;

import java.io.IOException;

public class Test_15_InlineMethods extends CommonTestRunner {
    public Test_15_InlineMethods() {
        super(false);
    }

    TypeContextVisitor typeContextVisitor = typeContext -> {
        MethodInfo unaryMinusInt = typeContext.getPrimitives().unaryMinusOperatorInt;
        Assert.assertEquals("int.-(int)", unaryMinusInt.fullyQualifiedName());
    };

    @Test
    public void test() throws IOException {
        testClass("InlineMethods", 0, 0, new DebugConfiguration.Builder()
                .addTypeContextVisitor(typeContextVisitor)
                .build());
    }

}
