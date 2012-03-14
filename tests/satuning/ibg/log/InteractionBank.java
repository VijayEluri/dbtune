package satuning.ibg.log;

import java.io.Serializable;

import satuning.engine.CandidatePool.Snapshot;
import satuning.util.BitSet;
import satuning.util.UnionFind;

public class InteractionBank implements Serializable {
	private static final long serialVersionUID = 1L;
	
	public final int indexCount;
	private double[] bestBenefit;
	private double[][] lowerBounds; 
	
	public InteractionBank(Snapshot candidateSet) {
		indexCount = candidateSet.maxInternalId() + 1;
		bestBenefit = new double[indexCount];
		lowerBounds = new double[indexCount][];
		for (int i = 0; i < indexCount; i++)
			lowerBounds[i] = new double[i];
	}
	
	/*
	 * Assign interaction with an exact value
	 */
	public final void assignInteraction(int id1, int id2, double newValue) {
		assert (newValue >= 0);
		assert (id1 != id2);

		if (id1 < id2) {
			int t = id1;
			id1 = id2;
			id2 = t;
		}
		
		lowerBounds[id1][id2] = Math.max(newValue, lowerBounds[id1][id2]);
	}

	public void assignBenefit(int id, double newValue) {
		bestBenefit[id] = Math.max(newValue, bestBenefit[id]);
	}
	
	/*
	 * Get an interaction value
	 */
	public final double interactionLevel(int id1, int id2) {
//		if (id1 >= indexCount || id2 >= indexCount)
//			return 0;
		
		assert (id1 != id2);	
		if (id1 < id2) 
			return lowerBounds[id2][id1];
		else
			return lowerBounds[id1][id2];
	}
	
	/*
	 * Get the best benefit of an index
	 */
	public final double bestBenefit(int id) {
//		if (id > indexCount)
//			return 0;
		return bestBenefit[id];
	}

	public final BitSet[] stablePartitioning(double threshold) {
		UnionFind uf = new UnionFind(indexCount);
		for (int a = 0; a < indexCount; a++) 
			for (int b = 0; b < a; b++) 
				if (lowerBounds[a][b] >= threshold)
					uf.union(a,b);
		return uf.sets();
	}

    // DEAD CODE
	
//	public final void clear() {
//		for (double[] a : lowerBounds) 
//			java.util.Arrays.fill(a, 0.0);
//	}
	
//	public final void print() {
//		/* print whole contents */
//		System.out.println("--- interactions");
//		for (int i = 0; i < indexCount; i++) {
//			for (int j = 0; j < i; j++) {
//				if (lowerBounds[i][j] > 0.0)
//					System.out.printf("%d\t%d\t%8f\n", i, j, lowerBounds[i][j]);
//			}
//		}
//		System.out.println("---");
//	}
}