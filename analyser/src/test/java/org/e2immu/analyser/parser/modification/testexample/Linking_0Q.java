package org.e2immu.analyser.parser.modification.testexample;

import org.e2immu.annotation.Container;

/*
We should not lose information when we encapsulate the pair in the container R,
because the concrete instance of R is properly typed.
 */
public class Linking_0Q {

    static class M {
        private int i;

        public int getI() {
            return i;
        }

        public void setI(int i) {
            this.i = i;
        }
    }

    @Container
    record Pair<F, G>(F f, G g) {
    }

    @Container
    record R<S>(S s) {
        public R {
            assert s != null;
        }
    }

    static <X, Y> R<Pair<X, Y>> method0(X x, Y y) {
        return new R<>(new Pair<>(x, y));
    }

    static <X, Y> R<Pair<Y, X>> method1(X x, Y y) {
        Pair<Y, X> p = new Pair<>(y, x);
        return new R<>(p);
    }

    static <X, Y> Pair<X, Y> method2(Pair<X, Y> in) {
        R<Pair<X, Y>> r = new R<>(in);
        return r.s;
    }

    static <X, Y> Pair<X, Y> method3(X x, Y y) {
        Pair<X, Y> p = new Pair<>(x, y);
        R<Pair<X, Y>> r = new R<>(p);
        return r.s;
    }

    static <X, Y> Pair<X, Y> method4(X x, Y y) {
        return new R<>(new Pair<X, Y>(x, y)).s;
    }

    static <X, Y> Pair<Y, X> method5(X x, Y y) {
        return new R<>(new Pair<Y, X>(y, x)).s;
    }
}
