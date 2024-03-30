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

    @Modified
    public void m1(int index) {
        M m = listM.get(index);
        m.setI(3);
    }


    @NotModified
    public List<M> getListM() {
        return listM;
    }
}
