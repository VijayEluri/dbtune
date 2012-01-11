package edu.ucsc.dbtune.bip;

import java.sql.SQLException;
import org.junit.Test;
import edu.ucsc.dbtune.bip.interactions.InteractionBIP;
import edu.ucsc.dbtune.bip.util.BIPOutput;

/**
 * Test for the InteractionBIP class
 */
public class InteractionBIPTest extends BIPTestConfiguration
{  
    @Test
    public void testInteraction() throws Exception
    {
        try {
            double delta = 0.4;
            InteractionBIP bip = new InteractionBIP(delta);
            bip.setCandidateIndexes(candidateIndexes);
            bip.setCommunicator(communicator);
            bip.setMapSchemaToWorkload(mapSchemaToWorkload);
            bip.setWorkloadName(workloadName);
            
            BIPOutput output = bip.solve();
            System.out.println(output.toString());
        } catch (SQLException e){
            System.out.println(" error " + e.getMessage());
        }
    }
}