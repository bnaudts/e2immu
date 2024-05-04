package org.e2immu.analyser.parser.modification.testexample;

import java.util.*;

public class Linking_0 {

    // the archetypal modifiable type, not extensible
    final static class M {
        private int i;

        public int getI() {
            return i;
        }

        public void setI(int i) {
            this.i = i;
        }
    }

    // Note: List, ArrayList are mutable; subList is dependent, because it
    // returns a list backed by the original one.

    // no linking
    static String m0(List<String> list) {
        return list.get(0);
    }

    // rv *M-4-0M list
    static M m1(List<M> list) {
        return list.get(0);
    }

    // rv ->4->list, corrected to allow modifications

    static M m1bb(List<M> list) {
        ArrayList<M> al = new ArrayList<>(list);
        return al.get(0);
    }

    static M m1b(List<M> list) {
        return new ArrayList<>(list).get(0);
    }

    // common HC
    static <X> X m2(List<X> list) {
        return list.get(0);
    }

    // dependent, regardless of String
    static List<String> m3(List<String> list) {
        return list.subList(0, 1);
    }

    // dependent
    static List<M> m4(List<M> list) {
        return list.subList(0, 1);
    }

    // dependent, regardless of X
    static <X> List<X> m5(List<X> list) {
        return list.subList(0, 1);
    }

    // independent, because of String
    static List<String> m6(List<String> list) {
        return new ArrayList<>(list);
    }

    // dependent, because of M -> downgrade removed; independent HC
    static List<M> m7(List<M> list) {
        return new ArrayList<>(list);
    }

    // independent HC, because of X
    static <X> List<X> m8(List<X> list) {
        return new ArrayList<>(list);
    }

    // independent HC
    static <X> Map<Long, X> m9(Map<Long, X> map) {
        return new HashMap<>(map);
    }

    // independent HC
    static <X> Map<X, String> m10(Map<X, String> map) {
        return new HashMap<>(map);
    }

    // independent HC
    static <X, Y> Map<X, Y> m11(Map<X, Y> map) {
        return new HashMap<>(map);
    }

    // independent
    static Map<Long, String> m12(Map<Long, String> map) {
        return new HashMap<>(map);
    }

    // dependent -> downgrade removed; independent HC
    static <X> Map<X, M> m13(Map<X, M> map) {
        return new HashMap<>(map);
    }

    // dependent
    static List<M> m14(List<M> list) {
        return list.subList(0, 1).subList(0, 1);
    }

    // dependent
    static <X> List<X> m15(List<X> list) {
        return list.subList(0, 1).subList(0, 1);
    }

    // independent HC
    static <X> List<X> m16(List<X> list) {
        return new ArrayList<>(list.subList(0, 1).subList(0, 1));
    }

    // independent HC
    static <X> List<X> m17(List<X> list) {
        return new ArrayList<>(list.subList(0, 1)).subList(0, 1);
    }

    static <X> List<X> m18(X x, List<X> list) {
        Collections.addAll(list, x);
        return list;
    }

    static <X> List<X> m19(X x0, X x1, List<X> list) {
        Collections.addAll(list, x0, x1);
        return list;
    }

    static List<String> m20(String x0, String x1, List<String> list) {
        Collections.addAll(list, x0, x1);
        return list;
    }

    static List<M> m21(M m, List<M> list) {
        Collections.addAll(list, m);
        return list;
    }

    static List<M> m22(M x0, M x1, List<M> list) {
        Collections.addAll(list, x0, x1);
        return list;
    }

    // ensure that it doesn't crash
    static List<M> m23(List<M> list) {
        Collections.addAll(list);
        return list;
    }

    private String string;

    public Collection<String> m24(Collection<String> collection) {
        collection.add(string);
        return collection;
    }

    static <X> M m25(Map<X, M> map, X x) {
        return map.get(x);
    }
}
