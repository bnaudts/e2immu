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

package org.e2immu.analyser.bytecode;

import org.e2immu.analyser.config.Configuration;
import org.e2immu.analyser.config.InputConfiguration;
import org.e2immu.analyser.inspector.InspectionState;
import org.e2immu.analyser.inspector.TypeContext;
import org.e2immu.analyser.model.TypeInfo;
import org.e2immu.analyser.model.TypeInspection;
import org.e2immu.analyser.parser.Parser;
import org.e2immu.analyser.parser.TypeMap;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;

public class TestPreloadJavaBase {

    @Test
    public void testPreload() throws IOException {
        InputConfiguration inputConfiguration = new InputConfiguration.Builder()
                .addClassPath(InputConfiguration.DEFAULT_CLASSPATH)
                .build();
        Configuration configuration = new Configuration.Builder()
                .setInputConfiguration(inputConfiguration)
                .build();
        Parser parser = new Parser(configuration);
        TypeContext typeContext = parser.getTypeContext();

        // NOTE: this may be very dependent on the current JDK and pre-loading settings.

        // interestingly, java.util.List has been referred to, but it has not been loaded
        // because it has not yet appeared in a type hierarchy (but it has appeared as a field type
        // in some private field of java.lang.Throwable)
        TypeInfo list = typeContext.typeMap().get("java.util.List");
        assertNotNull(list);
        TypeInfo classLoader = typeContext.typeMap().get("java.lang.ClassLoader");
        assertNotNull(classLoader);
        TypeMap.Builder typeMapBuilder = typeContext.typeMapBuilder();
        assertEquals(InspectionState.FINISHED_BYTECODE, typeMapBuilder.getInspectionState(classLoader));

        TypeInfo list2 = typeMapBuilder.getOrCreate("java.util.List", true);
        assertNotNull(list2);
        assertEquals(InspectionState.TRIGGER_BYTECODE_INSPECTION, typeMapBuilder.getInspectionState(list2));
        // the next call will trigger the byte code inspection of "list":
        TypeInspection listInspection = typeContext.getTypeInspection(list2);
        assertNotNull(listInspection);
        assertEquals(InspectionState.FINISHED_BYTECODE, typeMapBuilder.getInspectionState(list2));
    }
}
