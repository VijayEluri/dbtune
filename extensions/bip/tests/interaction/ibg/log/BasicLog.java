package interaction.ibg.log;

import interaction.ibg.log.InteractionBank;
import interaction.util.UnionFind;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class BasicLog implements Serializable {
	private static final long serialVersionUID = 8346511456560459427L;
	
	List<Entry> list = new ArrayList<Entry>();
	List<InteractionPair> listInteraction = new ArrayList<InteractionPair>();
	InteractionBank bank;
	
	protected BasicLog(InteractionBank bank0) {
		bank = bank0;
	}
	
	public void add(Entry entry) {
		list.add(entry);
	}
	
	public Iterator<Entry> iterator() {
		return list.iterator();
	}
	
	public void clear() {
		list.clear();
	}

	public InteractionBank getInteractionBank() {
		return bank;
	}
	
	public List<InteractionPair> interactions()
	{
		return listInteraction;
	}
	
	public AnalysisLog getAnalysisLog(double threshold) {
		int indexCount = bank.indexCount;
		listInteraction = new ArrayList<InteractionPair>();
		double trueTotalInteraction = 0;
		for (int a = 0; a < indexCount; a++)
			for (int b = 0; b < a; b++) 
				if (bank.interactionLevel(a, b) > 0) {
					trueTotalInteraction += bank.interactionLevel(a,b);
				}
		UnionFind uf = new UnionFind(indexCount);
		int numInteractingPairs = 0; // count up as we go
		double totalInteractionSoFar = 0;

		AnalysisLog log = new AnalysisLog(bank);
		
		for (Entry e : list) {
			double doi = bank.interactionLevel(e.a, e.b);
			double newRE = 1.0 - e.doiNew/doi;
			double oldRE = 1.0 - e.doiOld/doi;
			assert(0 <= newRE && newRE <= 1.0);
			assert(0 <= oldRE && oldRE <= 1.0);
			assert(oldRE >= newRE);
			totalInteractionSoFar += e.doiNew - e.doiOld;
			
			if (e.doiOld < threshold && e.doiNew >= threshold) {
				++numInteractingPairs;
				uf.union(e.a, e.b);
				
				// add into listInteraction
				if (e.a < e.b)
				    listInteraction.add(new InteractionPair(e.a, e.b));
				else 
				    listInteraction.add(new InteractionPair(e.b, e.a));
			}
			
			log.add(new AnalysisLog.Entry(
						e.a, e.b, e.doiNew,
						e.millis, e.whatifCalls, 
						numInteractingPairs, uf.numSets(), 
						newRE, 1 - totalInteractionSoFar/trueTotalInteraction));
//						newRE, sumRelativeError/pairCount));
		}
		
			
		return log;
	}
	
	static class Entry implements Serializable {
		private static final long serialVersionUID = -6596026594275542583L;
		
		int a;
		int b;
		double doiNew;
		double doiOld;
		long millis;
		int whatifCalls;
		
		public Entry(int a, int b, 
				     double doiNew, double doiOld, 
				     long millis, int whatif) {
			this.a = a;
			this.b = b;
			this.doiNew = doiNew;
			this.doiOld = doiOld;
			this.millis = millis;
			this.whatifCalls = whatif;
		}
	}
	
	public class InteractionPair 
	{	
		int a;
		int b;
		
		public InteractionPair(int a, int b) 
		{
			this.a = a;
			this.b = b;		
		}
		
		@Override
		public String toString()
		{
			return a + "|" + b;
		}
	}
}
