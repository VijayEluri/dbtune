package edu.ucsc.dbtune.util;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import edu.ucsc.dbtune.metadata.Index;
import edu.ucsc.dbtune.metadata.Schema;
import edu.ucsc.dbtune.metadata.Table;

/**
 * @author Ivo Jimenez
 */
public final class MetadataUtils
{
    /**
     * Utility class.
     */
    private MetadataUtils()
    {
    }

    /**
     * Returns the set of schemas referenced by the given collection of tables.
     *
     * @param tables
     *      a collection of tables
     * @return
     *      the set of schemas corresponding to one or more tables in the set
     */
    public static Set<Schema> getReferencedSchemas(Collection<Table> tables)
    {
        Set<Schema> schemas = new HashSet<Schema>();

        for (Table t : tables)
            schemas.add(t.getSchema());

        return schemas;
    }

    /**
     * Partitions a set of indexes based on the table they refer.
     *
     * @param indexes
     *      a collection of indexes
     * @return
     *      the set of tables corresponding to one or more indexes in the set
     */
    public static Map<Table, Set<Index>> getIndexesPerTable(Set<Index> indexes)
    {
        Map<Table, Set<Index>> indexesPerTable = new HashMap<Table, Set<Index>>();
        Set<Index> indexesForTable;

        for (Index i : indexes) {

            indexesForTable = indexesPerTable.get(i.getTable());

            if (indexesForTable == null) {
                indexesForTable = new HashSet<Index>();
                indexesPerTable.put(i.getTable(), indexesForTable);
            }

            indexesForTable.add(i);
        }

        return indexesPerTable;
    }

    /**
     * Returns the set of indexes that reference one of the tables contained in {@code tables}.
     *
     * @param indexes
     *      a collection of indexes
     * @param tables
     *      a collection of tables
     * @return
     *      the set of indexes, where each references one table in {@code tables}
     */
    public static Set<Index> getIndexesReferencingTables(
            Collection<Index> indexes, Collection<Table> tables)
    {
        Set<Index> indexesReferencingTables = new HashSet<Index>();

        for (Index i : indexes)
            if (tables.contains(i.getTable()))
                indexesReferencingTables.add(i);

        return indexesReferencingTables;
    }

    /**
     * Returns the set of tables referenced by the given collection of indexes.
     *
     * @param indexes
     *      a collection of indexes
     * @return
     *      the set of tables corresponding to one or more indexes in the set
     */
    public static Set<Table> getReferencedTables(Collection<Index> indexes)
    {
        Set<Table> tables = new HashSet<Table>();

        for (Index i : indexes)
            tables.add(i.getTable());

        return tables;
    }

    /**
     * Finds an index by name in a set of indexes. This looks only at the name of the of the index 
     * and not to the whole fully qualified one.
     *
     * @param indexes
     *      set of indexes where one with the given name is being looked for
     * @param name
     *      name of the index being looked for
     * @return
     *      the index with the given name; {@code null} if not found
     */
    public static Index find(Set<Index> indexes, String name)
    {
        for (Index i : indexes)
            if (i.getName().equals(name))
                return i;

        return null;
    }
}
