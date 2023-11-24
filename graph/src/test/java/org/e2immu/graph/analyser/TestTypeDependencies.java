package org.e2immu.graph.analyser;

import org.e2immu.graph.op.BreakCycles;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class TestTypeDependencies {

    // cp analyser/build/e2immuGraph/typeDependencies.gml analyser/build/e2immuGraph/packageDependenciesBasedOnTypeGraph.gml  graph/src/test/resources/org/e2immu/graph

    @Test
    public void test() throws IOException {
        Main main = new Main();
        BreakCycles.Linearization<TypeGraphIO.Node> lin = main.go(new String[]{Main.CLASSPATH
                + "org/e2immu/graph/typeDependencies.gml", "sequential"});
        assertEquals(31, lin.maxCycleSize());
        assertEquals(104, lin.actionLog().size());
        assertEquals(9, lin.list().size());
    }

    @Test
    public void testParallel() throws IOException {
        Main.main(new String[]{Main.CLASSPATH + "org/e2immu/graph/typeDependencies.gml", "parallel", "0.5"});
    }

    @Test
    public void test2() throws IOException {
        Main main = new Main();
        BreakCycles.Linearization<TypeGraphIO.Node> lin = main.go(new String[]{Main.CLASSPATH
                + "org/e2immu/graph/packageDependenciesBasedOnTypeGraph.gml"});
        assertEquals(9, lin.list().size());
        assertEquals(5, lin.actionLog().size());
        assertEquals(36, lin.maxCycleSize());
    }
}
