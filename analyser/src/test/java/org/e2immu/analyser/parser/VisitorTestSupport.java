
/*
 * e2immu: a static code analyser for effective and eventual immutability
 * Copyright 2020-2021, Bart Naudts, https://www.e2immu.org
 *
 * This program is free software: you can redistribute it and/or modify it under the
 * terms of the GNU Lesser General Public License as published by the Free Software
 * Foundation, either version 3 of the License, or (at your option) any later version.
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE.  See the GNU Lesser General Public License for
 * more details. You should have received a copy of the GNU Lesser General Public
 * License along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.e2immu.analyser.parser;

import ch.qos.logback.classic.Level;
import org.e2immu.analyser.analyser.AnalysisStatus;
import org.e2immu.analyser.analyser.DV;
import org.e2immu.analyser.analyser.LinkedVariables;
import org.e2immu.analyser.analyser.Property;
import org.e2immu.analyser.config.*;
import org.e2immu.analyser.inspector.TypeContext;
import org.e2immu.analyser.model.Expression;
import org.e2immu.analyser.model.impl.LocationImpl;
import org.e2immu.analyser.output.Formatter;
import org.e2immu.analyser.output.FormattingOptions;
import org.e2immu.analyser.output.OutputBuilder;
import org.e2immu.analyser.resolver.SortedType;
import org.e2immu.analyser.visitor.CommonVisitorData;
import org.e2immu.analyser.visitor.FieldAnalyserVisitor;
import org.e2immu.analyser.visitor.StatementAnalyserVariableVisitor;
import org.e2immu.analyser.visitor.StatementAnalyserVisitor;
import org.junit.jupiter.api.BeforeAll;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.e2immu.analyser.util.Logger.LogTarget.*;
import static org.junit.jupiter.api.Assertions.*;

public abstract class VisitorTestSupport {

    protected void assertSubMap(Map<AnalysisStatus, Set<String>> expect, Map<String, AnalysisStatus> statuses) {
        expect.forEach((as, set) -> set.forEach(label -> assertEquals(as, statuses.get(label),
                "Expected " + as + " for " + label + "; map is\n" + statuses)));
    }

    public void assertDvInitial(StatementAnalyserVariableVisitor.Data d, DV expect, Property property) {
        DV value = d.variableInfoContainer().getPreviousOrInitial().getProperty(property);
        assertEquals(expect, value);
    }

    public void assertDv(CommonVisitorData d, DV expect, Property property) {
        DV value = d.getProperty(property);
        assertEquals(expect, value);
    }

    public void assertDvInitial(StatementAnalyserVariableVisitor.Data d, String delayed, int delayedBeforeIteration, DV expect, Property property) {
        DV value = d.variableInfoContainer().getPreviousOrInitial().getProperty(property);
        if (d.iteration() < delayedBeforeIteration) {
            assertEquals(delayed, value.causesOfDelay().toString(), value.isDone() ? "Expected delay in iteration " + d.iteration() + "<=" + delayedBeforeIteration + ", but got " + value + " for property " + property :
                    "Expected delay " + delayed + ", but got " + value + " in iteration " + d.iteration() + "<" + delayedBeforeIteration + " for property " + property);
        } else {
            assertEquals(expect, value, "Expected " + expect + " from iteration " + d.iteration() + ">=" + delayedBeforeIteration + ", but got " + value + " for property " + property);
        }
    }

    public void assertDv(CommonVisitorData d, int delayedBeforeIteration, DV expect, Property property) {
        DV value = d.getProperty(property);
        if (d.iteration() < delayedBeforeIteration) {
            assertTrue(value == null || value.isDelayed(),
                    "Expected delay in iteration " + d.iteration() + "<" + delayedBeforeIteration + ", but got " + value + " for property " + property);
        } else {
            assertEquals(expect, value, "Expected " + expect + " from iteration " + d.iteration() + ">=" + delayedBeforeIteration + ", but got " + value + " for property " + property);
        }
    }

    public void assertDv(CommonVisitorData d, String delayed, int delayedBeforeIteration, DV expect, Property property) {
        DV value = d.getProperty(property);
        if (d.iteration() < delayedBeforeIteration) {
            assertEquals(delayed, value.toString(),
                    "Expected delay in iteration " + d.iteration() + "<" + delayedBeforeIteration + ", but got " + value + " for property " + property);
        } else {
            assertEquals(expect, value, "Expected " + expect + " from iteration " + d.iteration() + ">=" + delayedBeforeIteration + ", but got " + value + " for property " + property);
        }
    }

    public void assertDv(StatementAnalyserVisitor.Data d, int delayedBeforeIteration, DV expect, DV actual) {
        if (d.iteration() < delayedBeforeIteration) {
            assertTrue(actual.isDelayed(),
                    "Expected delay in iteration " + d.iteration() + "<" + delayedBeforeIteration + ", but got " + actual);
        } else {
            assertEquals(expect, actual, "Expected " + expect + " from iteration " + d.iteration() + ">=" + delayedBeforeIteration + ", but got " + actual);
        }
    }

    public void assertDv(StatementAnalyserVisitor.Data d, int delayedBeforeIteration, AnalysisStatus expect, AnalysisStatus actual) {
        if (d.iteration() < delayedBeforeIteration) {
            assertTrue(actual.isDelayed(),
                    "Expected delay in iteration " + d.iteration() + "<" + delayedBeforeIteration + ", but got " + actual);
        } else {
            assertEquals(expect, actual, "Expected " + expect + " from iteration " + d.iteration() + ">=" + delayedBeforeIteration + ", but got " + actual);
        }
    }

    public void assertCurrentValue(StatementAnalyserVariableVisitor.Data d, int delayedBeforeIteration, String causesOfDelay, String value) {
        if (d.iteration() < delayedBeforeIteration) {
            assertTrue(d.currentValue().isDelayed(), "Expected current value to be delayed in iteration " + d.iteration() + "<" + delayedBeforeIteration + ", but was " + d.currentValue() + " for variable " + d.variableName());
            assertEquals(extract(causesOfDelay, d.iteration()), d.currentValue().causesOfDelay().toString());
        } else {
            assertTrue(d.currentValue().isDone(), "Expected current value to be done in iteration " + d.iteration() + ">=" + delayedBeforeIteration + ", but got " + d.currentValue()
                    .causesOfDelay() + " for variable " + d.variableName());
            assertEquals(value, d.currentValue().toString());
        }
    }

    /*
    a -> a
    a|b -> abbbb
    a||b -> aabbbb
    a|b||c -> abbccc
     */
    private static String extract(String causesOfDelay, int iteration) {
        int pipe = causesOfDelay.indexOf('|');
        if (pipe >= 0) {
            String[] split = causesOfDelay.split("\\|");
            if (iteration >= split.length) return split[split.length - 1];
            int pos = iteration;
            while (pos > 0 && split[pos].isEmpty()) pos--;
            return split[pos];
        }
        return causesOfDelay;
    }

    public void assertInitialValue(StatementAnalyserVariableVisitor.Data d, int delayedBeforeIteration, String causesOfDelay, String value) {
        Expression initialValue = d.variableInfoContainer().getPreviousOrInitial().getValue();
        if (d.iteration() < delayedBeforeIteration) {
            assertTrue(initialValue.isDelayed(), "Expected current value to be delayed in iteration " + d.iteration() + "<" + delayedBeforeIteration + ", but was " + initialValue + " for variable " + d.variableName());
            assertEquals(causesOfDelay, initialValue.causesOfDelay().toString());
        } else {
            assertTrue(initialValue.isDone(), "Expected current value to be done in iteration " + d.iteration() + ">=" + delayedBeforeIteration + ", but got " + initialValue
                    .causesOfDelay() + " for variable " + d.variableName());
            assertEquals(value, initialValue.toString());
        }
    }

    public void assertLinked(StatementAnalyserVariableVisitor.Data d, int delayedBeforeIteration, String causesOfDelay, String value) {
        LinkedVariables linkedVariables = d.variableInfo().getLinkedVariables();
        if (d.iteration() < delayedBeforeIteration) {
            assertTrue(linkedVariables.isDelayed(), "Expected linked variables to be delayed in iteration "
                    + d.iteration() + "<" + delayedBeforeIteration + " for variable " + d.variableName());
            assertEquals(causesOfDelay, linkedVariables.causesOfDelay().toString());
        } else {
            assertTrue(linkedVariables.isDone(), "Expected linked variables to be done in iteration "
                    + d.iteration() + "<" + delayedBeforeIteration + " for variable " + d.variableName() + ", got delays "
                    + linkedVariables.causesOfDelay());
            assertEquals(value, linkedVariables.toString());
        }
    }

    public void assertLinked(FieldAnalyserVisitor.Data d, int delayedBeforeIteration, String causesOfDelay, String value) {
        LinkedVariables linkedVariables = d.fieldAnalysis().getLinkedVariables();
        if (d.iteration() < delayedBeforeIteration) {
            assertTrue(linkedVariables.isDelayed(), "Expected linked variables to be delayed in iteration "
                    + d.iteration() + "<" + delayedBeforeIteration + " for field " + d.fieldInfo().name);
            assertEquals(causesOfDelay, linkedVariables.causesOfDelay().toString());
        } else {
            assertTrue(linkedVariables.isDone(), "Expected linked variables to be done in iteration "
                    + d.iteration() + "<" + delayedBeforeIteration + " for field " + d.fieldInfo().name + ", got delays "
                    + linkedVariables.causesOfDelay());
            assertEquals(value, linkedVariables.toString());
        }
    }

    protected final Map<String, Integer> mustSee = new HashMap<>();

    public void mustSeeIteration(CommonVisitorData cvd, int targetIteration) {
        String label = cvd.label();
        if (cvd.iteration() < targetIteration) {
            mustSee.put(label, cvd.iteration());
        } else {
            mustSee.remove(label);
        }
    }

}
