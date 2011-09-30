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

package edu.ucsc.dbtune.advisor.interactions;

import edu.ucsc.dbtune.util.IndexBitSet;
import edu.ucsc.dbtune.util.ToStringBuilder;
import edu.ucsc.dbtune.util.UnionFind;

import java.io.Serializable;

public class InteractionBank implements Serializable {
    private static final long serialVersionUID = 1L;
    
    public  final int           indexCount;
    private final double[]      bestBenefit;
    private final double[][]    lowerBounds;

    /**
     * construct an {@link InteractionBank} given a particular set of
     * candidate indexes.
     * 
     * @param maxId
     *      the maximum id that an index can have.
     */
    public InteractionBank(int maxId) {
        indexCount = maxId + 1;
        bestBenefit = new double[indexCount];
        lowerBounds = new double[indexCount][];
        for (int i = 0; i < indexCount; i++)
            lowerBounds[i] = new double[i];
        if(maxId > 10240) {
            throw new RuntimeException("Can't be larger than 10240");
        }
    }
    
    /**
     * Assign interaction with an exact value
     * @param id1
     *      identifier of first index.
     * @param id2
     *      identifier of a second index.
     * @param newValue
     *      new value of interaction.
     */
    public final void assignInteraction(int id1, int id2, double newValue) {
        assert (newValue >= 0);
        assert (id1 != id2);

        if (id1 < id2) {
            int t   = id1;
            id1     = id2;
            id2     = t;
        }
        
        lowerBounds[id1][id2] = Math.max(newValue, lowerBounds[id1][id2]);
    }

    /**
     * assign benefit to a particular index
     * @param id
     *      index's identifier.
     * @param newValue
     *      benefit value.
     */
    public void assignBenefit(int id, double newValue) {
        bestBenefit[id] = Math.max(newValue, bestBenefit[id]);
    }
    
    /**
     * Returns the best benefit value of a particular index matching an id.
     * @param id
     *      index's id.
     * @return the best benefit of an index
     */
    public final double bestBenefit(int id) {
        return bestBenefit[id];
    }
    
    /**
     * calculates the interaction level between two indexes.
     * @param id1 id of first index
     * @param id2 id of second index
     * @return an interaction value given the ids of two indexes.
     */
    public final double interactionLevel(int id1, int id2) {
        assert (id1 != id2);        
        if (id1 < id2){
            return lowerBounds[id2][id1];

        } else {
            return lowerBounds[id1][id2];
        }
    }

    /**
     * performs a stable partitioning of the interaction bank.
     * @param threshold
     *      partition threshold 
     * @return an array of bit sets.
     */
    public final IndexBitSet[] stablePartitioning(double threshold) {
        UnionFind uf = new UnionFind(indexCount);
        for (int a = 0; a < indexCount; a++) { 
            for (int b = 0; b < a; b++) { 
                if (lowerBounds[a][b] > threshold) {
                    uf.union(a,b);
                } 
            } 
        }
        return uf.sets();
    }

    @Override
    public String toString() {
        return new ToStringBuilder<InteractionBank>(this)
               .add("indexCount", indexCount)
               .add("bestBenefit", bestBenefit)
               .add("lowerBounds", lowerBounds)
               .toString();
    }
}