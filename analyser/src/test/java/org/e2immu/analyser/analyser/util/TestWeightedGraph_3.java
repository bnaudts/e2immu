/*
 * e2immu: a static code analyser for effective and eventual immutability
 * Copyright 2020-2021, Bart Naudts, https://www.e2immu.org
 *
 * This program is free software: you can redistribute it and/or modify it under the
 * terms of the GNU Lesser General Public License as published by the Free Software
 * Foundation, either version 3 of the License, or (at your option) any later version.
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE.  See the GNU Lesser General Public License for
 * more details. You should have received a copy of the GNU Lesser General Public
 * License along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.e2immu.analyser.analyser.util;

import org.e2immu.analyser.analyser.*;
import org.e2immu.analyser.model.variable.Variable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.e2immu.analyser.analyser.LV.LINK_DEPENDENT;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class TestWeightedGraph_3 extends CommonWG {

    Variable element, subList, copy, list;
    WeightedGraph wg;

    @BeforeEach
    public void beforeEach() {
        /*
        element *-4-0 subList 0--2--0 list 0--4--0 copy
         */
        element = makeVariable("e");
        subList = makeVariable("s");
        copy = makeVariable("c");
        list = makeVariable("l");

        wg = new WeightedGraphImpl();
        LV.Links star0 = new LV.Links(Map.of(LV.ALL_INDICES, new LV.Link(new LV.Indices(0), true)));
        LV star0Lv = LV.createHC(star0);
        assertEquals("*M-4-0M", star0Lv.toString());
        wg.addNode(element, Map.of(subList, star0Lv));
        LV.Links d00M = new LV.Links(Map.of(i0, new LV.Link(i0, true)));
        LV d00Lv = LV.createDependent(d00M);
        assertEquals("0M-2-0M", d00Lv.toString());
        wg.addNode(subList, Map.of(list, d00Lv));
        LV hcd00Lv = LV.createHC(d00M);
        assertEquals("0M-4-0M", hcd00Lv.toString());
        wg.addNode(list, Map.of(copy, hcd00Lv));
    }

    @Test
    public void test1() {
        Map<Variable, LV> links = wg.shortestPath().links(element, null);
        assertEquals(4, links.size());
        assertEquals("-0-", links.get(element).toString());
        assertEquals("*M-4-0M", links.get(subList).toString());
        assertEquals("*M-4-0M", links.get(list).toString());
        assertEquals("*M-4-0M", links.get(copy).toString());
    }

    @Test
    public void test2() {
        Map<Variable, LV> links = wg.shortestPath().links(subList, null);
        assertEquals(4, links.size());
        assertEquals("-0-", links.get(subList).toString());
        assertEquals("0M-4-*M", links.get(element).toString());
        assertEquals("0M-2-0M", links.get(list).toString());
        assertEquals("0M-4-0M", links.get(copy).toString());
    }

    @Test
    public void test3() {
        Map<Variable, LV> links = wg.shortestPath().links(list, null);
        assertEquals(4, links.size());
        assertEquals("-0-", links.get(list).toString());
        assertEquals("0M-4-*M", links.get(element).toString());
        assertEquals("0M-2-0M", links.get(subList).toString());
        assertEquals("0M-4-0M", links.get(copy).toString());
    }

    @Test
    public void test4() {
        Map<Variable, LV> links = wg.shortestPath().links(copy, null);
        assertEquals(4, links.size());
        assertEquals("-0-", links.get(copy).toString());
        assertEquals("0M-4-*M", links.get(element).toString());
        assertEquals("0M-4-0M", links.get(subList).toString());
        assertEquals("0M-4-0M", links.get(list).toString());
    }
}
