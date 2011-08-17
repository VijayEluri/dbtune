package edu.ucsc.dbtune.spi;

import edu.ucsc.dbtune.spi.core.Console;

import static edu.ucsc.dbtune.spi.EnvironmentProperties.DATABASE;
import static edu.ucsc.dbtune.spi.EnvironmentProperties.DBMS;
import static edu.ucsc.dbtune.spi.EnvironmentProperties.FILE;
import static edu.ucsc.dbtune.spi.EnvironmentProperties.IBG;
import static edu.ucsc.dbtune.spi.EnvironmentProperties.INDEX_STATISTICS_WINDOW;
import static edu.ucsc.dbtune.spi.EnvironmentProperties.INUM;
import static edu.ucsc.dbtune.spi.EnvironmentProperties.JDBC_DRIVER;
import static edu.ucsc.dbtune.spi.EnvironmentProperties.MAX_NUM_INDEXES;
import static edu.ucsc.dbtune.spi.EnvironmentProperties.MAX_NUM_STATES;
import static edu.ucsc.dbtune.spi.EnvironmentProperties.NUM_PARTITION_ITERATIONS;
import static edu.ucsc.dbtune.spi.EnvironmentProperties.OUTPUT_FOLDERNAME;
import static edu.ucsc.dbtune.spi.EnvironmentProperties.OPTIMIZER;
import static edu.ucsc.dbtune.spi.EnvironmentProperties.OVERHEAD_FACTOR;
import static edu.ucsc.dbtune.spi.EnvironmentProperties.PASSWORD;
import static edu.ucsc.dbtune.spi.EnvironmentProperties.SCHEMA;
import static edu.ucsc.dbtune.spi.EnvironmentProperties.URL;
import static edu.ucsc.dbtune.spi.EnvironmentProperties.USERNAME;
import static edu.ucsc.dbtune.spi.EnvironmentProperties.WORKLOADS_FOLDERNAME;
import static edu.ucsc.dbtune.spi.EnvironmentProperties.WORKLOAD_NAME;
import static edu.ucsc.dbtune.util.Objects.cast;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Properties;
import java.util.Set;

/**
 * @author huascar.sanchez@gmail.com (Huascar A. Sanchez)
 */
public class Environment {
    private final Configuration configuration;

    public Environment() throws IOException {
        this(
                new PropertiesConfiguration(
                        getDefaultProperties(),
                        System.getProperty("user.dir") + "/config/" + FILE
                )
        );
    }

    Environment(Configuration configuration){
        this.configuration = configuration;
    }


    private static String asString(Object obj){
      return cast(obj, String.class);
    }

    /**
     * @return {@link EnvironmentProperties#URL}
     */
    public String getDatabaseUrl(){
        return asString(configuration.getProperty(URL));
    }

    /**
     * @return {@link EnvironmentProperties#JDBC_DRIVER}
     */
    public String getJDBCDriver(){
        return asString(configuration.getProperty(JDBC_DRIVER));
    }

    /**
     * @return {@link EnvironmentProperties#DATABASE}
     */
    public String getDatabaseName(){
        return asString(configuration.getProperty(DATABASE));
    }

    /**
     * @return {@link EnvironmentProperties#USERNAME}
     */
    public String getUsername(){
        return asString(configuration.getProperty(USERNAME));
    }

    /**
     * @return {@link EnvironmentProperties#SCHEMA}
     */
    public String getSchema(){
        return asString(configuration.getProperty(SCHEMA));
    }

    /**
     * @return {@link EnvironmentProperties#OPTIMIZER}
     */
    public String getOptimizer() throws IllegalArgumentException {
        String opt = asString(configuration.getProperty(OPTIMIZER));

        if(!opt.equals(INUM) && !opt.equals(IBG) && !opt.equals(DBMS)) {
            throw new IllegalArgumentException("Bad optimizer option " + opt);
        }

        return opt;
    }

    /**
     * @return {}EnvironmentProperties#PASSWORD
     */
    public String getPassword(){
        return asString(configuration.getProperty(PASSWORD));
    }

    /**
     * @return {@link EnvironmentProperties#OUTPUT_FOLDERNAME}
     */
    public String getOutputFoldername(){
        return asString(configuration.getProperty(OUTPUT_FOLDERNAME));
    }

