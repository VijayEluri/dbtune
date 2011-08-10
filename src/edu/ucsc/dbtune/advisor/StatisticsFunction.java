/*
 * ****************************************************************************
 *   Copyright 2010 University of California Santa Cruz                       *
 *                                                                            *
 *   Licensed under the Apache License, Version 2.0 (the "License");          *
 *   you may not use this file except in compliance with the License.         *
 *   You may obtain a copy of the License at                                  *
 *                                                                            *
 *       http://www.apache.org/licenses/LICENSE-2.0                           *
 *                                                                            *
 *   Unless required by applicable law or agreed to in writing, software      *
 *   distributed under the License is distributed on an "AS IS" BASIS,        *
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. *
 *   See the License for the specific language governing permissions and      *
 *   limitations under the License.                                           *
 *  ****************************************************************************
 */
package edu.ucsc.dbtune.advisor;

import edu.ucsc.dbtune.metadata.Index;
import edu.ucsc.dbtune.util.IndexBitSet;

/**
 * @author huascar.sanchez@gmail.com (Huascar A. Sanchez)
 */
public interface StatisticsFunction {
    /**
     * adds a {@link ProfiledQuery} given a {@link DynamicIndexSet set} of
     * materialized {@link Index indexes}.
     * @param queryInfo
     *    a {@link ProfiledQuery profiled query}.
     * @param matSet
     *    a {@link DynamicIndexSet set} of materialized indexes.
     */
    void addQuery(ProfiledQuery queryInfo, DynamicIndexSet matSet);

    /**
     * applies the {@link DoiFunction} to two indexes.
     * @param a
     *      first index object.
     * @param b
     *      second index object.
     * @return
     *      the result of the {@code doi}'s execution.
     */
    double doi(Index a, Index b);

    /**
     * Applies the function to an index object of type {@code I} and to an index configuration,
     * resulting in an object of type {@code double}, which an the benefit value of the index
     * object given an index configuration.
     *
     * @param a the index object.
     * @param m the index configuration.
     * @return the benefit value of the index object given an index configuration.
     */
    double benefit(Index a, IndexBitSet m);
}
