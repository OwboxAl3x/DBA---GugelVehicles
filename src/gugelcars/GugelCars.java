/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package gugelcars;

import es.upv.dsic.gti_ia.core.AgentID;
import es.upv.dsic.gti_ia.core.AgentsConnection;

/**
 *
 * @author Adrian Martin Jaimez
 */
public class GugelCars  {
    
    public static void main(String[] args) throws Exception{
        Coches coches[] = null;
        Coordinador coordinador = null;
        
        AgentsConnection.connect("isg2.ugr.es", 6000, "Cerastes", "Boyero", "Carducci", false);
        
        try {
            
            
            
        } catch (Exception ex) {
            
            System.err.println("Fallo al crear al agente coche/sensor");
            System.exit(1);
            
        }
        
        
    }
    
}
