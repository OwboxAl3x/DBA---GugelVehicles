/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package gugelcars;

import DBA.SuperAgent;
import com.eclipsesource.json.Json;
import com.eclipsesource.json.JsonObject;
import es.upv.dsic.gti_ia.core.ACLMessage;
import es.upv.dsic.gti_ia.core.AgentID;

/**
 *
 * @author Adrian Martin Jaimez
 */
public class Coches extends SuperAgent {
    
    private String nombreCoche1;
    private String nombreCoche2;
    private String nombreCoche3;
    private String nombreCoche4;
    private String nombreCoordinador;
    private MessageQueue mensajesCoordinador;
    private MessageQueue mensajesCoches;
    private MessageQueue mensajesServidor;
    private String conversationID;
    private String replyWith;
    
    public Coches(AgentID aid, String nombreCoordinador, String nombreCoche1, String nombreCoche2, String nombreCoche3, String nombreCoche4) throws Exception {
        super(aid);
        this.nombreCoordinador = nombreCoordinador;
        this.nombreCoche1 = nombreCoche1;
        this.nombreCoche2 = nombreCoche2;
        this.nombreCoche3 = nombreCoche3;
        this.nombreCoche4 = nombreCoche4;
        mensajesCoordinador = new MessageQueue(30); // OJO, solo caben 30 mensajes
        mensajesCoches = new MessageQueue(30); // OJO, solo caben 30 mensajes
        mensajesServidor = new MessageQueue(30); // OJO, solo caben 30 mensajes
        conversationID = "";
        replyWith = "";
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
            if (msg.getSender().equals(new AgentID(nombreCoordinador))){
                mensajesCoordinador.Push(msg);
            } else if (msg.getSender().equals(new AgentID("Cerastes"))){
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
    */
    @Override
    public void execute(){
        try {
            this.login();
        } catch (InterruptedException e){
            System.out.println("Error al sacar mensaje de la cola");
        }
    }
    
    public void login() throws InterruptedException{
        while (mensajesCoordinador.isEmpty()){}
        ACLMessage inboxLogin = mensajesCoordinador.Pop();
        
        JsonObject json= new JsonObject();
        conversationID = Json.parse(inboxLogin.getContent()).asObject().get("logueate").asString();
        
        boolean salir = false;
        while (!salir){
            json = new JsonObject();
            json.add("command","checkin");
            this.enviarMensaje(new AgentID("Cerastes"), json, ACLMessage.REQUEST, conversationID, null);

            while (mensajesCoordinador.isEmpty()){}
            inboxLogin = mensajesCoordinador.Pop(); 
            
            if (inboxLogin.getPerformativeInt() == ACLMessage.INFORM){
                salir = true;
            }
        }
        
        replyWith = inboxLogin.getReplyWith();
        json = Json.parse(inboxLogin.getContent()).asObject();
        this.enviarMensaje(new AgentID(nombreCoordinador),json,ACLMessage.INFORM,null,this.getName());
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
    *
    * @author Adrian Martin
    */
    @Override
    public void finalize()  {    
        System.out.println("\nAgente("+this.getName()+") Terminando"); 
        super.finalize();
    }
    
}
