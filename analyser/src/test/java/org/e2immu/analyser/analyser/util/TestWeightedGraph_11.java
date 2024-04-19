package org.e2immu.analyser.analyser.util;

import org.e2immu.analyser.analyser.LV;
import org.e2immu.analyser.model.variable.Variable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

public class TestWeightedGraph_11 extends CommonWG {

    /*
       M m = ms.get(0)
       M n = ms.get(1)

       ms  0M----4----*M m
       *0
       |
       4
       |
       *M
       n
     */
    Variable m, ms, n;
    WeightedGraph wg;
    ShortestPath shortestPath;

    @BeforeEach
    public void beforeEach() {
        m = makeVariable("m");
        ms = makeVariable("ms");
        n = makeVariable("n");

        wg = new WeightedGraphImpl();

        LV link = LV.createHC(new LV.Links(Map.of(LV.ALL_INDICES, new LV.Link(i0, true))));
        assertEquals("*M-4-0M", link.toString());

        wg.addNode(m, Map.of(ms, link));
        wg.addNode(n, Map.of(ms, link));
        shortestPath = wg.shortestPath();
        assertEquals("0(2:*M-4-0M)1(2:*M-4-0M)2(0:0M-4-*M;1:0M-4-*M)",
                ((ShortestPathImpl) shortestPath).getCacheKey());

    }

    @Test
    @DisplayName("start in m")
    public void testM() {
        Map<Variable, LV> startAt = shortestPath.links(m, null);
        assertEquals(2, startAt.size());
        assertEquals(v0, startAt.get(m));
        assertTrue(startAt.get(ms).isCommonHC());
        assertEquals("*M-4-0M", startAt.get(ms).toString());
        assertNull(startAt.get(n));
    }


    @Test
    @DisplayName("start in n")
    public void testN() {
        Map<Variable, LV> startAt = shortestPath.links(n, null);
        assertEquals(2, startAt.size());
        assertEquals(v0, startAt.get(n));
        assertTrue(startAt.get(ms).isCommonHC());
        assertNull(startAt.get(m));
    }

    @Test
    @DisplayName("start in ms")
    public void testMS() {
        Map<Variable, LV> startAt = shortestPath.links(ms, null);
        assertEquals(3, startAt.size());
        assertEquals(v0, startAt.get(ms));
        assertTrue(startAt.get(m).isCommonHC());
        assertTrue(startAt.get(n).isCommonHC());
    }
}
