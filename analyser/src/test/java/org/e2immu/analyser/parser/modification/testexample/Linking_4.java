package org.e2immu.analyser.parser.modification.testexample;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class Linking_4<A, B> {

    record Pair<S, T>(S s, T t) {
    }

    /*
    test extraction *-4-0 from 0.0,0.1 -4- 0,1
     */
    static <X, Y> Pair<Y, X> method(Map<X, Y> map) {
        // 0.0,0.1 -4- 0,1
        Set<Map.Entry<X, Y>> entrySet = map.entrySet();
        List<Map.Entry<X, Y>> entryList = new ArrayList<>(entrySet);
        Map.Entry<X, Y> entry = entryList.get(0);
        X x = entry.getKey();
        Y y = entry.getValue();
        return new Pair<>(y, x);
    }

    /*
    abs links to this as *-0, but also as 0,1-4-0.0,0.1
     */
    Map<A, B> abs;

    Pair<B, A> extract() {
        return method(abs);
    }
}
