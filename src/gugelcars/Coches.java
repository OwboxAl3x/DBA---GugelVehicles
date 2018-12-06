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
 * @author Manuel Ros Rodríguez
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
    private int tamanoMapa;
    private int cuadrante;
    private boolean puedoVolar;
    
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
        tamanoMapa = 0;
        cuadrante = 0;
        puedoVolar = false;
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
        boolean salirSubscribe = false;
        ACLMessage inbox = null;
        JsonObject json;
        
        while (!salirSubscribe){
            while (mensajesCoordinador.isEmpty()){}
            inbox = mensajesCoordinador.Pop();

            json = new JsonObject();
            conversationID = Json.parse(inbox.getContent()).asObject().get("logueate").asString();

            boolean salir = false;
            while (!salir){
                json = new JsonObject();
                json.add("command","checkin");
                this.enviarMensaje(new AgentID("Cerastes"), json, null, ACLMessage.REQUEST, conversationID, null);

                while (mensajesServidor.isEmpty()){}
                inbox = mensajesServidor.Pop(); 

                // Si es un inform, terminamos
                if (inbox.getPerformativeInt() == ACLMessage.INFORM)
                    salir = true;
            }

            // Le mandamos al coordinador las capabilities y la información que nos da el GPS
            replyWith = inbox.getReplyWith();
            JsonObject mensajeCoordinador = Json.parse(inbox.getContent()).asObject();
            this.enviarMensaje(new AgentID("Cerastes"), new JsonObject(), null, ACLMessage.QUERY_REF, conversationID, replyWith);
            
            while (mensajesServidor.isEmpty()){}
            inbox = mensajesServidor.Pop();  
            replyWith = inbox.getReplyWith();
            
            mensajeCoordinador.add("x", Json.parse(inbox.getContent()).asObject().get("result").asObject().get("x").asInt());
            mensajeCoordinador.add("y", Json.parse(inbox.getContent()).asObject().get("result").asObject().get("y").asInt());
            
            this.enviarMensaje(new AgentID(nombreCoordinador), mensajeCoordinador, null, ACLMessage.INFORM, null, this.getName());

            while (mensajesCoordinador.isEmpty()){}
            inbox = mensajesCoordinador.Pop(); 

            // Si recibimos un CANCEL, repetimos el bucle
            if (inbox.getPerformativeInt() != ACLMessage.CANCEL)
                salirSubscribe = true;
        }
        
        if (inbox.getContent().contains("calcularTamanoMapa")){
            // Si hemos recibido este mensajes es porque este coche puede volar
            puedoVolar = true;
            this.calcularTamanoMapa();
            
            json = new JsonObject();
            json.add("tamanoMapa", tamanoMapa);
            this.enviarMensaje(new AgentID(nombreCoordinador), json, null, ACLMessage.INFORM, null, this.getName());
            
            while (mensajesCoordinador.isEmpty()){}
            inbox = mensajesCoordinador.Pop(); 
        }
        
        if (puedoVolar){
            // En este caso no nos hace falta sacar el tamaño del mapa, ya lo tenemos
            cuadrante = Json.parse(inbox.getContent()).asObject().get("empieza").asInt();
        } else {
            cuadrante = Json.parse(inbox.getContent()).asObject().get("empieza").asInt();
            tamanoMapa = Json.parse(inbox.getContent()).asObject().get("tamanoMapa").asInt();
        }
        
        json = new JsonObject();
        json.add("result", "OK");
        this.enviarMensaje(new AgentID(nombreCoordinador), json, null, ACLMessage.INFORM, null, this.getName());        
    }
    
    /**
    *
    * @author Manuel Ros Rodríguez
    * @author Adrian Martin Jaimez
    */    
    public void calcularTamanoMapa(){
        // Esta parte es el mismo bucle que tiene que hacer adrian, cuando lo haga lo pegamos aqui y ya, por eso le pongo de author
        // Nos movemos siempre para abajo, hasta encontrar una pared limite
    }
    
    /**
    *
    * Envía un mensaje con los parametros que le digamos al receptor que le digamos
    * Si hay algún argumento que no vamos a utilizar, escribir null
    * 
    * @author Manuel Ros Rodríguez
    */
    public void enviarMensaje(AgentID receptor, JsonObject contenido, String contenidoString, int performative, String conversationID, String replyWith){
        ACLMessage outbox = new ACLMessage();
        outbox.setSender(this.getAid());
        outbox.setReceiver(receptor);
        outbox.setPerformative(performative);
        if (contenido != null)
            outbox.setContent(contenido.toString());
        if (contenidoString != null)
            outbox.setContent(contenidoString);
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
