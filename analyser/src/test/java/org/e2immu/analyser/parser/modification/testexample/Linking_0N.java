package org.e2immu.analyser.parser.modification.testexample;

import org.e2immu.annotation.Modified;
import org.e2immu.annotation.NotModified;

import java.util.ArrayList;
import java.util.List;

public class Linking_0N {

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

    static void m1(@Modified(onlyHcs = {0}) List<M> list, int index) {
        M m = list.get(index);
        m.setI(3);
    }

}
