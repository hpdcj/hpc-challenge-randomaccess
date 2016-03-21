
import java.util.Random;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.pcj.FutureObject;
import org.pcj.PCJ;
import org.pcj.Shared;
import org.pcj.StartPoint;
import org.pcj.Storage;

/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

/**
 *
 * @author Łukasz
 */
public class Test extends Storage implements StartPoint {
    

    public void main () {
        System.out.println("Hello from " + PCJ.myId());
    }
    public static void main (String[] args) {
        String nodesFileName = "nodes.txt";
        if (args.length > 0) {
            nodesFileName = args[0];
        }
        
        PCJ.start (Test.class, Test.class, nodesFileName);
    }
    
}
