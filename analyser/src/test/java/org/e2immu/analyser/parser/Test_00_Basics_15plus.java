
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

import org.e2immu.analyser.config.DebugConfiguration;
import org.junit.jupiter.api.Test;

import java.io.IOException;

public class Test_00_Basics_15plus extends CommonTestRunner {
    public Test_00_Basics_15plus() {
        super(true);
    }

    @Test
    public void test_15() throws IOException {
        testClass("Basics_15", 0, 0, new DebugConfiguration.Builder()
                .build());
    }

    @Test
    public void test_16() throws IOException {
        testClass("Basics_16", 0, 1, new DebugConfiguration.Builder()
                .build());
    }

    @Test
    public void test_17() throws IOException {
        testClass("Basics_17", 0, 0, new DebugConfiguration.Builder()
                .build());
    }
}
