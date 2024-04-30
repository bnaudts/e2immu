package org.e2immu.analyser.parser.modification.testexample;

import org.e2immu.support.SetOnce;

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

    record R<F, G>(Pair<F, G> pair) {
        public R {
            assert pair != null;
        }
    }

    record R1<F, G>(SetOnce<Pair<F, G>> setOncePair) {
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

    static <X, Y> Pair<Y, X> reverse2(Pair<X, Y> pair) {
        return new Pair<>(pair.g(), pair.f());
    }

    static <X, Y> Pair<Y, X> reverse3(R<X, Y> r) {
        return new Pair<>(r.pair.g, r.pair.f);
    }

    static <X, Y> R<Y, X> reverse4(R<X, Y> r) {
        return new R<>(new Pair<>(r.pair.g, r.pair.f));
    }

    static <X, Y> R<Y, X> reverse4b(R<X, Y> r) {
        Pair<Y, X> yxPair = new Pair<>(r.pair.g, r.pair.f);
        return new R<>(yxPair);
    }

    static <X, Y> R<Y, X> reverse5(R<X, Y> r) {
        return new R(new Pair(r.pair.g, r.pair.f));
    }

    static <X, Y> R<Y, X> reverse6(X x, Y y) {
        return new R<>(new Pair<>(y, x));
    }

    static <X, Y> R<Y, X> reverse7(R<X, Y> r1, R<X, Y> r2) {
        return new R<>(new Pair<>(r2.pair.g, r1.pair.f));
    }
}
