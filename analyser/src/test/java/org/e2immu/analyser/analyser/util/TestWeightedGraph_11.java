package org.e2immu.analyser.analyser.util;

import org.e2immu.analyser.analyser.HiddenContentSelector;
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
        HiddenContentSelector m0 = new HiddenContentSelector.CsSet(Map.of(0, true));

        LV link = LV.createHC(HiddenContentSelector.All.MUTABLE_INSTANCE, m0);

        assertEquals("*M-4-0M", link.toString());

        wg.addNode(m, Map.of(ms, link));
        wg.addNode(n, Map.of(ms, link));
        shortestPath = wg.shortestPath();
        assertEquals("0(2:3)1(2:3)2(0:3;1:3)", ((ShortestPathImpl) shortestPath).getCacheKey());
    }

    @Test
    @DisplayName("start in m")
    public void testM() {
        Map<Variable, LV> startAt = shortestPath.links(m, null);
        assertEquals(3, startAt.size());
        assertEquals(v0, startAt.get(m));
        assertTrue(startAt.get(ms).isCommonHC());
        assertTrue(startAt.get(n).isCommonHC());
    }

    @Test
    @DisplayName("start in m, HC mutable level")
    public void testMm() {
        Map<Variable, LV> startAt = shortestPath.links(ms, LV.LINK_HC_MUTABLE);
        assertEquals(2, startAt.size());
        assertEquals(v0, startAt.get(ms));
        assertTrue(startAt.get(m).isCommonHC());
        assertTrue(startAt.get(n).isCommonHC());
    }

}
