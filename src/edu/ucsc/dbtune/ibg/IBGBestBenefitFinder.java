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

package edu.ucsc.dbtune.ibg;

import edu.ucsc.dbtune.ibg.IndexBenefitGraph.IBGNode;
import edu.ucsc.dbtune.util.DefaultBitSet;
import edu.ucsc.dbtune.util.ToStringBuilder;

public class IBGBestBenefitFinder {
	private final DefaultBitSet visited;
	private final DefaultBitSet bitset_Ya;
	private final IBGNodeQueue          pending;
	private final IBGCoveringNodeFinder finder;

    public IBGBestBenefitFinder(){
        this(new DefaultBitSet(), new DefaultBitSet(), new IBGNodeQueue(), new IBGCoveringNodeFinder());
    }

    IBGBestBenefitFinder(DefaultBitSet visited, DefaultBitSet bitset_Ya, IBGNodeQueue pendingQueue, IBGCoveringNodeFinder finder){
        this.visited = visited;
        this.bitset_Ya = bitset_Ya;
        this.pending   = pendingQueue;
        this.finder = finder;
    }

    /**
     * calculates the best benefit for a given index in the index benefit graph.
     * @param ibg
     *      the {@link IndexBenefitGraph}.
     * @param indexId
     *      an {@link edu.ucsc.dbtune.core.DBIndex}'s unique identifier.
     * @param M
     *      an index configuration M.
     * @return
     *     the best benefit for a given index in the index benefit graph.
     */
	public double bestBenefit(IndexBenefitGraph ibg, int indexId, DefaultBitSet M) {
		visited.clear();
		pending.reset();
		
		double bestValue = 0;
		
		pending.addNode(ibg.rootNode());
		while (pending.hasNext()) {
			IBGNode Y = pending.next();

			if (visited.get(Y.id)) 
				continue;
			visited.set(Y.id);

			if (!Y.config.get(indexId) && M.subsetOf(Y.config)) {
				bitset_Ya.set(Y.config);
				bitset_Ya.set(indexId);
				IBGNode Ya = finder.findFast(ibg.rootNode(), bitset_Ya, null);
				double value = Y.cost() - Ya.cost();
				bestValue = Math.max(value, bestValue);
			}
			pending.addChildren(Y.firstChild());
		}
		
		return bestValue;
	}

    @Override
    public String toString() {
        return new ToStringBuilder<IBGBestBenefitFinder>(this)
               .add("visited",visited)
               .add("bitset_Ya",bitset_Ya)
               .add("pending",pending)
               .add("finder",finder)
               .toString();
    }
}
