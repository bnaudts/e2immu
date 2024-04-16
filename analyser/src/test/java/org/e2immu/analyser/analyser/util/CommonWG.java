package org.e2immu.analyser.analyser.util;

import org.e2immu.analyser.analyser.CauseOfDelay;
import org.e2immu.analyser.analyser.DV;
import org.e2immu.analyser.analyser.HiddenContentSelector;
import org.e2immu.analyser.analyser.LV;
import org.e2immu.analyser.analyser.delay.DelayFactory;
import org.e2immu.analyser.analyser.delay.SimpleCause;
import org.e2immu.analyser.model.LocalVariable;
import org.e2immu.analyser.model.Location;
import org.e2immu.analyser.model.ParameterizedType;
import org.e2immu.analyser.model.TypeInfo;
import org.e2immu.analyser.model.variable.LocalVariableReference;
import org.e2immu.analyser.model.variable.Variable;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.e2immu.analyser.analyser.LV.*;
import static org.e2immu.analyser.analyser.LinkedVariables.*;

public class CommonWG {
    final LV v0 = LINK_STATICALLY_ASSIGNED;
    final LV v2 = LINK_DEPENDENT;
    final LV v4 = LV.createHC(new Links(0, 0));
    final LV delay = LV.delay(DelayFactory.createDelay(new SimpleCause(Location.NOT_YET_SET, CauseOfDelay.Cause.ECI)));
    final LV.Indices i0 = new LV.Indices(Set.of(new LV.Index(List.of(0))));
    final LV.Indices i1 = new LV.Indices(Set.of(new LV.Index(List.of(0))));

    protected static Variable makeVariable(String name) {
        TypeInfo t = new TypeInfo("a.b.c", "T");
        return new LocalVariableReference(new LocalVariable(name, new ParameterizedType(t, 0)));
    }
}
