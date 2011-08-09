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

import edu.ucsc.dbtune.core.metadata.Index;
import edu.ucsc.dbtune.util.IndexBitSet;
import edu.ucsc.dbtune.util.ToStringBuilder;

public class AnalyzedQuery {
    private final ProfiledQuery profileInfo;
    private final IndexBitSet[]    partition;

    /**
     * construct a query which has been analyzed by some {@code tuning interface}.
     * @param orig
     *      original query before it got analyzed.
     * @param partition
     *      an array of index partitions.
     */
    public AnalyzedQuery(ProfiledQuery orig, IndexBitSet[] partition) {
        this.profileInfo    = orig;
        this.partition      = partition;
    }

    /**
     * @return original query before it got analyzed.
     */
    public ProfiledQuery getProfileInfo() {
        return profileInfo;
    }

    /**
     * @return an array of index partitions.
     */
    public IndexBitSet[] getPartition() {
        return partition;
    }

    @Override
    public String toString() {
        return new ToStringBuilder<AnalyzedQuery>(this)
               .add("original", profileInfo)
               .add("partition", partition)
               .toString();
    }
}
