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

package org.e2immu.analyser.model.statement;

import org.e2immu.analyser.model.Comment;
import org.e2immu.analyser.model.Identifier;
import org.e2immu.analyser.model.Statement;
import org.e2immu.analyser.model.impl.ElementImpl;
import org.e2immu.analyser.output.OutputBuilder;
import org.e2immu.analyser.output.Space;
import org.e2immu.analyser.output.Symbol;
import org.e2immu.analyser.output.Text;

import java.util.stream.Stream;

public abstract class StatementWithStructure extends ElementImpl implements Statement {
    public final Structure structure;
    public final String label;
    public static final Structure EMPTY_CODE_ORGANIZATION = new Structure.Builder().build();

    public StatementWithStructure(Identifier identifier, String label, Comment comment) {
        super(identifier);
        structure = comment == null ? EMPTY_CODE_ORGANIZATION : new Structure.Builder().setComment(comment).build();
        this.label = label;
    }

    public StatementWithStructure(Identifier identifier, String label, Structure structure) {
        super(identifier);
        this.structure = structure;
        this.label = label;
    }

    @Override
    public Structure getStructure() {
        return structure;
    }

    @Override
    public String label() {
        return label;
    }

    protected OutputBuilder outputBuilderWithLabel() {
        OutputBuilder ob = new OutputBuilder();
        if (label != null) {
            ob.add(Space.ONE).add(new Text(label)).add(Symbol.COLON_LABEL).add(Space.ONE_IS_NICE_EASY_SPLIT);
        }
        return ob;
    }

    @Override
    public Stream<Block> subBlockStream() {
        return structure.subBlockStream();
    }
}
