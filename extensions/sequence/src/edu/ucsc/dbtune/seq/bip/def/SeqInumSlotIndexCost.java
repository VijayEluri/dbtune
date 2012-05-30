package edu.ucsc.dbtune.seq.bip.def;

import java.io.Serializable;

import edu.ucsc.dbtune.seq.bip.SeqInumCost;
import edu.ucsc.dbtune.seq.utils.Rx;

public class SeqInumSlotIndexCost implements Serializable {
    public SeqInumIndex index;
    public double cost;
    public double updateCost;

    public SeqInumSlotIndexCost() {
    }

    public void save(Rx rx) {
        rx.setAttribute("indexId", index.id);
        rx.setAttribute("indexName", index.name);
        rx.setAttribute("cost", cost);
        rx.setAttribute("updateCost", updateCost);
    }

    public SeqInumSlotIndexCost(SeqInumCost costModel, Rx rx) {
        String indexName = rx.getAttribute("indexName");
        index = costModel.indexHash.get(indexName);
        if (index == null)
            throw new Error();
        cost = rx.getDoubleAttribute("cost");
        updateCost = rx.getDoubleAttribute("updateCost");
    }
}