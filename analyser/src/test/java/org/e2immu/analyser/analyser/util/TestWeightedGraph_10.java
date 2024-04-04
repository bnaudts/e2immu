package org.e2immu.analyser.analyser.util;

import org.e2immu.analyser.analyser.HiddenContentSelector;
import org.e2immu.analyser.analyser.LV;
import org.e2immu.analyser.model.variable.Variable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

public class TestWeightedGraph_10 extends CommonWG {

    /*
       for(M m: ms) { if(someCriterion(..) selection.add(m); }

       m  *M----4----0M ms
       *M
       |
       4
       |
       0M
       selection
     */
    Variable m, ms, selection;
    WeightedGraph wg;
    ShortestPath shortestPath;

    @BeforeEach
    public void beforeEach() {
        m = makeVariable("m");
        ms = makeVariable("ms");
        selection = makeVariable("selection");

        wg = new WeightedGraphImpl();
        HiddenContentSelector m0 = new HiddenContentSelector.CsSet(Map.of(0, true));

        LV link = LV.createHC(HiddenContentSelector.All.MUTABLE_INSTANCE, m0);

        assertEquals("*M-4-0M", link.toString());

        wg.addNode(m, Map.of(ms, link, selection, link));

        shortestPath = wg.shortestPath();
        assertEquals("0(1:3;2:3)1(0:3)2(0:3)", ((ShortestPathImpl) shortestPath).getCacheKey());
    }

    @Test
    @DisplayName("start in m")
    public void testM() {
        Map<Variable, LV> startAt = shortestPath.links(m, null);
        assertEquals(2, startAt.size());
        assertEquals(v0, startAt.get(m));
        assertTrue(startAt.get(ms).isCommonHC());
        assertTrue(startAt.get(selection).isCommonHC());
    }

    @Test
    @DisplayName("start in ms")
    public void testMs() {
        Map<Variable, LV> startAt = shortestPath.links(ms, null);
        assertEquals(3, startAt.size());
        assertEquals(v0, startAt.get(ms));
        assertTrue(startAt.get(m).isCommonHC());
        assertTrue(startAt.get(selection).isCommonHC());
    }

    @Test
    @DisplayName("start in ms, HC mutable level")
    public void testMsMutable() {
        Map<Variable, LV> startAt = shortestPath.links(ms, LV.LINK_HC_MUTABLE);
        assertEquals(2, startAt.size());
        assertEquals(v0, startAt.get(ms));
        assertTrue(startAt.get(m).isCommonHC());
        assertNull(startAt.get(selection));
    }

}
