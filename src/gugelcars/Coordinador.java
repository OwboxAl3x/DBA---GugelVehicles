/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package gugelcars;

import DBA.SuperAgent;
import com.eclipsesource.json.JsonArray;
import com.eclipsesource.json.JsonObject;
import es.upv.dsic.gti_ia.core.ACLMessage;
import es.upv.dsic.gti_ia.core.AgentID;
import java.io.FileOutputStream;
import java.io.IOException;

/**
 *
 * @author Adrian Martin Jaimez
 */
public class Coordinador extends SuperAgent {
    
    private String nombreCoche1;
    private String nombreCoche2;
    private String nombreCoche3;
    private String nombreCoche4;
    private String mapa;
    private MessageQueue mensajesCoches;
    private MessageQueue mensajesServidor;
    private String conversationID;
    
    public Coordinador(AgentID aid, String nombreCoche1, String nombreCoche2, String nombreCoche3, String nombreCoche4, String mapa) throws Exception {
        super(aid);
        this.nombreCoche1 = nombreCoche1;
        this.nombreCoche2 = nombreCoche2;
        this.nombreCoche3 = nombreCoche3;
        this.nombreCoche4 = nombreCoche4;
        this.mapa = mapa;
        mensajesCoches = new MessageQueue(30); // OJO, solo caben 30 mensajes
        mensajesServidor = new MessageQueue(30); // OJO, solo caben 30 mensajes
        conversationID = "";
    }
    
    /**
    *
    * @author Adrian Martin Jaimez
    */
    @Override
    public void init(){
        System.out.println("\nAgente("+this.getName()+") Iniciando");
    }
    
    /**
    *
    * @author Manuel Ros Rodríguez
    */     
    public void onMessage(ACLMessage msg)  {
	try {
            if (msg.getSender().equals(new AgentID("Cerastes"))){
                mensajesServidor.Push(msg);
            } else {
                mensajesCoches.Push(msg); 
            }
        } catch (InterruptedException e){
            System.out.println("Error al añadir, no hay espacio en la cola de mensajes");
        }
    }
    
    /**
    *
    * @author Adrian Martin Jaimez
    * @author Manuel Ros Rodríguez
    */
    @Override
    public void execute(){
        try {
            this.login();
        } catch (InterruptedException e){
            System.out.println("Error al sacar mensaje de la cola");
        }
    }

    /**
    *  
    * Inicia sesión en el servidor e inicializa a los coches
    *
    * @author Manuel Ros Rodríguez
    */    
    public void login() throws InterruptedException {
        boolean salir = false;
        ACLMessage inboxLogin = null;
        JsonObject json = null;
        ACLMessage outbox = null;
        
        while (!salir){
            json= new JsonObject();
            json.add("world", mapa);
            this.enviarMensaje(new AgentID("Cerastes"),json,ACLMessage.SUBSCRIBE,null,null);

            while (mensajesServidor.isEmpty()){}
            inboxLogin = mensajesServidor.Pop();

            // Si es un inform, terminamos
            if (inboxLogin.getPerformativeInt() == ACLMessage.INFORM){
                salir = true;
            }
        }
        
        conversationID = inboxLogin.getConversationId();
        
        json = new JsonObject();
        json.add("logueate",conversationID); 
        this.enviarMensaje(new AgentID(nombreCoche1),json,ACLMessage.REQUEST,null,nombreCoche1);
    }
    
    /**
    *
    * @author Manuel Ros Rodríguez
    */
    public void enviarMensaje(AgentID receptor, JsonObject contenido, int performative, String conversationID, String replyWith){
        ACLMessage outbox = new ACLMessage();
        outbox.setSender(this.getAid());
        outbox.setReceiver(new AgentID("Cerastes"));
        outbox.setContent(contenido.toString());
        outbox.setPerformative(ACLMessage.REQUEST);
        if (conversationID != null)
            outbox.setConversationId(conversationID);
        if (replyWith != null)
            outbox.setInReplyTo(replyWith);
        this.send(outbox);        
    }
    
    /**
     * Crea una imagen a partir de la traza
     * 
     * @author Manuel Ros Rodríguez
     * @param inJsonImagen
     */
    public void crearImagen(JsonObject inJsonImagen){
        
        try {
            JsonArray array = inJsonImagen.get("trace").asArray();
            byte data[] = new byte [array.size()];
            for (int i=0; i<data.length; i++){
                    data[i] = (byte) array.get(i).asInt();
            }
            FileOutputStream fos;
            fos = new FileOutputStream("traza"+this.mapa+".png");
            fos.write(data);
            fos.close();
            System.out.println("Imagen creada");
        } catch (IOException ex) {
            System.out.println("Fallo al crear la imagen");
        }
    }
    
    
    /**
    *
    * @author Adrian Martin
    */
    @Override
    public void finalize()  {    
        System.out.println("\nAgente("+this.getName()+") Terminando"); 
        super.finalize();
    }
}
