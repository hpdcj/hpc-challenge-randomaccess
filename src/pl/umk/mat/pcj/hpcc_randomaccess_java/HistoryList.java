/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package pl.umk.mat.pcj.hpcc_randomaccess_java;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Iterator;

/* An utility for lock- and sync- free version of RA problem; history list keeps a register 
 * of updates + stamps each update with a serial number
 */
public class HistoryList implements Serializable {
    private ArrayList<Long> updates = new ArrayList<>();
    private ArrayList<Long> stamps = new ArrayList<>();

    public ArrayList<Long> getUpdates() {
        return updates;
    }

    public ArrayList<Long> getStamps() {
        return stamps;
    }
    
    private long stamp = 0;
    public void add (long upd) {
        updates.add(upd);
        stamps.add(stamp++);
    }
    
    public void removeUpTo (long stamp) { 
        Iterator<Long> stampsIterator = stamps.iterator();
        Iterator<Long> updatesIterator = updates.iterator();
        
        while (stampsIterator.hasNext()) {
            updatesIterator.next();
            if (stampsIterator.next() <= stamp) {
                updatesIterator.remove();
                stampsIterator.remove();
            } else {
                break;
            }
        }
    }
}
