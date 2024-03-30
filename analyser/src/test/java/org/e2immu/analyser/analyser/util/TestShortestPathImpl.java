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
        long lh = ShortestPathImpl.fromHighToLow(h);
        assertEquals(l, lh);
    }

    @Test
    public void testAssigned() {
        long l = ShortestPathImpl.toDistanceComponent(LV.LINK_ASSIGNED);
        assertEquals(ShortestPathImpl.ASSIGNED, l);
        long h = ShortestPathImpl.toDistanceComponentHigh(LV.LINK_ASSIGNED);
        assertEquals(ShortestPathImpl.ASSIGNED_H, h);
        long lh = ShortestPathImpl.fromHighToLow(h);
        assertEquals(l, lh);
    }

    @Test
    public void testCommonHC() {
        LV commonHC = LV.createHC(HiddenContentSelector.All.INSTANCE, HiddenContentSelector.All.INSTANCE);
        long l = ShortestPathImpl.toDistanceComponent(commonHC);
        assertEquals(ShortestPathImpl.INDEPENDENT_HC, l);
        long h = ShortestPathImpl.toDistanceComponentHigh(commonHC);
        assertEquals(ShortestPathImpl.INDEPENDENT_HC_H, h);
        long lh = ShortestPathImpl.fromHighToLow(h);
        assertEquals(l, lh);
    }

    @Test
    public void testHCMutable() {
        LV hcMutable = LV.createHC(HiddenContentSelector.All.MUTABLE_INSTANCE, HiddenContentSelector.All.MUTABLE_INSTANCE);
        long m = ShortestPathImpl.toDistanceComponent(hcMutable);
        assertEquals(ShortestPathImpl.HC_MUTABLE, m);
        long mh = ShortestPathImpl.toDistanceComponentHigh(hcMutable);
        assertEquals(ShortestPathImpl.HC_MUTABLE_H, mh);
        long mhl = ShortestPathImpl.fromHighToLow(mh);
        assertEquals(m, mhl);
    }
}
