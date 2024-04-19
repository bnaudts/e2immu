package org.e2immu.analyser.analyser.util;

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
    ShortestPath shortestPath,  shortestPathWg2;

    @BeforeEach
    public void beforeEach() {
        m = makeVariable("m");
        ms = makeVariable("ms");
        selection = makeVariable("selection");

        LV link = LV.createHC(new LV.Links(Map.of(LV.ALL_INDICES, new LV.Link(i0, true))));
        LV hc00 = v4;

        assertEquals("*M-4-0M", link.toString());
        assertEquals("0-4-0", hc00.toString());

        wg = new WeightedGraphImpl();
        wg.addNode(m, Map.of(ms, link, selection, link));

        shortestPath = wg.shortestPath();
        assertEquals("0(1:*M-4-0M;2:*M-4-0M)1(0:0M-4-*M)2(0:0M-4-*M)",
                ((ShortestPathImpl) shortestPath).getCacheKey());


        // an extra edge, 0-4-0
        wg2 = new WeightedGraphImpl();
        wg2.addNode(m, Map.of(ms, link, selection, link));
        wg2.addNode(ms, Map.of(selection, hc00));
        shortestPathWg2 = wg2.shortestPath();
        assertEquals("0(1:*M-4-0M;2:*M-4-0M)1(0:0M-4-*M;2:0-4-0)2(0:0M-4-*M;1:0-4-0)",
                ((ShortestPathImpl) shortestPathWg2).getCacheKey());
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
    }

    @Test
    @DisplayName("start in selection")
    public void testMsMutable() {
        Map<Variable, LV> startAt = shortestPath.links(selection, null);
        assertEquals(3, startAt.size());
        assertEquals(v0, startAt.get(selection));
        assertTrue(startAt.get(m).isCommonHC());
        assertTrue(startAt.get(ms).isCommonHC());
    }

}
