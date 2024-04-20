package org.e2immu.analyser.analyser.util;

import org.e2immu.analyser.analyser.LV;
import org.e2immu.analyser.model.variable.Variable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class TestWeightedGraph_15 extends CommonWG {

    /*
       rv *--4--0 s *--4--0 supplier
     */
    Variable s, rv, supplier;
    WeightedGraph wg;
    ShortestPath shortestPath;

    @BeforeEach
    public void beforeEach() {
        s = makeVariable("s");
        rv = makeVariable("rv");
        supplier = makeVariable("supplier");

        wg = new WeightedGraphImpl();

        LV link = LV.createHC(new LV.Links(Map.of(LV.ALL_INDICES, new LV.Link(i0, false))));
        assertEquals("*-4-0", link.toString());

        wg.addNode(s, Map.of(supplier, link));
        wg.addNode(rv, Map.of(s, link));
        shortestPath = wg.shortestPath();
        assertEquals("0(1:0-4-*)1(0:*-4-0;2:0-4-*)2(1:*-4-0)",
                ((ShortestPathImpl) shortestPath).getCacheKey());

    }

    @Test
    @DisplayName("start in rv")
    public void testM() {
        Map<Variable, LV> startAt = shortestPath.links(rv, null);
        assertEquals(3, startAt.size());
        assertEquals(v0, startAt.get(rv));
        assertEquals("*-4-0", startAt.get(s).toString());
        assertEquals("*-4-0", startAt.get(supplier).toString());
    }

    @Test
    @DisplayName("start in s")
    public void testMS() {
        Map<Variable, LV> startAt = shortestPath.links(s, null);
        assertEquals(3, startAt.size());
        assertEquals(v0, startAt.get(s));
        assertEquals("0-4-*", startAt.get(rv).toString());
        assertEquals("*-4-0", startAt.get(supplier).toString());
    }
}