    /**
     * @return {@link EnvironmentProperties#WORKLOADS_FOLDERNAME}
     */
    public String getWorkloadsFoldername(){
        return asString(configuration.getProperty(WORKLOADS_FOLDERNAME));
    }

    /**
     * @return {@link EnvironmentProperties#WORKLOAD_NAME}
     */
    public String getWorkloadName(){
        return asString(configuration.getProperty(WORKLOAD_NAME));
    }

    /**
     * @return {@link EnvironmentProperties#MAX_NUM_INDEXES}
     */
    public int getMaxNumIndexes(){
        String maxSize = asString(configuration.getProperty(MAX_NUM_INDEXES));
        return Integer.valueOf(maxSize);
    }

    /**
     * @return {@link EnvironmentProperties#MAX_NUM_STATES}
     * @throws NumberFormatException
     *      unable to return the max num of states due to the stated reason.
     */
    public int getMaxNumStates() throws NumberFormatException {
        String numOfStates = asString(configuration.getProperty(MAX_NUM_STATES));
        return Integer.valueOf(numOfStates);
    }

    /**
     * @return {@link EnvironmentProperties#OVERHEAD_FACTOR}
     * @throws NumberFormatException
     *      unable to return the overhead factor due to the stated reason.
     */
    public float getOverheadFactor() throws NumberFormatException {
        String overheadFactor = asString(configuration.getProperty(OVERHEAD_FACTOR));
        return Float.valueOf(overheadFactor);
    }

    /**
     * @return {@link EnvironmentProperties#NUM_PARTITION_ITERATIONS}
     * @throws NumberFormatException
     *      unable to return the overhead factor due to the stated reason.
     */
    public int getNumPartitionIterations() throws NumberFormatException {
        String numPartitionIterations = asString(configuration.getProperty(NUM_PARTITION_ITERATIONS));
        return Integer.valueOf(numPartitionIterations);
    }

    /**
     * @return {@link EnvironmentProperties#INDEX_STATISTICS_WINDOW}
     * @throws NumberFormatException
     *      unable to return the overhead factor due to the stated reason.
     */
    public int getIndexStatisticsWindow() throws NumberFormatException {
        String indexStatisticsWindow = asString(configuration.getProperty(INDEX_STATISTICS_WINDOW));
        return Integer.valueOf(indexStatisticsWindow);
    }

    /**
     * Returns the path to a given workload file. The path is qualified against the value of {@link 
     * EnvironmentProperties#WORKLOADS_FOLDERNAME}.
     *
     * @param scriptPath
     *    relative path to the file contained in {@link EnvironmentProperties#WORKLOADS_FOLDERNAME}.
     * @return
     *    {@code String} containing the path to the given script filename
     */
    public String getScriptAtWorkloadsFolder(String scriptPath){
        return getWorkloadsFoldername() + scriptPath;
    }

    /**
     * Returns the path to a file inside the workload folder. The path is qualified against the 
     * concatenation of {@link EnvironmentProperties#WORKLOADS_FOLDERNAME} and
     * {@link EnvironmentProperties#WORKLOAD_NAME}. The contents of the
     * returned string look like:
     * <p>
     * {@code getWorkloadsFoldername()} + "/" + {@code getWorkloadName() + {@code filename} }
     *
     * @param filename
     *    name of file contained inside {@link EnvironmentProperties#WORKLOADS_FOLDERNAME}.
     * @return
     *    {@code String} containing the path to the given script filename
     * @see #getWorkloadsFoldername
     * @see #getWorkloadName
     */
    public String getFilenameAtWorkloadFolder(String filename){
        return getWorkloadsFoldername() + "/" + getWorkloadName() + "/" + filename;
    }

    /**
     * Returns the path to a file inside the output folder. The path is qualified against the 
     * concatenation of {@link EnvironmentProperties#OUTPUT_FOLDERNAME} and
     * {@link EnvironmentProperties#WORKLOAD_NAME}. The contents of the
     * returned string look like:
     * <p>
     * {@code getOutputFoldername()} + "/" + {@code getWorkloadName() + {@code filename} }
     *
     * @param filename
     *    name of file contained inside {@link EnvironmentProperties#OUTPUT_FOLDERNAME}.
     * @return
     *    {@code String} containing the path to the given script filename
     * @see #getOutputFoldername
     * @see #getWorkloadName
     */
    public String getFilenameAtOutputFolder(String filename){
        return getOutputFoldername() + "/" + getWorkloadName() + "/" + filename;
    }

