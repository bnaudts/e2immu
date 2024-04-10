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
    WeightedGraph wg, wg2;
    ShortestPath shortestPath, shortestPathM, shortestPathWg2, shortestPathWg2M;

    @BeforeEach
    public void beforeEach() {
        m = makeVariable("m");
        ms = makeVariable("ms");
        selection = makeVariable("selection");

        HiddenContentSelector m0 = new HiddenContentSelector.CsSet(Map.of(0, true));
        HiddenContentSelector hc0 = new HiddenContentSelector.CsSet(Map.of(0, false));
        LV link = LV.createHC(HiddenContentSelector.All.MUTABLE_INSTANCE, m0);
        LV hc00 = LV.createHC(hc0, hc0);

        assertEquals("*M-4-0M", link.toString());
        assertEquals("0-4-0", hc00.toString());

        wg = new WeightedGraphImpl();
        wg.addNode(m, Map.of(ms, link, selection, link));
        shortestPathM = wg.shortestPath(true);
        String cacheKeyModification = "0(1:3;2:3)1()2()";
        assertEquals(cacheKeyModification, ((ShortestPathImpl) shortestPathM).getCacheKey());
        shortestPath = wg.shortestPath(false);
        assertEquals("0(1:3;2:3)1(0:3)2(0:3)", ((ShortestPathImpl) shortestPath).getCacheKey());


        // an extra edge, 0-4-0
        wg2 = new WeightedGraphImpl();
        wg2.addNode(m, Map.of(ms, link, selection, link));
        wg2.addNode(ms, Map.of(selection, hc00));
        shortestPathWg2M = wg2.shortestPath(true);
        assertEquals(cacheKeyModification, ((ShortestPathImpl) shortestPathWg2M).getCacheKey());
        shortestPathWg2 = wg2.shortestPath(false);
        assertEquals("0(1:3;2:3)1(0:3;2:4)2(0:3;1:4)", ((ShortestPathImpl) shortestPathWg2).getCacheKey());
    }

    @Test
    @DisplayName("start in m")
    public void testM() {
        for (ShortestPath sp : new ShortestPath[]{shortestPath, shortestPathWg2}) {
            Map<Variable, LV> startAt = sp.links(m, null);
            assertEquals(3, startAt.size());
            assertEquals(v0, startAt.get(m));
            assertTrue(startAt.get(ms).isCommonHC());
            assertTrue(startAt.get(selection).isCommonHC());
        }
        for (ShortestPath sp : new ShortestPath[]{shortestPathM, shortestPathWg2M}) {
            Map<Variable, LV> startAtM = sp.links(m, null);
            assertEquals(3, startAtM.size());
            assertEquals(v0, startAtM.get(m));
            assertTrue(startAtM.get(ms).isCommonHC());
            assertTrue(startAtM.get(selection).isCommonHC());
        }
    }

    @Test
    @DisplayName("start in ms")
    public void testMs() {
        for (ShortestPath sp : new ShortestPath[]{shortestPath, shortestPathWg2}) {
            Map<Variable, LV> startAt = sp.links(ms, null);
            assertEquals(3, startAt.size());
            assertEquals(v0, startAt.get(ms));
            assertTrue(startAt.get(m).isCommonHC());
            assertTrue(startAt.get(selection).isCommonHC());
        }
        for (ShortestPath sp : new ShortestPath[]{shortestPathM, shortestPathWg2M}) {
            Map<Variable, LV> startAtM = sp.links(ms, null);
            assertEquals(1, startAtM.size());
            assertEquals(v0, startAtM.get(ms));
            assertNull(startAtM.get(m));
            assertNull(startAtM.get(selection));
        }
    }

    @Test
    @DisplayName("start in selection")
    public void testMsMutable() {
        Map<Variable, LV> startAt = shortestPath.links(selection, null);
        assertEquals(3, startAt.size());
        assertEquals(v0, startAt.get(selection));
        assertTrue(startAt.get(m).isCommonHC());
        assertTrue(startAt.get(ms).isCommonHC());

        Map<Variable, LV> startAtM = shortestPathM.links(selection, null);
        assertEquals(1, startAtM.size());
        assertEquals(v0, startAtM.get(selection));
        assertNull(startAtM.get(m));
        assertNull(startAtM.get(ms));
    }

}
