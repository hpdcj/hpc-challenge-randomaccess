
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
    Integer happySharedInteger;
    String name = "happySharedInteger";
    
    public static void main (String[] args) {
        PCJ.deploy(Test.class, Test.class, new String[]{"localhost", "localhost"});
    }

    @Override
    public void main() throws Throwable {
        if (PCJ.myId() == 0) {
            thread0();
        } else {
            thread1();
        }
    }

    private void thread0() {
        try {
            Thread.sleep(2000);
            FutureObject<Integer> result = PCJ.cas(1, name, 2, 3); // <-----  2
            Thread.sleep(5000);
            PCJ.barrier();
            Thread.sleep(2000);
            System.out.println(result.get());
            System.out.println(PCJ.get(1, name));
        } catch (InterruptedException ex) {
            Logger.getLogger(Test.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    private void thread1() {
        try {
            PCJ.putLocal(name, 1); // <--- 1
            Thread.sleep(5000);
            PCJ.barrier();
            PCJ.putLocal(name, 2); // <--- 3
        } catch (InterruptedException ex) {
            Logger.getLogger(Test.class.getName()).log(Level.SEVERE, null, ex);
        }
    }
    
}