    /**
     * Returns all the properties defined in the {@code Environment} class as a {@link Properties} 
     * object.
     * <p>
     * Note that the {@code Properties} object returned by this method, unlike the other getters of 
     * the class, is not thread safe.
     *
     * @return {@code Properties} object containing all the settings contained in the class
     */
    public Properties getAll(){
        Properties properties = new Properties();

        for ( Entry<String, Object> property : configuration.getAllProperties() ) {
            properties.setProperty( property.getKey(), property.getValue().toString() );
        }

        return properties;
    }

    private static Properties getDefaultProperties(){
        return new Properties(){
            /**
             * 
             */
            private static final long serialVersionUID = 1L;

            {
                setProperty(URL,         "jdbc:postgresql://aigaion.cse.ucsc.edu/test");
                setProperty(USERNAME,    "dbtune");
                setProperty(PASSWORD,    "dbtuneadmin");
                setProperty(WORKLOADS_FOLDERNAME, "resources/workloads/postgres");
                setProperty(DATABASE,    "test");
                setProperty(JDBC_DRIVER, "org.postgresql.Driver");
            }
        };
    }

    static interface Configuration {
       Object getProperty(String propertyName);
       Set<Entry<String,Object>> getAllProperties();
    }

    static abstract class AbstractConfiguration implements Configuration {
        private final Map<String, Object> properties = new HashMap<String, Object>();

        AbstractConfiguration(){}

        /**
         * This method should be overridden to check whether the
         * properties could maybe have changed, and if yes, to reload
         * them.
         */
        protected abstract void checkForPropertyChanges();
        
        protected abstract Properties getDefaults();
        
        protected boolean isDefaultsMode(){ return false; }

        @Override
        public Object getProperty(String propertyName) {
            checkForPropertyChanges();
            synchronized (properties){
                return !isDefaultsMode() ? properties.get(propertyName) : getDefaults().getProperty(propertyName);
            }
        }

        @Override
        public Set<Entry<String, Object>> getAllProperties() {
            synchronized (properties){
                return properties.entrySet();
            }
        }

        /**
       * setting a property.
       *
       * @param propertyName
       *     name of property
       * @param value
       *    value of property.
       */
      protected final void setProperty(String propertyName, Object value) {
        synchronized (properties) {
          Object old = properties.get(propertyName);
          if ((value != null && !value.equals(old))
              || value == null && old != null) {
            properties.put(propertyName, value);
          }
        }
      }
    }

    static class PropertiesConfiguration extends AbstractConfiguration {
        private final File          file;
        private final Properties    defaults;

        private boolean useDefaults = false;
        private long    lastModified = 0;

        PropertiesConfiguration(Properties defaults, String filename) throws IOException {
            super();
            this.defaults       = defaults;
            this.file           = new File(filename);
            this.useDefaults    = !file.exists();
            loadProperties();
        }

        @Override
        protected void checkForPropertyChanges() {
            if (lastModified != file.lastModified()) {
              try {
                lastModified = file.lastModified();
                loadProperties();
              } catch (IOException e) {
                Console.streaming().error("unable to load properties" + e);
                throw new RuntimeException(e);
              }
            }
        }

        @Override
        protected Properties getDefaults() {
            return defaults;
        }

        @Override
        protected boolean isDefaultsMode() {
            return useDefaults;
        }

        private void loadProperties() throws IOException {
            final Properties properties = new Properties();
            if(!useDefaults){
                properties.load(new FileInputStream(file));
                setAllProperties(properties);
            } else {
                setAllProperties(getDefaultProperties());
            }
        }

        private void setAllProperties(Properties properties) {
            for (Map.Entry<Object, Object> entry : properties.entrySet()) {
                setProperty(asString(entry.getKey()), entry.getValue());
            }
        }
    }

    /**
     * @return the environment singleton.
     */
    public static Environment getInstance(){
        return Installer.INSTANCE;
    }

    /** Lazy-constructed singleton, which is thread safe */
    static class Installer {
        static final Environment INSTANCE;

        static {
            try {
                INSTANCE = new Environment();
            } catch (IOException e) {
                throw new IllegalStateException(e);
            }
        }
    }
}
