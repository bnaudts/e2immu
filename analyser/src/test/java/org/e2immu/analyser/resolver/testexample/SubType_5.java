package org.e2immu.analyser.resolver.testexample;

public class SubType_5 {

    interface Iterator<T> {
        T next();
    }

    static class ArrayList<T> {

        private final T t;

        public ArrayList(T t) {
            this.t = t;
        }

        private class Itr implements Iterator<T> {
            @Override
            public T next() {
                return t;
            }
        }
    }
}
