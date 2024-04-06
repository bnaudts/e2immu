package org.e2immu.analyser.parser.modification.testexample;

import org.e2immu.annotation.Modified;
import org.e2immu.annotation.NotModified;

import java.util.*;

public class Linking_0M {

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

    private final List<M> listM = new ArrayList<>();
    private final List<M> listM2 = new ArrayList<>();

    @Modified
    void m1(int index) {
        M m = listM.get(index);
        m.setI(3);
    }


    @NotModified
    List<M> getListM() {
        return listM;
    }

    List<M> getListM2() {
        return listM2;
    }

    @Modified
    void m2(M m) {
        listM.add(m);
        listM2.add(m);
    }
}
