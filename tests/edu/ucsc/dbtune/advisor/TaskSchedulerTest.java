package edu.ucsc.dbtune.advisor;

import edu.ucsc.dbtune.advisor.TaskScheduler.SchedulerTask;
import edu.ucsc.dbtune.connectivity.DatabaseConnection;
import edu.ucsc.dbtune.DBTuneInstances;
import edu.ucsc.dbtune.ibg.CandidatePool;
import edu.ucsc.dbtune.ibg.CandidatePool.Snapshot;
import edu.ucsc.dbtune.ibg.IBGAnalyzer;
import edu.ucsc.dbtune.ibg.IndexBenefitGraph;
import edu.ucsc.dbtune.ibg.IndexBenefitGraphConstructor;
import edu.ucsc.dbtune.ibg.InteractionLogger;
import edu.ucsc.dbtune.ibg.RunnableState;
import edu.ucsc.dbtune.ibg.ThreadIBGAnalysis;
import edu.ucsc.dbtune.ibg.ThreadIBGConstruction;
import edu.ucsc.dbtune.metadata.Index;
import edu.ucsc.dbtune.spi.core.Console;
import org.hamcrest.CoreMatchers;
import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

import java.lang.reflect.Field;
import java.util.concurrent.LinkedBlockingDeque;

import static edu.ucsc.dbtune.DBTuneInstances.generateColumns;
import static edu.ucsc.dbtune.DBTuneInstances.generateDescVals;
import static edu.ucsc.dbtune.DBTuneInstances.newPGDatabaseConnectionManager;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;

/**
 * @author huascar.sanchez@gmail.com (Huascar A. Sanchez)
 */
public class TaskSchedulerTest {
    TaskScheduler scheduler;

    @Before
    public void setUp() throws Exception {
        final DatabaseConnection connection;
        final WorkloadProfilerImpl profiler;

        final ThreadIBGAnalysis     analysis     = new ThreadIBGAnalysis(){
            @Override
            public void run() {
                info("Running IBG Analysis Task.");
                try {
                    final Field field = getClass().getDeclaredField("state");
                    field.set(this, RunnableState.DONE);
                } catch (Exception e) {
                    info(e.getMessage());
                }
            }

            @Override
            public void waitUntilDone() {
                info("Waiting for a second.");
            }

            @Override
            public void startAnalysis(IBGAnalyzer analyzer, InteractionLogger logger) {
                info("started analysis");
            }
        };
        final ThreadIBGConstruction construction = new ThreadIBGConstruction(){
            @Override
            public void run() {
                info("Running IBG Construction Task.");
                try {
                    final Field field = getClass().getDeclaredField("state");
                    field.set(this, RunnableState.DONE);
                } catch (Exception e) {
                    info(e.getMessage());
                }
            }

            @Override
            public void waitUntilDone() {
                info("Waiting for a second.");
            }

            @Override
            public void startConstruction(IndexBenefitGraphConstructor ibgCons) {
                info("started construction");
            }
        };

        connection = newPGDatabaseConnectionManager().connect();
        profiler   = new WorkloadProfilerImpl(connection, analysis, construction, new CandidatePool(), true);
        scheduler  = new TaskScheduler(
                 connection,
                 new CandidatePool(),
                 new CandidatesSelector(40,12345,10,100),
                 new LinkedBlockingDeque<SchedulerTask>(),
                 new LinkedBlockingDeque<SchedulerTask>(),
                 new LinkedBlockingDeque<SchedulerTask>()
        ){
            @Override
            WorkloadProfilerImpl getProfiler() {
                return profiler;
            }
        };

    }

    @Test
    public void testSchedulerCreateIndex() throws Exception {
        try {
            scheduler.start();
            double transitionCost = scheduler.create(newPGIndex(2345, 6543));
            assertThat(Double.compare(4.5, transitionCost), equalTo(0));
        } finally {
            scheduler.shutdown();
        }
    }

    @Ignore @Test
    public void testSchedulerAnalyseQuery() throws Exception {
        try {
            scheduler.start();
            final AnalyzedQuery aq = scheduler.analyzeQuery("SELECT * FROM T;");

            assertThat(aq, CoreMatchers.<Object>notNullValue());

            final ProfiledQuery p = aq.getProfileInfo();
            assertThat(p, CoreMatchers.<Object>notNullValue());
            assertThat(p.getWhatIfCount(), equalTo(2));

            final Snapshot snapshot = p.getCandidateSnapshot();
            assertThat(snapshot.maxInternalId(), equalTo(2));

            final IndexBenefitGraph graph = p.getIndexBenefitGraph();
            assertThat(Double.compare(graph.emptyCost(), 1.0) == 0, is(true));

        } finally {
            scheduler.shutdown();
        }
    }


    @After
    public void tearDown() throws Exception {
        scheduler = null;
    }


    private static Index newPGIndex(int indexId, int schemaId) throws Exception {
       return DBTuneInstances.newPGIndex(indexId, schemaId, generateColumns(3), generateDescVals(3));
    }

    private static void info(String message){
        Console.streaming().info(message);
    }
}
