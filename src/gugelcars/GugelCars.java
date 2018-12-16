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
 * @author Manuel Ros Rodríguez
 */
public class GugelCars  {
    
    public static void main(String[] args) throws Exception{
        Coches coches[] = null;
        Coordinador coordinador = null;
        
        AgentsConnection.connect("isg2.ugr.es", 6000, "Cerastes", "Boyero", "Carducci", false);
        
        try {
            int numeroInicial = 5;
            String nombreCoche1 = "car"+numeroInicial;
            String nombreCoche2 = "car"+numeroInicial+1;
            String nombreCoche3 = "car"+numeroInicial+2;
            String nombreCoche4 = "car"+numeroInicial+3;
            String nombreCoordinador = "coordinador"+numeroInicial;
            String mapa = "map1";
            
            coches = new Coches[4];
            coches[0] = new Coches(new AgentID(nombreCoche1),nombreCoordinador, nombreCoche1, nombreCoche2, nombreCoche3, nombreCoche4);
            coches[1] = new Coches(new AgentID(nombreCoche2),nombreCoordinador, nombreCoche1, nombreCoche2, nombreCoche3, nombreCoche4);
            coches[2] = new Coches(new AgentID(nombreCoche3),nombreCoordinador, nombreCoche1, nombreCoche2, nombreCoche3, nombreCoche4);
            coches[3] = new Coches(new AgentID(nombreCoche4),nombreCoordinador, nombreCoche1, nombreCoche2, nombreCoche3, nombreCoche4);
            coordinador = new Coordinador(new AgentID(nombreCoordinador), nombreCoche1, nombreCoche2, nombreCoche3, nombreCoche4, mapa);            
            
        } catch (Exception ex) {
            
            System.err.println("Fallo al crear los agentes");
            System.exit(1);
            
        }
        
        for (int i=0; i<4; i++){
            coches[i].start();
        }
        
        coordinador.start();
    }
    
}
