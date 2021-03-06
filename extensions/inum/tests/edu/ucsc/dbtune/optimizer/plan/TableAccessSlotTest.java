package edu.ucsc.dbtune.optimizer.plan;

import java.sql.SQLException;

import edu.ucsc.dbtune.metadata.Catalog;
import edu.ucsc.dbtune.metadata.ColumnOrdering;
import edu.ucsc.dbtune.metadata.Index;
import edu.ucsc.dbtune.metadata.Table;

import org.junit.BeforeClass;
import org.junit.Test;

import static edu.ucsc.dbtune.DBTuneInstances.configureCatalog;
import static edu.ucsc.dbtune.inum.FullTableScanIndex.getFullTableScanIndexInstance;
import static edu.ucsc.dbtune.metadata.ColumnOrdering.ASC;
import static edu.ucsc.dbtune.optimizer.plan.Operator.INDEX_SCAN;
import static edu.ucsc.dbtune.optimizer.plan.Operator.TABLE_SCAN;

import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;

import static org.junit.Assert.assertThat;
import static org.junit.Assert.fail;

/**
 * @author Ivo Jimenez
 */
public class TableAccessSlotTest
{
    private static Catalog catalog;

    /**
     *
     */
    @BeforeClass
    public static void beforeClass()
    {
        catalog = configureCatalog();
    }

    /**
     * @throws Exception
     *      if fails
     */
    @Test
    public void testConstruction() throws Exception
    {
        TableAccessSlot slot;

        Operator tblScan = new Operator(TABLE_SCAN, 3000, 1);
        Operator idxScan = new Operator(INDEX_SCAN, 5000, 1);

        Table table = catalog.<Table>findByName("schema_0.table_0");
        Index index = catalog.<Index>findByName("schema_0.table_0_index_2");
        ColumnOrdering io = new ColumnOrdering(table.columns().get(0), ASC);
        
        tblScan.add(table);
        idxScan.add(index);

        // check a table scan
        try {
            slot = new TableAccessSlot(tblScan);
            fail("construction should reject an operator with no columns fetched");
        } catch (SQLException e) {
            assertThat(e.getMessage(), is("No columns fetched for leaf"));
        }

        tblScan.addColumnsFetched(io);

        slot = new TableAccessSlot(tblScan);

        assertThat(slot.getTable(), is(table));
        assertThat(slot.getIndex(), is((Index) getFullTableScanIndexInstance(table)));
        assertThat(slot.isCompatible(getFullTableScanIndexInstance(table)), is(true));
        assertThat(slot.isCreatedFromFullTableScan(), is(true));
        assertThat(slot, is(slot.duplicate()));

        // check ixScan
        try {
            slot = new TableAccessSlot(idxScan);
            fail("construction should reject an operator with no columns fetched");
        } catch (SQLException e) {
            assertThat(e.getMessage(), is("No columns fetched for leaf"));
        }

        idxScan.addColumnsFetched(io);

        slot = new TableAccessSlot(idxScan);

        assertThat(slot.getTable(), is(table));
        assertThat(slot.getIndex(), is(index));
        assertThat(slot.isCompatible(index), is(true));
        assertThat(slot.isCompatible(getFullTableScanIndexInstance(index.getTable())), is(false));
        assertThat(slot, is(slot.duplicate()));
    }

    /**
     * @throws Exception
     *      if fails
     */
    @Test
    public void testHashAndEquals() throws Exception
    {
        Operator tblScan = new Operator(TABLE_SCAN, 3000, 1);
        Operator idxScan = new Operator(INDEX_SCAN, 5000, 1);

        Table table = catalog.<Table>findByName("schema_0.table_0");
        Index index = catalog.<Index>findByName("schema_0.table_0_index_2");
        ColumnOrdering io = new ColumnOrdering(table.columns().get(0), ASC);
        
        tblScan.addColumnsFetched(io);
        idxScan.addColumnsFetched(io);
        tblScan.add(table);
        idxScan.add(index);

        TableAccessSlot tblScanSlot = new TableAccessSlot(tblScan);
        TableAccessSlot idxScanSlot = new TableAccessSlot(idxScan);

        assertThat(tblScanSlot, is(tblScanSlot));
        assertThat(tblScanSlot, is(not(idxScanSlot)));

        assertThat(idxScanSlot, is(idxScanSlot));
        assertThat(idxScanSlot, is(not(tblScanSlot)));

        assertThat(tblScanSlot.hashCode(), is(tblScanSlot.hashCode()));
        assertThat(tblScanSlot.hashCode(), is(not(idxScanSlot.hashCode())));

        assertThat(idxScanSlot.hashCode(), is(idxScanSlot.hashCode()));
        assertThat(idxScanSlot.hashCode(), is(not(tblScanSlot.hashCode())));
    }
}
