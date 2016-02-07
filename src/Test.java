
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
    
    @Shared
    boolean bailOut=false;
    
    Random random = new Random();

    public void main () {
        for (int i = 0; i < 100_000; i++) {
            PCJ.put(random.nextInt(PCJ.threadCount()), "bailOut", random.nextBoolean());
            PCJ.getLocal("bailOut");
        }
    }
    public static void main (String[] args) {
        PCJ.deploy(Test.class, Test.class, new String[] {"localhost", "localhost","localhost","localhost","localhost","localhost","localhost","localhost"});
    }
    
}
