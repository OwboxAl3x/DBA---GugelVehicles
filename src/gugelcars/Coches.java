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
import java.util.ArrayList;
import java.util.Map;
import java.util.TreeMap;

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
    private int prioridad;
    
    private double bateria = 0.0;
    private int x;
    private int y;
    private ArrayList<ArrayList<Integer>> mapaPasos = new ArrayList<>();
    private ArrayList<ArrayList<Float>> mapaEscaner = new ArrayList<>();
    
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
        prioridad = 0;
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
            if (msg.getSender().toString().contains(nombreCoordinador)){
                mensajesCoordinador.Push(msg);
            } else if (msg.getSender().toString().contains("Cerastes")){
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
            inbox = this.recibirMensaje(mensajesCoordinador);

            json = new JsonObject();
            conversationID = Json.parse(inbox.getContent()).asObject().get("logueate").asString();

            boolean salir = false;
            while (!salir){
                json = new JsonObject();
                json.add("command","checkin");
                this.enviarMensaje(new AgentID("Cerastes"), json, null, ACLMessage.REQUEST, conversationID, null);

                inbox = this.recibirMensaje(mensajesServidor);

                // Si es un inform, terminamos
                if (inbox.getPerformativeInt() == ACLMessage.INFORM)
                    salir = true;
            }

            // Le mandamos al coordinador las capabilities y la información que nos da el GPS
            replyWith = inbox.getReplyWith();
            JsonObject mensajeCoordinador = Json.parse(inbox.getContent()).asObject();
            this.enviarMensaje(new AgentID("Cerastes"), new JsonObject(), null, ACLMessage.QUERY_REF, conversationID, replyWith);
            
            inbox = this.recibirMensaje(mensajesServidor); 
            replyWith = inbox.getReplyWith();
            
            mensajeCoordinador.add("x", Json.parse(inbox.getContent()).asObject().get("result").asObject().get("x").asInt());
            mensajeCoordinador.add("y", Json.parse(inbox.getContent()).asObject().get("result").asObject().get("y").asInt());
            
            this.enviarMensaje(new AgentID(nombreCoordinador), mensajeCoordinador, null, ACLMessage.INFORM, null, this.getName());

            inbox = this.recibirMensaje(mensajesCoordinador);

            // Si recibimos un CANCEL, repetimos el bucle
            if (inbox.getPerformativeInt() != ACLMessage.CANCEL)
                salirSubscribe = true;
        }
        System.out.println("ohsi");
        
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
    
    public ACLMessage recibirMensaje(MessageQueue cola) throws InterruptedException{
        while (cola.isEmpty()){Thread.sleep(500);}
        return (cola.Pop());
    }
    
    /**
     * @author Adrian Martin Jaimez
     * 
     * No tengo muy claro que nombre es el de este metodo
     */
    
    public void explorar() throws InterruptedException{
        boolean veoMeta = false;
        ACLMessage inbox = null;
        JsonObject json;
        int bateria = 0;

        while (mensajesCoordinador.isEmpty()){};
        inbox = mensajesCoordinador.Pop();
        json = new JsonObject();
        conversationID = Json.parse(inbox.getContent()).asObject().get("empieza").asString();
        
        while(!veoMeta){
            this.enviarMensaje(new AgentID("Cerastes"), null, null, ACLMessage.QUERY_REF, conversationID, null);
            while (mensajesServidor.isEmpty()){}
                inbox = mensajesServidor.Pop(); 

                // Si es un inform, guardamos los datos
                if (inbox.getPerformativeInt() == ACLMessage.INFORM){
                    bateria = Json.parse(inbox.getContent()).asObject().get("result").asObject().get("battery").asInt();
                }else{
                    //exit();
                }
                
                if(bateria <= 1){
                    json = new JsonObject();
                    json.add("command","refuel");
                    this.enviarMensaje(new AgentID("Cerastes"), json, null, ACLMessage.REQUEST, conversationID, null);
                    while (mensajesServidor.isEmpty()){}
                        inbox = mensajesServidor.Pop(); 
                    if (inbox.getPerformativeInt() != ACLMessage.INFORM){
                        //exit()
                    }
                }else{
                    json = new JsonObject();
                    json.add("command","moveX");
                    this.enviarMensaje(new AgentID("Cerastes"), json, null, ACLMessage.REQUEST, conversationID, null);
                    while (mensajesServidor.isEmpty()){}
                        inbox = mensajesServidor.Pop(); 
                    if (inbox.getPerformativeInt() != ACLMessage.INFORM){
                        //exit()
                    }
                }
                    
        }
        
        
    }
    
       /**
     * @author Adrian Martin Jaimez
     * 
     * No tengo muy claro que nombre es el de este metodo
     */
    
    public void irAMeta() throws InterruptedException{
        boolean goal = false;
        ACLMessage inbox = null;
        JsonObject json;
        int bateria = 0;

        while (mensajesCoordinador.isEmpty()){};
        inbox = mensajesCoordinador.Pop();
        json = new JsonObject();
        conversationID = Json.parse(inbox.getContent()).asObject().get("empieza").asString();
        
        while(!goal){
            this.enviarMensaje(new AgentID("Cerastes"), null, null, ACLMessage.QUERY_REF, conversationID, null);
            while (mensajesServidor.isEmpty()){}
                inbox = mensajesServidor.Pop(); 

                // Si es un inform, guardamos los datos
                if (inbox.getPerformativeInt() == ACLMessage.INFORM){
                    bateria = Json.parse(inbox.getContent()).asObject().get("result").asObject().get("battery").asInt();
                }else{
                    //exit();
                }
                
                if(bateria <= 1){
                    json = new JsonObject();
                    json.add("command","refuel");
                    this.enviarMensaje(new AgentID("Cerastes"), json, null, ACLMessage.REQUEST, conversationID, null);
                    while (mensajesServidor.isEmpty()){}
                        inbox = mensajesServidor.Pop(); 
                    if (inbox.getPerformativeInt() != ACLMessage.INFORM){
                        //exit()
                    }
                }else{
                    json = new JsonObject();
                    json.add("command","moveX");
                    this.enviarMensaje(new AgentID("Cerastes"), json, null, ACLMessage.REQUEST, conversationID, null);
                    while (mensajesServidor.isEmpty()){}
                        inbox = mensajesServidor.Pop(); 
                    if (inbox.getPerformativeInt() != ACLMessage.INFORM){
                        //exit()
                    }
                }
                    
        }
        
        
    }
    
    /**
     * @author Adrian Martin Jaimez 
     * 
     */
    public void trafico() throws InterruptedException{
        ACLMessage inbox = null;
        JsonObject json;
        int suPrioridad=0;
        while (mensajesCoches.isEmpty()){};
        inbox = mensajesCoches.Pop();
        json = new JsonObject();
        conversationID = Json.parse(inbox.getContent()).asObject().get("empieza").asString();
    
        this.enviarMensaje(new AgentID("OTROCOCHE"), null, null, ACLMessage.QUERY_REF, conversationID, null);
        if (inbox.getPerformativeInt() == ACLMessage.INFORM){
            suPrioridad = Json.parse(inbox.getContent()).asObject().get("prioridad").asObject().asInt();
        }else{
            //exit();
        }
        
        if(suPrioridad>this.prioridad){
            this.enviarMensaje(new AgentID("OTROCOCHE"), json, null, ACLMessage.REQUEST, conversationID, null);
        }else{
            //no me muevo
        }
    }
    
    /**
     * Obtiene el valor del escaner de una casilla
     * @author Fernando Ruiz Hernández
     * @author Adrian Martin 
     */
    
    public float getValorEscaner(int x, int y, int celda) {
        float valor = Float.MAX_VALUE;
        switch (celda){
            case 6:
                valor = this.mapaEscaner.get(y-1).get(x-1);
                break;
            case 7:
                valor = this.mapaEscaner.get(y-1).get(x);
                break;
            case 8:
                valor = this.mapaEscaner.get(y-1).get(x+1);
                break;
            case 11:
                valor = this.mapaEscaner.get(y).get(x-1);
                break;
            case 13:
                valor = this.mapaEscaner.get(y).get(x+1);
                break;
            case 16:
                valor = this.mapaEscaner.get(y+1).get(x-1);
                break;
            case 17:
                valor = this.mapaEscaner.get(y+1).get(x);
                break;
            case 18:
                valor = this.mapaEscaner.get(y+1).get(x+1);
                break;
        }
        return valor;
    }
    
    /**
     * Obtiene el valor de pasos de una casilla
     * @author Fernando Ruiz Hernández
     * @author Adrian Martin 
     */
    
    public int getValorPasos(int x, int y, int celda) {
        int valor = Integer.MAX_VALUE;
        switch (celda){
            case 6:
                valor = this.mapaPasos.get(y-1).get(x-1);
                break;
            case 7:
                valor = this.mapaPasos.get(y-1).get(x);
                break;
            case 8:
                valor = this.mapaPasos.get(y-1).get(x+1);
                break;
            case 11:
                valor = this.mapaPasos.get(y).get(x-1);
                break;
            case 13:
                valor = this.mapaPasos.get(y).get(x+1);
                break;
            case 16:
                valor = this.mapaPasos.get(y+1).get(x-1);
                break;
            case 17:
                valor = this.mapaPasos.get(y+1).get(x);
                break;
            case 18:
                valor = this.mapaPasos.get(y+1).get(x+1);
                break;
        }
        return valor;
    }
    
    /**
     * Actualiza el mapa de los pasos. Cada casilla contiene el número de veces
     * que se ha pasado por ella.
     * @author Fernando Ruiz Hernández
     * @param percepcion Objeto JSON con la percepción recibida
     */
    public void actualizarMapaPasos(JsonObject percepcion) {
        // Coordenadas de posición
        x = percepcion.get("gps").asObject().get("x").asInt();
        y = percepcion.get("gps").asObject().get("y").asInt();
        
        // Ajustar tamaño del mapa si es necesario
        int sizeNuevo = x + 3;
        if (sizeNuevo < y + 3)
            sizeNuevo = y + 3;
        if (sizeNuevo > mapaPasos.size())
            this.extenderMapaPasos(sizeNuevo);
        
        // Actualizar casillas
        int casilla_valor = mapaPasos.get(y).get(x);
        mapaPasos.get(y).set(x, casilla_valor+1);
    }
    
    
    /**
     * Extiende el tamaño del mapa de pasos. Se rellena con el valor 0.
     * 
     * @author Fernando Ruiz Hernández
     * @param sizeNuevo Tamaño nuevo
     */
    public void extenderMapaPasos(int sizeNuevo) {
        int size = mapaPasos.size();
        
        // Extender filas existentes
        for (int i=0; i<size; i++) {
            for (int j=size; j<sizeNuevo; j++) {
                mapaPasos.get(i).add(0);
            }
        }

        // Añadir nuevas filas
        ArrayList<Integer> fila;
        for (int i=size; i<sizeNuevo; i++) {
            fila = new ArrayList<>();
            for (int j=0; j<sizeNuevo; j++) {
                fila.add(0);
            }
            mapaPasos.add(fila);
        }
    }
    
    /**
     * Construye el mapaEscaner para teniendo como objetivo un cuadrante
     * @author Fernando Ruiz Hernández
     */
    public void construirEscanerCuadrante(int size) {
        
        int x_objetivo = (size * (1 + (cuadrante % 2)))/4;
        int y_objetivo = (size * (1 + (cuadrante / 2)))/4;;
        float distancia;
        
        // Añadir nuevas filas
        ArrayList<Float> fila;
        for (int i=0; i<size; i++) {
            fila = new ArrayList<>();
            for (int j=0; j<size; j++) {
                distancia = (float) Math.sqrt(
                    (x_objetivo - j) *  (x_objetivo - j) + 
                    (y_objetivo - i) *  (y_objetivo - i)
                );
                fila.add(distancia);
            }
            mapaEscaner.add(fila);
        }
    }
    
    /**
     * Se mueve hacia el objetivo.
     * 
     * @author Fernando Ruiz Hernández
     * 
     */
    public void irObjetivo(JsonObject percepcionJson){
        boolean salir = false;
        
        while (!salir){
            
            // *** Comprobar si tiene que hacer refuel
            if(bateria <= 1.0){
                //refuel();
            }else if (percepcionJson.get("radar").asArray().get(12).asInt() != 2){
                // Coordenadas de posición
                x = percepcionJson.get("gps").asObject().get("x").asInt();
                y = percepcionJson.get("gps").asObject().get("y").asInt();
                
                // Algoritmo de cálculo de movimiento
                int minimo = Integer.MAX_VALUE;
                
                TreeMap<Float,String> casillas = new TreeMap<Float,String>();
                
                // Calculamos mínimo
                if (percepcionJson.get("radar").asArray().get(6).asInt() != 1){
                    if(minimo >= this.getValorPasos(x, y,6)){
                        minimo = this.getValorPasos(x, y,6);
                    }
                }
                if (percepcionJson.get("radar").asArray().get(7).asInt() != 1){
                    if(minimo >= this.getValorPasos(x, y, 7)){
                        minimo = this.getValorPasos(x, y, 7);
                    }
                }
                if (percepcionJson.get("radar").asArray().get(8).asInt() != 1){
                    if(minimo >= this.getValorPasos(x, y, 8)){
                        minimo = this.getValorPasos(x, y, 8);
                    }
                }
                if (percepcionJson.get("radar").asArray().get(11).asInt() != 1){
                    if(minimo >= this.getValorPasos(x, y, 11)){
                        minimo = this.getValorPasos(x, y, 11);
                    }
                }
                if (percepcionJson.get("radar").asArray().get(13).asInt() != 1){
                    if(minimo >= this.getValorPasos(x, y, 13)){
                        minimo = this.getValorPasos(x, y, 13); 
                    }
                }
                if (percepcionJson.get("radar").asArray().get(16).asInt() != 1){
                    if(minimo >= this.getValorPasos(x, y, 16)){
                        minimo = this.getValorPasos(x, y, 16);
                    }
                }
                if (percepcionJson.get("radar").asArray().get(17).asInt() != 1){
                    if(minimo >= this.getValorPasos(x, y, 17)){
                        minimo = this.getValorPasos(x, y, 17);
                    }
                }
                if (percepcionJson.get("radar").asArray().get(18).asInt() != 1){
                    if(minimo >= this.getValorPasos(x, y, 18)){
                        minimo = this.getValorPasos(x, y, 18);
                    }
                }
                
                // Añadir casillas
                if (percepcionJson.get("radar").asArray().get(6).asInt() != 1){
                    if(minimo >= this.getValorPasos(x, y,6)){
                        casillas.put(getValorEscaner(x, y, 6), "NW");
                    }
                }
                if (percepcionJson.get("radar").asArray().get(7).asInt() != 1){
                    if(minimo >= this.getValorPasos(x, y, 7)){
                        casillas.put(getValorEscaner(x, y, 7), "N");
                    }
                }
                if (percepcionJson.get("radar").asArray().get(8).asInt() != 1){
                    if(minimo >= this.getValorPasos(x, y, 8)){
                        casillas.put(getValorEscaner(x, y, 8), "NE");
                    }
                }
                if (percepcionJson.get("radar").asArray().get(11).asInt() != 1){
                    if(minimo >= this.getValorPasos(x, y, 11)){
                        casillas.put(getValorEscaner(x, y, 11), "W");
                    }
                }
                if (percepcionJson.get("radar").asArray().get(13).asInt() != 1){
                    if(minimo >= this.getValorPasos(x, y, 13)){
                        casillas.put(getValorEscaner(x, y, 13), "E"); 
                    }
                }
                if (percepcionJson.get("radar").asArray().get(16).asInt() != 1){
                    if(minimo >= this.getValorPasos(x, y, 16)){
                        casillas.put(getValorEscaner(x, y, 16), "SW");
                    }
                }
                if (percepcionJson.get("radar").asArray().get(17).asInt() != 1){
                    if(minimo >= this.getValorPasos(x, y, 17)){
                        casillas.put(getValorEscaner(x, y, 17), "S");
                    }
                }
                if (percepcionJson.get("radar").asArray().get(18).asInt() != 1){
                    if(minimo >= this.getValorPasos(x, y, 18)){
                        casillas.put(getValorEscaner(x, y, 18), "SE");
                    }
                }
                
                Map.Entry<Float,String> casillaResultado = casillas.firstEntry();
                
                
                //this.moverse("move"+casillaResultado.getValue());
                
                bateria--;
            } else {
                // logout
                System.out.println("Hemos llegado al objetivo.");
                salir = true;
                //this.logout();
            }
        }
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
