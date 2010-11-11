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

import edu.ucsc.dbtune.util.BitSet;
import edu.ucsc.dbtune.util.ToStringBuilder;

import java.io.Serializable;

public class IndexBenefitGraph implements Serializable {
	private static final long serialVersionUID = 1L;
	
	/*
	 * The primary information stored by the graph
	 * 
	 * Every node in the graph is a descendant of rootNode. We also keep the
	 * cost of the workload under the empty configuration, stored in emptyCost.
	 */
	private final IBGNode rootNode;
	private double emptyCost;
	
	/* true if the index is used somewhere in the graph */
	private final BitSet isUsed;
	
	/**
	 * Creates an IBG which is in a state ready for building.
	 * Specifically, the rootNode is physically constructed, but it is not
	 * expanded, so its cost and used set have not been determined.
	 * 
	 * In the initial state, the cost of the workload under the empty configuration
	 * is set, and may be accessed through emptyCost()
	 * 
	 * Nodes are built by calling buildNode() until it returns false.
     * @param rootNode
     *      the root of this graph.
     * @param emptyCost
     *      initial cost of the workload under the empty configuration.
     * @param isUsed
     *      a flag that indicates if the index is used somewhere in the graph.
     */
	public IndexBenefitGraph(IBGNode rootNode, double emptyCost, BitSet isUsed) {
		this.rootNode   = rootNode;
		this.emptyCost  = emptyCost;
		this.isUsed     = isUsed;
	}

    /**
     * @return initial cost of the workload under the empty configuration.
     */
	public final double emptyCost() {
		return emptyCost;
	}

    /**
     * indicate if an index is used at position i.
      * @param i
     *      position of an {@link IBGNode}.
     * @return
     *      {@code true} if the index is used somewhere in the graph.
     */
	public final boolean isUsed(int i) {
		return isUsed.get(i);
	}

    /**
     * @return an {@link IBGNode} instance.
     */
	public final IBGNode rootNode() {
		return rootNode;
	}

	// only used by MonotonicEnforcer
	public void setEmptyCost(double cost) {
		emptyCost = cost;
	}

    @Override
    public String toString() {
        return new ToStringBuilder<IndexBenefitGraph>(this)
               .add("rootNode", rootNode())
               .add("emptycost", emptyCost())
               .add("isUsed bitset", isUsed)
               .toString();
    }

    /**
	 * A node of the IBG
	 */
	public static class IBGNode  implements Serializable {
		private static final long serialVersionUID = 1L;

		/* Configuration that this node is about */
		public final BitSet config;
		
		/* id for the node that is unique within the enclosing IBG */
		public final int id;
		
		/* 
		 * cost with the given configuration 
		 * don't access until isExpanded() returns true
		 * 
		 * internally, this is used to determine if the node 
		 * is expanded... it is set to -1.0 until expanded
		 */
		private volatile double cost;

		/*
		 * Linked list of children
		 * don't access until isExpanded() returns true
		 */
		private volatile IBGChild firstChild;
		
		IBGNode(BitSet config, int id) {
			this.config = config;
			this.id = id;
			this.cost = -1.0;
			this.firstChild = null;
		}
		
		/**
		 * Check if it has children/cost yet
         * @return {@code true} if the cost is greater or equal than zero.
         */
		protected final boolean isExpanded() { return cost >= 0; }
		
		/**
		 * Set the cost and list of children (one for each used index).
         * @param cost
         *      cost of child node.
         * @param firstChild
         *      first child node.
         */
		protected final void expand(double cost, IBGChild firstChild) {
			assert(!isExpanded());
			assert(cost >= 0);

			// volatile assignments must be ordered with "state" assigned last
			this.cost = cost;
			this.firstChild = firstChild;
		}
		
		/**
		 * @return the cost of index node.
		 */
		public final double cost() {
			assert(isExpanded());
			return cost;
		}
		
		/**
		 * @return the head of the child list
		 */
		protected final IBGChild firstChild() {
			assert(isExpanded());
			return firstChild; 
		}

		/**
		 * Add each of the used indexes in this node to the given BitSet
         * @param bs
         *      nex used indexes set.
         */
		public final void addUsedIndexes(BitSet bs) {
			assert(isExpanded());
			for (IBGChild ch = firstChild; ch != null; ch = ch.next){
                bs.set(ch.usedIndex);
            }
		}
		
		/**
		 * Remove each of the used indexes in this node from the given BitSet
         * @param bs
         *     nodes to be removed in the used indexes set.
         */
		public void clearUsedIndexes(BitSet bs) {
			assert(isExpanded());
			for (IBGChild ch = firstChild; ch != null; ch = ch.next){
                bs.clear(ch.usedIndex);
            }
		}
		
		/**
         * determine if the used set is a subject of another subset.
		 * @param other
         *      the other subset which will be checked if a subset of
         *      the used set of indexes.
         * @return {@code true} if each of the used indexes are in the given BitSet
		 */
		public boolean usedSetIsSubsetOf(BitSet other) {
			assert(isExpanded());
			for (IBGChild ch = firstChild; ch != null; ch = ch.next) {
                if (!other.get(ch.usedIndex)){
                    return false;
                }

            }
			return true;
		}
		
		/**
		 * @return {@code true} if the i is in the used set
		 */
		public boolean usedSetContains(int id) {
			assert(isExpanded());
			for (IBGChild ch = firstChild; ch != null; ch = ch.next){
                if (id == ch.usedIndex){
                    return true;
                }
            }
			return false;
		}

        /**
         * sets the cost of workload.
         * @param cost
         *      cost of the workload
         */
		public void setCost(double cost) {
			this.cost = cost;
		}

        @Override
        public String toString() {
            return new ToStringBuilder<IBGNode>(this)
                   .add("node's id", id)
                   .add("index configuration", config)
                   .add("cost", cost)
                   .add("first child", firstChild)
                   .toString();
        }
    }

    /**
     *  Represents a child node in the Index Benefit Graph.
     */
	protected static class IBGChild implements Serializable {
		private static final long serialVersionUID = 1L;
		
		final int usedIndex; // the internalID of the used index on this edge
		final IBGNode node;  // the actual child node
		IBGChild next = null;
		
		// next pointer is initially null
		IBGChild(IBGNode node, int usedIndex) {
			this.node       = node;
			this.usedIndex  = usedIndex;
		}

        @Override
        public String toString() {
            return new ToStringBuilder<IBGChild>(this)
                   .add("usedIndex", usedIndex)
                   .add("actual child node", node)
                   .add("next node", next)
                   .toString();
        }
    }
}


