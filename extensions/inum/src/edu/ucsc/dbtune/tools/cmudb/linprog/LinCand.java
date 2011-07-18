package edu.ucsc.dbtune.tools.cmudb.linprog;

import edu.ucsc.dbtune.tools.cmudb.model.Index;
import java.util.ArrayList;
import java.util.LinkedHashSet;


public class LinCand { 


    int ID; 
    LinkedHashSet candidate; //pointer to the actual column set. p
    Index index; // pointer to a configuration object that contains only that index.
    int used; 
    ArrayList containing_configs; //candidates containing this index... 
    float size; 
    LinCand() { 
	used = 0;
	size = 0;
	//nothing for now. 
	containing_configs = new ArrayList();
    }


    //public String toString() { return config.toString() + " " + containing_configs;}
    public String toString() { return index.toString();}
    
}
