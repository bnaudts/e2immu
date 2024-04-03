package org.e2immu.analyser.parser.modification.testexample;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

public class Linking_2 {

    // the archetypal modifiable type
    static class M {
        private int i;

        public int getI() {
            return i;
        }

        public void setI(int i) {
            this.i = i;
        }
    }

    static List<String> m1(List<String> strings, Predicate<String> selector) {
        List<String> selection = new ArrayList<>();
        for(String string: strings) {
            if(selector.test(string)) {
                selection.add(string);
            }
        }
        return selection;
    }

    static <X> List<X> m2(List<X> xs, Predicate<X> selector) {
        List<X> selection = new ArrayList<>();
        for(X x: xs) {
            if(selector.test(x)) {
                selection.add(x);
            }
        }
        return selection;
    }

    static List<M> m3(List<M> ms, Predicate<M> selector) {
        List<M> selection = new ArrayList<>();
        for(M m: ms) {
            if(selector.test(m)) {
                selection.add(m);
            }
        }
        return selection;
    }
}
