/*
 * e2immu: a static code analyser for effective and eventual immutability
 * Copyright 2020-2021, Bart Naudts, https://www.e2immu.org
 *
 * This program is free software: you can redistribute it and/or modify it under the
 * terms of the GNU Lesser General License as published by the Free Software
 * Foundation, either version 3 of the License, or (at your option) any later version.
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE.  See the GNU Lesser General License for
 * more details. You should have received a copy of the GNU Lesser General Public
 * License along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.e2immu.annotatedapi;

import org.e2immu.annotation.*;

public class JavaIo {
    final static String PACKAGE_NAME = "java.io";


    @ERContainer
    interface Serializable$ {

    }

    // throws IOException rather than Exception (in AutoCloseable)
    @Independent
    @Container
    interface Closeable$ {

        @Modified
        void close();
    }


    @Independent
    @Container
    interface PrintStream$ {
        @Modified
        @AllowsInterrupt
        void print(char c);

        @Modified
        @AllowsInterrupt
        void print(boolean b);

        @Modified
        @AllowsInterrupt
        void print(int i);

        @Modified
        @AllowsInterrupt
        void print(float f);

        @Modified
        @AllowsInterrupt
        void print(long l);

        @Modified
        @AllowsInterrupt
        void print(String s);

        @Modified
        @AllowsInterrupt
        void print(Object obj);

        @Modified
        @AllowsInterrupt
        void println();

        @Modified
        @AllowsInterrupt
        void println(char c);

        @Modified
        @AllowsInterrupt
        void println(boolean b);

        @Modified
        @AllowsInterrupt
        void println(int i);

        @Modified
        @AllowsInterrupt
        void println(float f);

        @Modified
        @AllowsInterrupt
        void println(long l);

        @Modified
        @AllowsInterrupt
        void println(String s);

        @Modified
        @AllowsInterrupt
        void println(Object obj);
    }

    @Independent
    @Container
    interface OutputStream$ {

        @Modified
        void write(@Independent @NotNull byte[] b);

        @Modified
        void write(@Independent @NotNull byte[] b, int off, int len);

        @Modified
        void flush();

        @Modified
        void write(int b);
    }

    @Independent
    @Container
    interface FilterOutputStream$ {

    }

    @Independent
    @Container
    interface Writer$ {
        @Modified
        void write(@Independent char[] cbuf);

        @Modified
        void write(@Independent char[] cbuf, int off, int len);
    }
}
