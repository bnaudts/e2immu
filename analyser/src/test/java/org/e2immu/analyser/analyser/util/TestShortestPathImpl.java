package org.e2immu.analyser.analyser.util;

import org.e2immu.analyser.analyser.HiddenContentSelector;
import org.e2immu.analyser.analyser.LV;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TestShortestPathImpl extends CommonWG {

    @Test
    public void testDelay() {
        long l = ShortestPathImpl.toDistanceComponent(delay);
        long h = ShortestPathImpl.toDistanceComponentHigh(delay);
        assertTrue(l < h);
    }

    @Test
    public void testAssigned() {
        long l = ShortestPathImpl.toDistanceComponent(LV.LINK_ASSIGNED);
        assertEquals(ShortestPathImpl.ASSIGNED, l);
        long h = ShortestPathImpl.toDistanceComponentHigh(LV.LINK_ASSIGNED);
        assertEquals(ShortestPathImpl.ASSIGNED_H, h);
    }

    @Test
    public void testCommonHC() {
        LV commonHC = v4;
        long l = ShortestPathImpl.toDistanceComponent(commonHC);
        assertEquals(ShortestPathImpl.INDEPENDENT_HC, l);
        long h = ShortestPathImpl.toDistanceComponentHigh(commonHC);
        assertEquals(ShortestPathImpl.INDEPENDENT_HC_H, h);
    }

    @Test
    public void testHCMutable() {
        long m = ShortestPathImpl.toDistanceComponent(LV.LINK_HC_MUTABLE);
        assertEquals(ShortestPathImpl.HC_MUTABLE, m);
        long mh = ShortestPathImpl.toDistanceComponentHigh(LV.LINK_HC_MUTABLE);
        assertEquals(ShortestPathImpl.HC_MUTABLE_H, mh);
    }
}
