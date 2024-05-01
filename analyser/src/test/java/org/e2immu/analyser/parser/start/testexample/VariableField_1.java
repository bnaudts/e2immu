package org.e2immu.analyser.parser.start.testexample;

// accessors and variable fields
public class VariableField_1 {

    static class M {
        private int i;

        public int getI() {
            return i;
        }

        public void setI(int i) {
            this.i = i;
        }
    }

    private M m;

    void method(int j) {
        m = new M();
        m.setI(j);
        int v1 = m.getI();
        System.out.println(v1);
        int v2 = m.getI();
        System.out.println(v2);
        assert v1 == v2;
    }
}
