package edu.ucsc.dbtune.core;

import com.google.common.collect.ImmutableList;
import com.google.common.io.Files;
import static edu.ucsc.dbtune.core.JdbcConnectionManager.makeDatabaseConnectionManager;
import edu.ucsc.dbtune.spi.Environment;
import edu.ucsc.dbtune.spi.core.Console;
import edu.ucsc.dbtune.util.Strings;
import java.io.File;
import java.sql.SQLException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

/**
 * InumWhatIfOptimizer's Functional Test
 *
 * @author hsanchez@cs.ucsc.edu (Huascar A. Sanchez)
 */
public class InumWhatIfOptimizerTestFunctional {
  static DatabaseConnection       CONNECTION;
  static final Environment        ENV;
  static final String             WORKLOAD_PATH;
  static final String             FILE_NAME       = "workload.sql";
  static final String             DESTINATION;
  static final String             WORKLOAD_IN_USE;
  static {
    ENV                 = Environment.getInstance();
    WORKLOAD_PATH       = ENV.getScriptAtWorkloadsFolder("inum/" + FILE_NAME);
    DESTINATION         = ENV.getInumCacheDeploymentDir() + "/";
    WORKLOAD_IN_USE     = DESTINATION + FILE_NAME;
    try {
      CONNECTION = makeDatabaseConnectionManager(ENV.getAll()).connect();
    } catch (SQLException e) {
      throw new ExceptionInInitializerError(e);
    }
  }

  @Test public void testUseInumToEstimateCostOfWorload_WithHypotheticalIndexes() throws Exception {
    final InumWhatIfOptimizer optimizer = new InumWhatIfOptimizerImpl(CONNECTION);
    final String              workload  = WORKLOAD_IN_USE;

    final Iterable<DBIndex>   candidates = configureCandidates();
    optimizer.estimateCost(workload, candidates);
  }

  private static Iterable<DBIndex> configureCandidates() {
    final IndexExtractor extractor = CONNECTION.getIndexExtractor();
    final File workloadFile = new File(WORKLOAD_IN_USE);
    try {
      //todo(Huascar) the extractor is to constructing PGTables property...
      // we need the table name and the columns....WE NEED THAT
      // the problem is that PGTable state after DatabaseObject and
      // AbstractDatabase is broken
      final Iterable<DBIndex> candidates = extractor.recommendIndexes(
          Strings.wholeContentAsSingleLine(workloadFile)
      );
      return ImmutableList.copyOf(candidates);
    } catch (Exception e){
      return ImmutableList.of();
    }
  }

  @BeforeClass public static void setUp() throws Exception {
    final File    outputdir    = new File(DESTINATION);
    final File    twinWorkload = new File(WORKLOAD_IN_USE);

    if(outputdir.mkdirs())  { Console.streaming().info(outputdir.toString() + " has been created.");}
    else                    { Console.streaming().info(outputdir.toString() + " already exists.");}


    Files.copy(new File(WORKLOAD_PATH), twinWorkload);
  }

  @AfterClass public static void tearDown() throws Exception {
    if(CONNECTION != null) {
      if(CONNECTION.isOpened()){
        CONNECTION.close();
      }

      CONNECTION = null;
    }
  }

}
