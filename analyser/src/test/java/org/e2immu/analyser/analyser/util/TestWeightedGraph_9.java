package org.e2immu.analyser.analyser.util;

import org.e2immu.analyser.analyser.HiddenContentSelector;
import org.e2immu.analyser.analyser.LV;
import org.e2immu.analyser.model.variable.Variable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

public class TestWeightedGraph_9 extends CommonWG {

    Variable map, keys, values;
    WeightedGraph wg;
    ShortestPath shortestPath;

    // M is modifiable
    // Map<M,V> map = ...; Set<M> keys = map.keySet(); Collection<V> values = map.values()

    @BeforeEach
    public void beforeEach() {
        keys = makeVariable("keys");
        map = makeVariable("map");
        values = makeVariable("values");

        wg = new WeightedGraphImpl();
        HiddenContentSelector m0 = new HiddenContentSelector.CsSet(Map.of(0, true));
        HiddenContentSelector hc0 = HiddenContentSelector.CsSet.selectTypeParameter(0);
        HiddenContentSelector hc1 = HiddenContentSelector.CsSet.selectTypeParameter(1);

        LV map_4_keys = LV.createHC(m0, m0);
        LV map_4_values = LV.createHC(hc1, hc0);

        assertEquals("<1>-4-<0>", map_4_values.toString());
        assertEquals("<0M>-4-<0M>", map_4_keys.reverse().toString());

        wg.addNode(map, Map.of(keys, map_4_keys, values, map_4_values));

        shortestPath = wg.shortestPath();
        assertEquals("0(1:4;2:3)1(0:4)2(0:3)", ((ShortestPathImpl) shortestPath).getCacheKey());
    }

    @Test
    @DisplayName("start in keys")
    public void testK() {
        Map<Variable, LV> startAt = shortestPath.links(keys, null);
        assertEquals(2, startAt.size());
        assertEquals(v0, startAt.get(keys));
        assertTrue(startAt.get(map).isCommonHC());
        assertNull(startAt.get(values));
    }

    @Test
    @DisplayName("start in keys, limit dependent")
    public void testKD() {
        Map<Variable, LV> startAt = shortestPath.links(keys, LV.LINK_DEPENDENT);
        assertEquals(1, startAt.size());
        assertEquals(v0, startAt.get(keys));
        assertNull(startAt.get(map));
        assertNull(startAt.get(values));
    }

    @Test
    @DisplayName("start in keys, limit HC_MUTABLE")
    public void testKM() {
        Map<Variable, LV> startAt = shortestPath.links(keys, LV.LINK_HC_MUTABLE);
        assertEquals(2, startAt.size());
        assertEquals(v0, startAt.get(keys));
        assertTrue(startAt.get(map).isCommonHC());
        assertNull(startAt.get(values));
    }

    @Test
    @DisplayName("start in values")
    public void testV() {
        Map<Variable, LV> startAt = shortestPath.links(values, null);
        assertEquals(2, startAt.size());
        assertEquals(v0, startAt.get(values));
        assertTrue(startAt.get(map).isCommonHC()); // and not isHcMutable, that's an internal value
        assertNull(startAt.get(keys));
    }

    @Test
    @DisplayName("start in map")
    public void testM() {
        Map<Variable, LV> startAt = shortestPath.links(map, null);
        assertEquals(3, startAt.size());
        assertEquals(v0, startAt.get(map));
        assertTrue(startAt.get(keys).isCommonHC());
        assertTrue(startAt.get(values).isCommonHC());
    }

    @Test
    @DisplayName("start in map, limit HC_MUTABLE")
    public void testMM() {
        Map<Variable, LV> startAt = shortestPath.links(map, LV.LINK_HC_MUTABLE);
        assertEquals(2, startAt.size());
        assertEquals(v0, startAt.get(map));
        assertTrue(startAt.get(keys).isCommonHC());
        assertNull(startAt.get(values));
    }
}
