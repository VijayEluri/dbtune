package edu.ucsc.dbtune.deployAware;

import java.util.Vector;

import edu.ucsc.dbtune.bip.core.IndexTuningOutput;
import edu.ucsc.dbtune.seq.bip.def.SeqInumIndex;

public class DATOutput extends IndexTuningOutput {
    public static class W {
        public boolean[] indexUsed;
        public double cost;
        public double createCost;
        public int create=0,drop=0,present=0;
    }

    public W[] ws;
    public double totalCost;
    public double last() {
        return ws[ws.length-1].cost;
    }

    public DATOutput(int size) {
        ws = new W[size];
        for (int i = 0; i < size; i++)
            ws[i] = new W();
    }

    public void print() {
        for (int i = 0; i < ws.length; i++) {
            System.out.print("Window " + i + ":");
            for (int j = 0; j < ws[i].indexUsed.length; j++) {
                if (ws[i].indexUsed[j])
                    System.out.print(" " + j);
            }
            System.out.println();
        }
    }
}
