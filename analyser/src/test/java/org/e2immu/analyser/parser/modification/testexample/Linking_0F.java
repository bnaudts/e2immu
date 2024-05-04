package org.e2immu.analyser.parser.modification.testexample;

import java.util.ArrayList;
import java.util.List;

public class Linking_0F {

    // extensible, no type parameters, modifiable
    static class M {
        private int i;

        public int getI() {
            return i;
        }

        public void setI(int i) {
            this.i = i;
        }
    }

    // non-extensible, no type parameters, modifiable, no HC
    static final class FM {
        private int i;

        public int getI() {
            return i;
        }

        public void setI(int i) {
            this.i = i;
        }
    }

    // not extensible, but type parameters
    // interesting example: extends a shallowly analyzed class, so we should take
    static final class FAR<L> extends ArrayList<L> {
    }

    // extensible
    static class E {
        public final int i;

        public E(int i) {
            this.i = i;
        }
    }

    // not extensible, modifiable, no type parameters, but with an extensible field
    static final class FME {
        private E e;

        public E getE() {
            return e;
        }

        public void setE(E e) {
            this.e = e;
        }
    }

    // not extensible, not modifiable, but with an extensible field
    // has HC!
    static final class FE {
        private E e;

        public E getE() {
            return e;
        }

        public void setE(E e) {
            this.e = e;
        }
    }

    static class C1<T> {
        // no type parameters

        private T t;  // ext, non-mod
        private Integer i; // non-ext, non-mod
        private M m; // ext, mod
        private FM fm; // not-ext, mod
        private FAR far; // non-ext, mod, missing TPs

        private List<T> listT;
        private List<Integer> listI;
        private List<M> listM;
        private List<FM> listFM;

        private FAR<T> farT;
        private FAR<Integer> farI;
        private FAR<M> farM;
        private FAR<FM> farFM;


        public T getT() {
            return t;
        }

        public void setT(T t) {
            this.t = t;
        }

        public Integer getI() {
            return i;
        }

        public void setI(Integer i) {
            this.i = i;
        }

        public M getM() {
            return m;
        }

        public void setM(M m) {
            this.m = m;
        }

        public FAR<FM> getFarFM() {
            return farFM;
        }

        public void setFarFM(FAR<FM> farFM) {
            this.farFM = farFM;
        }

        public FAR getFar() {
            return far;
        }

        public void setFar(FAR far) {
            this.far = far;
        }

        public List<M> getListM() {
            return listM;
        }

        public void setListM(List<M> listM) {
            this.listM = listM;
        }

        public List<FM> getListFM() {
            return listFM;
        }

        public void setListFM(List<FM> listFM) {
            this.listFM = listFM;
        }

        public List<Integer> getListI() {
            return listI;
        }

        public void setListI(List<Integer> listI) {
            this.listI = listI;
        }

        public List<T> getListT() {
            return listT;
        }

        public void setListT(List<T> listT) {
            this.listT = listT;
        }

        public FAR<Integer> getFarI() {
            return farI;
        }

        public void setFarI(FAR<Integer> farI) {
            this.farI = farI;
        }

        public FAR<M> getFarM() {
            return farM;
        }

        public void setFarM(FAR<M> farM) {
            this.farM = farM;
        }

        public FAR<T> getFarT() {
            return farT;
        }

        public void setFarT(FAR<T> farT) {
            this.farT = farT;
        }

        public FM getFm() {
            return fm;
        }

        public void setFm(FM fm) {
            this.fm = fm;
        }
    }
}
