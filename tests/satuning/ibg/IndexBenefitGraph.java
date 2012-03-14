package satuning.ibg;

import java.io.Serializable;

import satuning.util.BitSet;

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
	
	/*
	 * Creates an IBG which is in a state ready for building.
	 * Specifically, the rootNode is physically constructed, but it is not
	 * expanded, so its cost and used set have not been determined.
	 * 
	 * In the initial state, the cost of the workload under the empty configuration
	 * is set, and may be accessed through emptyCost()
	 * 
	 * Nodes are built by calling buildNode() until it returns false.
	 */
	public IndexBenefitGraph(IBGNode rootNode0, double emptyCost0, BitSet isUsed0) {
		rootNode = rootNode0;
		emptyCost = emptyCost0;
		isUsed = isUsed0;
	}

	public final double emptyCost() {
		return emptyCost;
	}
	
	public final IBGNode rootNode() {
		return rootNode;
	}

	public final boolean isUsed(int i) {
		return isUsed.get(i);
	}
	
	/*
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
		
		IBGNode(BitSet config0, int id0) {
			config = config0;
			id = id0;
			cost = -1.0;
			firstChild = null;
		}
		
		/*
		 * Check if it has children/cost yet
		 */
		protected final boolean isExpanded() { return cost >= 0; }
		
		/*
		 * Set the cost and list of children (one for each used index).
		 */
		protected final void expand(double cost0, IBGChild firstChild0) {
			assert(!isExpanded());
			assert(cost0 >= 0);
			
			// volatile assignments must be ordered with "state" assigned last
			cost = cost0;
			firstChild = firstChild0;
		}
		
		/*
		 * Get the cost
		 */
		public final double cost() {
			assert(isExpanded());
			return cost;
		}
		
		/*
		 * Get the head of the child list
		 */
		protected final IBGChild firstChild() {
			assert(isExpanded());
			return firstChild; 
		}

		/*
		 * Add each of the used indexes in this node to the given BitSet
		 */
		public final void addUsedIndexes(BitSet bs) {
			assert(isExpanded());
			for (IBGChild ch = firstChild; ch != null; ch = ch.next)
				bs.set(ch.usedIndex);
		}
		
		/*
		 * Remove each of the used indexes in this node from the given BitSet
		 */
		public void clearUsedIndexes(BitSet bs) {
			assert(isExpanded());
			for (IBGChild ch = firstChild; ch != null; ch = ch.next)
				bs.clear(ch.usedIndex);
		}
		
		/*
		 * return true if each of the used indexes are in the given BitSet
		 */
		public boolean usedSetIsSubsetOf(BitSet other) {
			assert(isExpanded());
			for (IBGChild ch = firstChild; ch != null; ch = ch.next)
				if (!other.get(ch.usedIndex))
					return false;
			return true;
		}
		
		/*
		 * return true if the i is in the used set
		 */
		public boolean usedSetContains(int id) {
			assert(isExpanded());
			for (IBGChild ch = firstChild; ch != null; ch = ch.next)
				if (id == ch.usedIndex)
					return true;
			return false;
		}

		public void setCost(double cost0) {
			cost = cost0;
		}
	}
	
	protected static class IBGChild implements Serializable {
		private static final long serialVersionUID = 1L;
		
		final int usedIndex; // the internalID of the used index on this edge
		final IBGNode node; // the actual child node
		IBGChild next = null;
		
		// next pointer is initially null
		IBGChild(IBGNode node0, int usedIndex0) {
			node = node0;
			usedIndex = usedIndex0;
		}
	}

	// only used by MonotonicEnforcer
	public void setEmptyCost(double cost) {
		emptyCost = cost;
	}
}

