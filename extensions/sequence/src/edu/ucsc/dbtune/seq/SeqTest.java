package edu.ucsc.dbtune.seq;

import java.io.File;
import java.io.IOException;

import edu.ucsc.dbtune.DatabaseSystem;
import edu.ucsc.dbtune.metadata.Catalog;
import edu.ucsc.dbtune.metadata.Column;
import edu.ucsc.dbtune.metadata.Index;
import edu.ucsc.dbtune.metadata.Schema;
import edu.ucsc.dbtune.metadata.Table;
import edu.ucsc.dbtune.optimizer.Optimizer;
import edu.ucsc.dbtune.seq.def.*;
import edu.ucsc.dbtune.seq.utils.Rt;
import edu.ucsc.dbtune.util.Environment;

public class SeqTest {
	SeqCost cost;
	SeqConfiguration[] allConfigurations;

	public SeqTest() throws Exception {
		// from file test
		if (false) {
			cost = SeqCost.fromFile(Rt.readResourceAsString(SeqCost.class,
					"disjoint.txt"));
			SeqSplit split = new SeqSplit(cost, cost.sequence);
			for (SeqSplitGroup g : split.groups) {
				SeqConfiguration[] confs = cost.getAllConfigurations(g.indices);
				SeqStep[] steps = SeqOptimal.getOptimalSteps(cost.source,
						cost.destination, g.queries, confs);
				SeqOptimal optimal = new SeqOptimal(cost, cost.source,
						cost.destination, g.queries, steps);
				g.bestPath = optimal.getBestSteps();
				Rt.np(SeqOptimal.formatBestPathPlain(g.bestPath));
			}
			SeqMerge merge = new SeqMerge(cost, split.groups);
		}
		Environment en = Environment.getInstance();
		en.setProperty("optimizer", "dbms");
		DatabaseSystem db = DatabaseSystem.newDatabaseSystem(en);
		Optimizer optimizer = db.getOptimizer();
		String workloadFile = en
				.getScriptAtWorkloadsFolder("tpch/workload_seq.sql");
		String text = Rt.readFile(new File(workloadFile));
		text = text.replaceAll("--.*\n", "");
		Index[] indices = { new Index(new Column(new Table(new Schema(
				new Catalog(""), "tpch"), "lineitem"), "l_orderkey"), true), };
		cost = SeqCost.fromOptimizer(optimizer, text.split(";\r?\n?"), indices);
		SeqStep[] steps = SeqOptimal.getOptimalSteps(cost.source,
				cost.destination, cost.sequence, cost
						.getAllConfigurations(cost.indicesV));
		SeqOptimal optimal = new SeqOptimal(cost, cost.source,
				cost.destination, cost.sequence, steps);
		for (SeqStepConf step : optimal.getBestSteps()) {
			if (step.step.query != null)
				Rt.np(step.step.query.name + ": " + step.step.query.sql.trim());
			Rt.np("\t"+step.configuration);
			Rt.np("\t"+step.costUtilThisStep);
		}
	}

	public static void main(String[] args) throws Exception {
		new SeqTest();
	}
}
