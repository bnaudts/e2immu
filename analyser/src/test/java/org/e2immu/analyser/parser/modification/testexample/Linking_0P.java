package org.e2immu.analyser.parser.modification.testexample;

public class Linking_0P {

    static class M {
        private int i;

        public int getI() {
            return i;
        }

        public void setI(int i) {
            this.i = i;
        }
    }

    static class N {
        private int i;

        public int getI() {
            return i;
        }

        public void setI(int i) {
            this.i = i;
        }
    }

    record Pair<F, G>(F f, G g) {
    }

    static <X, Y> Pair<X, Y> create0(X x, Y y) {
        return new Pair<>(x, y);
    }

    static <X> Pair<X, M> create1(X x, M m) {
        return new Pair<>(x, m);
    }

    static <X> Pair<X, M> create2(X x, M m) {
        //noinspection ALL
        Pair<X, M> p = new Pair<>(x, m);
        return p;
    }

    static Pair<N, M> create3(N n, M m) {
        //noinspection ALL
        Pair<N, M> p = new Pair<>(n, m);
        return p;
    }

    static Pair<Integer, M> create4(Integer i, M m) {
        //noinspection ALL
        Pair<Integer, M> p = new Pair<>(i, m);
        return p;
    }

    static <X, Y> Pair<Y, X> reverse(Pair<X, Y> pair) {
        return new Pair<>(pair.g, pair.f);
    }
}
