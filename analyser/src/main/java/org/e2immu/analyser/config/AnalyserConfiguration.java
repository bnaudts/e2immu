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

package org.e2immu.analyser.config;

import org.e2immu.annotation.Container;

import static org.e2immu.analyser.config.Configuration.NL_TAB;

public record AnalyserConfiguration(boolean computeContextPropertiesOverAllMethods,
                                    boolean computeFieldAnalyserAcrossAllMethods,
                                    boolean forceExtraDelayForTesting,
                                    boolean forceAlphabeticAnalysisInPrimaryType,
                                    boolean normalizeMore,
                                    int graphCacheSize) {

    public static final int DEFAULT_GRAPH_CACHE_SIZE = 1000;

    @Container(builds = AnalyserConfiguration.class)
    public static class Builder {

        // see @NotNull in FieldAnalyser for an explanation
        private boolean computeContextPropertiesOverAllMethods;
        private boolean computeFieldAnalyserAcrossAllMethods;
        private boolean forceExtraDelayForTesting;
        private boolean forceAlphabeticAnalysisInPrimaryType;
        private boolean normalizeMore;
        private int graphCacheSize = DEFAULT_GRAPH_CACHE_SIZE;

        public Builder setNormalizeMore(boolean normalizeMore) {
            this.normalizeMore = normalizeMore;
            return this;
        }

        public Builder setForceAlphabeticAnalysisInPrimaryType(boolean forceAlphabeticAnalysisInPrimaryType) {
            this.forceAlphabeticAnalysisInPrimaryType = forceAlphabeticAnalysisInPrimaryType;
            return this;
        }

        public Builder setForceExtraDelayForTesting(boolean forceExtraDelayForTesting) {
            this.forceExtraDelayForTesting = forceExtraDelayForTesting;
            return this;
        }

        public Builder setComputeContextPropertiesOverAllMethods(boolean computeContextPropertiesOverAllMethods) {
            this.computeContextPropertiesOverAllMethods = computeContextPropertiesOverAllMethods;
            return this;
        }

        public Builder setComputeFieldAnalyserAcrossAllMethods(boolean computeFieldAnalyserAcrossAllMethods) {
            this.computeFieldAnalyserAcrossAllMethods = computeFieldAnalyserAcrossAllMethods;
            return this;
        }

        public Builder setGraphCacheSize(int graphCacheSize) {
            this.graphCacheSize = graphCacheSize;
            return this;
        }

        public AnalyserConfiguration build() {
            return new AnalyserConfiguration(
                    computeContextPropertiesOverAllMethods,
                    computeFieldAnalyserAcrossAllMethods,
                    forceExtraDelayForTesting,
                    forceAlphabeticAnalysisInPrimaryType,
                    normalizeMore,
                    graphCacheSize);
        }
    }

    @Override
    public String toString() {
        return "AnalyserConfiguration:" +
                NL_TAB + "computeContextPropertiesOverAllMethods=" + computeContextPropertiesOverAllMethods +
                NL_TAB + "computeFieldAnalyserAcrossAllMethods=" + computeFieldAnalyserAcrossAllMethods +
                NL_TAB + "forceAlphabeticAnalysisInPrimaryType=" + forceAlphabeticAnalysisInPrimaryType +
                NL_TAB + "forceExtraDelayForTesting=" + forceExtraDelayForTesting +
                NL_TAB + "normalizeMore=" + normalizeMore +
                NL_TAB + "graphCacheSize=" + graphCacheSize;
    }
}
