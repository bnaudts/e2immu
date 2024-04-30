package org.e2immu.analyser.parser.modification.testexample;

import org.e2immu.annotation.ImmutableContainer;

import java.util.ArrayList;
import java.util.List;

/*
Contrast to Linking_0D, where "this.list" is linked -4- to "this", because "this" is immutable HC.
Also tests List.of() and List.copyOf(...), which have a type parameter bound to the method.
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
        this.list = List.copyOf(list);
    }

    List<X> getList() {
        return List.copyOf(list);
    }

    static Linking_0H<M> create(M m) {
        List<M> mList = List.of(m);
        return new Linking_0H<>(mList);
    }

    static Linking_0H<M> create2(M m) {
        List<M> mList = new ArrayList<>();
        mList.add(m);
        return new Linking_0H<>(mList);
    }
}
