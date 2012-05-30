package edu.ucsc.dbtune.seq.bip.def;

import java.io.Serializable;
import java.util.Vector;

import edu.ucsc.dbtune.seq.bip.SeqInumCost;
import edu.ucsc.dbtune.seq.utils.Rx;

public class SeqInumSlot implements Serializable {
    public SeqInumPlan plan;
    public double fullTableScanCost;
    public Vector<SeqInumSlotIndexCost> costs = new Vector<SeqInumSlotIndexCost>();

    // the following variables are only for debugging purposes
    // and should be removed when the system works correctly
    public SeqInumSlotIndexCost selectedIndex;

    public SeqInumSlot(SeqInumPlan plan) {
        this.plan = plan;
    }

    public void save(Rx rx) {
        rx.setAttribute("fullTableScanCost", fullTableScanCost);
        for (SeqInumSlotIndexCost cost : costs) {
            cost.save(rx.createChild("index"));
        }
    }

    public SeqInumSlot(SeqInumCost cost,SeqInumPlan plan, Rx rx) {
        this.plan = plan;
        fullTableScanCost = rx.getDoubleAttribute("fullTableScanCost");
        for (Rx r : rx.findChilds("index")) {
            costs.add(new SeqInumSlotIndexCost(cost,r));
        }
    }
}