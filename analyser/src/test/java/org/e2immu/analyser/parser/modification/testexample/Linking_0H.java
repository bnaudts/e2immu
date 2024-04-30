package org.e2immu.analyser.parser.modification.testexample;

import org.e2immu.annotation.ImmutableContainer;

import java.util.ArrayList;
import java.util.List;

/*
Contrast to Linking_0D, where "this.list" is linked -4- to "this", because "this" is immutable HC.
Also tests List.copyOf(...) which has a type parameter bound to the method.
 */
@ImmutableContainer(hc = true)
public class Linking_0H<X> {

    static class M {
        private int i;

        public int getI() {
            return i;
        }

        public void setI(int i) {
            this.i = i;
        }
    }

    private final List<X> list;

    Linking_0H(List<X> list) {
        this.list = new ArrayList<>(list);// FIXME  List.copyOf(list);
    }

    List<X> getList() {
        return new ArrayList<>(list);
    }

    static Linking_0H<M> create(M m) {
        List<M> mList = List.of(m);
        return new Linking_0H<>(mList);
    }
}
