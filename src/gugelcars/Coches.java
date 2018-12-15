/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package gugelcars;

import DBA.SuperAgent;
import com.eclipsesource.json.Json;
import com.eclipsesource.json.JsonArray;
import com.eclipsesource.json.JsonObject;
import es.upv.dsic.gti_ia.core.ACLMessage;
import es.upv.dsic.gti_ia.core.AgentID;
import java.util.ArrayList;
import java.util.Map;
import java.util.TreeMap;
import javafx.util.Pair;

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
    private boolean finRefuel = false;
    private boolean objetivoEncontrado = false;
    private boolean irCuadrante = true;
    private boolean escanerCuadranteCreado = false;
    private boolean escanerObjetivoCreado = false;
    private int x;
    private int y;
    private int xObjetivo;
    private int yObjetivo;
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
            System.out.print("ejecutado");
            this.comportamiento();
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
            
            // Comprobamos si somos volador
            if (mensajeCoordinador.get("capabilities").asObject().get("fly").asBoolean() == true)
                puedoVolar = true;
            
            // Terminamos de preparar el mensaje para el coordinador y se lo enviamos
            mensajeCoordinador.add("x", Json.parse(inbox.getContent()).asObject().get("result").asObject().get("x").asInt());
            mensajeCoordinador.add("y", Json.parse(inbox.getContent()).asObject().get("result").asObject().get("y").asInt());
            
            this.enviarMensaje(new AgentID(nombreCoordinador), mensajeCoordinador, null, ACLMessage.INFORM, null, this.getName());

            inbox = this.recibirMensaje(mensajesCoordinador);

            // Si recibimos un CANCEL, repetimos el bucle
            if (inbox.getPerformativeInt() != ACLMessage.CANCEL)
                salirSubscribe = true;
        }
        System.out.println("ohsi");
        
        cuadrante = Json.parse(inbox.getContent()).asObject().get("empieza").asInt();
        tamanoMapa = Json.parse(inbox.getContent()).asObject().get("tamanoMapa").asInt();
        
        json = new JsonObject();
        json.add("result", "OK");
        this.enviarMensaje(new AgentID(nombreCoordinador), json, null, ACLMessage.INFORM, null, this.getName());        
    }
    
    public ACLMessage recibirMensaje(MessageQueue cola) throws InterruptedException{
        while (cola.isEmpty()){this.sleep(500);}
        return (cola.Pop());
    }
    
     /**
     * 
     * Método principal de los coches, donde se decide que acción va a hacer
     * 
     * @author Adrian Martin Jaimez
     * @author Manuel Ros Rodríguez
     * 
     */
    public void comportamiento() throws InterruptedException{
        boolean salir = false;
        ACLMessage inbox = null;
        JsonObject json;
        
        while (!salir){
            // Pedimos percepción y la recibimos
            this.enviarMensaje(new AgentID("Cerastes"), null, null, ACLMessage.QUERY_REF, conversationID, null);
            inbox = this.recibirMensaje(mensajesServidor);
            JsonObject percepcionJson = Json.parse(inbox.getContent()).asObject();
            
            // Actualizamos nuestra posición
            x = percepcionJson.get("gps").asObject().get("x").asInt();
            y = percepcionJson.get("gps").asObject().get("y").asInt();
            
            /*
            trafico
            */
            
            // Si el coordinador nos manda un mensaje es porque alguien ha encontrado el objetivo
            if (!mensajesCoordinador.isEmpty()){
                inbox = mensajesCoordinador.Pop();
                xObjetivo = Json.parse(inbox.getContent()).asObject().get("objetivoEncontrado").asObject().get("x").asInt();
                yObjetivo = Json.parse(inbox.getContent()).asObject().get("objetivoEncontrado").asObject().get("y").asInt();
                
                if (irCuadrante)
                    irCuadrante = false;
                objetivoEncontrado = true;
            }
            
            // Comprobamos si hemos llegado al cuadrante
            if (cuadrante == 1 && x > 0 && x < tamanoMapa/2 && y > 0 && y < tamanoMapa/2){
                irCuadrante = false;
            } else if (cuadrante == 2 && x > 0 && x < tamanoMapa/2 && y > tamanoMapa/2 && y < tamanoMapa){
                irCuadrante = false;
            } else if (cuadrante == 3 && x > tamanoMapa/2 && x < tamanoMapa && y > 0 && y < tamanoMapa/2){
                irCuadrante = false;
            } else if (cuadrante == 4 && x > tamanoMapa/2 && x < tamanoMapa && y > tamanoMapa/2 && y < tamanoMapa){
                irCuadrante = false;
            }
            
            // Inicializamos bateria
            if (bateria == 0.0)
                bateria = Json.parse(inbox.getContent()).asObject().get("result").asObject().get("battery").asInt();
     
            // Comprobamos bateria
            if (bateria <= 1){
                json = new JsonObject();
                json.add("command","refuel");
                this.enviarMensaje(new AgentID("Cerastes"), json, null, ACLMessage.REQUEST, conversationID, null);
                inbox = this.recibirMensaje(mensajesServidor);
                
                if (inbox.getPerformativeInt() == ACLMessage.REFUSE)
                    finRefuel = true;
            }
            
            // Comprobamos si estamos en el objetivo, en ese caso se avisa al coordinador
            JsonArray radar = percepcionJson.get("result").asObject().get("radar").asArray();
            int posicionRadar = -1;
            for (int i=0; i<radar.size() && !objetivoEncontrado; i++){
                if (radar.get(i).asInt() == 3){
                    objetivoEncontrado = true;
                    posicionRadar = i;
                }
            }
                
            if (objetivoEncontrado){
                json = new JsonObject();
                JsonObject jsonCoordenadas = new JsonObject();
                Pair<Integer,Integer> coordenadas = this.coordenadasCasillaRadar(radar.size(), posicionRadar);
                xObjetivo = coordenadas.getKey();
                yObjetivo = coordenadas.getValue();
                jsonCoordenadas.add("x",xObjetivo);
                jsonCoordenadas.add("y",yObjetivo);
                json.add("objetivoEncontrado",jsonCoordenadas);
                
                this.enviarMensaje(new AgentID(nombreCoordinador), json, null, ACLMessage.INFORM, null, null); // CHECK**
                
                if (irCuadrante)
                    irCuadrante = false;
            }
            
            // Comprobamos en que modo estamos y nos movemos
            if ((finRefuel && bateria <= 1) || valoRadar(percepcionJson.get("radar").asArray(),12) == 2){
                json = new JsonObject();
                json.add("heTerminado","OK");
                this.enviarMensaje(new AgentID(nombreCoordinador), json, null, ACLMessage.INFORM, null, null);
                
                salir = true;
            } else {
                String movimiento;
                
                if (irCuadrante){
                    if (!escanerCuadranteCreado)
                        this.construirEscanerCuadrante(tamanoMapa); // *size de esto?
                    movimiento = this.irObjetivo(percepcionJson);
                } else if (!objetivoEncontrado){                  
                    movimiento = this.explorar();
                } else {
                    if (!escanerObjetivoCreado)
                        this.construirEscanerObjetivo(tamanoMapa); // *size de esto?  
                    movimiento = this.irObjetivo(percepcionJson);
                }
                
                json = new JsonObject();
                json.add("command",movimiento);
                this.enviarMensaje(new AgentID("Cerastes"), json, null, ACLMessage.REQUEST, conversationID, null);
                inbox = this.recibirMensaje(mensajesServidor);
                
                bateria--;
            }          
        }
        
        // El coche ha terminado y espera el cancel del coordinador
        inbox = this.recibirMensaje(mensajesCoordinador);
    }
    
    public String explorar(){
        String movimiento = "";
        
        if (puedoVolar){
            
        } else {
            
        }
        
        return (movimiento);
    }
    
    public void construirEscanerObjetivo(int size){
        
    }

     /**
     * 
     * @author Manuel Ros Rodríguez
     * 
     */    
    public Pair<Integer,Integer> coordenadasCasillaRadar(int tamanoRadar, int posicion){
        ArrayList<Pair<Integer,Integer>> radarPosiciones = new ArrayList<Pair<Integer,Integer>>();
        
        if (tamanoRadar <= 9){
            for (int i=0; i<3; i++){
                for (int j=0; j<3; j++){
                    int dif_x = (1-i)*(-1);
                    int dif_y = (1-j)*(-1);
                    radarPosiciones.add(new Pair(x+dif_x,y+dif_y));
                }
            }
        } else if (tamanoRadar <= 25){
            for (int i=0; i<5; i++){
                for (int j=0; j<5; j++){
                    int dif_x = (2-i)*(-1);
                    int dif_y = (2-j)*(-1);
                    radarPosiciones.add(new Pair(x+dif_x,y+dif_y));
                }
            }
        } else if (tamanoRadar <= 121){
            for (int i=0; i<11; i++){
                for (int j=0; j<11; j++){
                    int dif_x = (5-i)*(-1);
                    int dif_y = (5-j)*(-1);
                    radarPosiciones.add(new Pair(x+dif_x,y+dif_y));
                }
            }            
        }
        
        return (radarPosiciones.get(posicion));
    }
    
    // previamente a esto, vamos 
    //public void Comportamiento(){
        // pedimos percepcion y la recibimos
        
        
        // comprobamos percepcion para ver si alguien en radar, le mandamos mensaje
        // if (){} // alguien nos ha visto pero nosotros no a el y nos manda un mensaje, llamamos trafico
        // si mandamos mensaje somos activo si lo recibimos somos pasivo
        
        
        // ya hemos comprobado trafico, si no hemos entrado en trafico seguimos
        
        // comprobamos el modo que vamos a utilizar, si hay objetivo, si hemos llegado al cuadrante
        // avisar al coordinador si hemos encontrado el objetivo 
        
        // (refuel) y recibimos resultado
        // puede que no refuel, en ese mandar mensaje al coordinador y esperamos mensaje de el pa morirnos
        // en logout de este
        
        //  modo movimiento que toque, con booleanos, al movernos no olvidar comprobar si fuera del cuadrante
        // modo objetivo
        // algoritmo (move)
        
        // modo el otro
        // algoritmo (move)
        
        
        
    //}
    
    /**
     * @author Adrian Martin Jaimez 
     * 
     */
    // pasivo o activo 
    // esperamos su mensaje si activo lo mandamos si pasivo
    // hasta crisis resuelta, si no seguimos aqui
    public void trafico(int tipo) throws InterruptedException{
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
        
        int x_objetivo = (size * (1 + ((cuadrante-1) % 2)))/4;
        int y_objetivo = (size * (1 + ((cuadrante-1) / 2)))/4;
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
    public String irObjetivo(JsonObject percepcionJson){
        // Coordenadas de posición
        x = percepcionJson.get("gps").asObject().get("x").asInt();
        y = percepcionJson.get("gps").asObject().get("y").asInt();

        // Algoritmo de cálculo de movimiento
        int minimo = Integer.MAX_VALUE;

        TreeMap<Float,String> casillas = new TreeMap<Float,String>();

        if (puedoVolar){
            // Calculamos mínimo
            if (valoRadar(percepcionJson.get("radar").asArray(), 6) != 2 && valoRadar(percepcionJson.get("radar").asArray(), 6) != 4 && !this.comprobarSiCasillaFueraCuadrante(percepcionJson.get("radar").asArray(), 6)){
                if(minimo >= this.getValorPasos(x, y,6)){
                    minimo = this.getValorPasos(x, y,6);
                }
            }
            if (valoRadar(percepcionJson.get("radar").asArray(), 7) != 2 && valoRadar(percepcionJson.get("radar").asArray(), 7) != 4 && !this.comprobarSiCasillaFueraCuadrante(percepcionJson.get("radar").asArray(), 6)){
                if(minimo >= this.getValorPasos(x, y, 7)){
                    minimo = this.getValorPasos(x, y, 7);
                }
            }
            if (valoRadar(percepcionJson.get("radar").asArray(), 8) != 2 && valoRadar(percepcionJson.get("radar").asArray(), 8) != 4 && !this.comprobarSiCasillaFueraCuadrante(percepcionJson.get("radar").asArray(), 6)){
                if(minimo >= this.getValorPasos(x, y, 8)){
                    minimo = this.getValorPasos(x, y, 8);
                }
            }
            if (valoRadar(percepcionJson.get("radar").asArray(), 11) != 2 && valoRadar(percepcionJson.get("radar").asArray(), 11) != 4 && !this.comprobarSiCasillaFueraCuadrante(percepcionJson.get("radar").asArray(), 6)){
                if(minimo >= this.getValorPasos(x, y, 11)){
                    minimo = this.getValorPasos(x, y, 11);
                }
            }
            if (valoRadar(percepcionJson.get("radar").asArray(), 13) != 2 && valoRadar(percepcionJson.get("radar").asArray(), 13) != 4 && !this.comprobarSiCasillaFueraCuadrante(percepcionJson.get("radar").asArray(), 6)){
                if(minimo >= this.getValorPasos(x, y, 13)){
                    minimo = this.getValorPasos(x, y, 13); 
                }
            }
            if (valoRadar(percepcionJson.get("radar").asArray(), 16) != 2 && valoRadar(percepcionJson.get("radar").asArray(), 16) != 4 && !this.comprobarSiCasillaFueraCuadrante(percepcionJson.get("radar").asArray(), 6)){
                if(minimo >= this.getValorPasos(x, y, 16)){
                    minimo = this.getValorPasos(x, y, 16);
                }
            }
            if (valoRadar(percepcionJson.get("radar").asArray(), 17) != 2 && valoRadar(percepcionJson.get("radar").asArray(), 17) != 4 && !this.comprobarSiCasillaFueraCuadrante(percepcionJson.get("radar").asArray(), 6)){
                if(minimo >= this.getValorPasos(x, y, 17)){
                    minimo = this.getValorPasos(x, y, 17);
                }
            }
            if (valoRadar(percepcionJson.get("radar").asArray(), 18) != 2 && valoRadar(percepcionJson.get("radar").asArray(), 18) != 4 && !this.comprobarSiCasillaFueraCuadrante(percepcionJson.get("radar").asArray(), 6)){
                if(minimo >= this.getValorPasos(x, y, 18)){
                    minimo = this.getValorPasos(x, y, 18);
                }
            }

            // Añadir casillas
            if (valoRadar(percepcionJson.get("radar").asArray(), 6) != 2 && valoRadar(percepcionJson.get("radar").asArray(), 6) != 4 && !this.comprobarSiCasillaFueraCuadrante(percepcionJson.get("radar").asArray(), 6)){
                if(minimo >= this.getValorPasos(x, y,6)){
                    casillas.put(getValorEscaner(x, y, 6), "NW");
                }
            }
            if (valoRadar(percepcionJson.get("radar").asArray(), 7) != 2 && valoRadar(percepcionJson.get("radar").asArray(), 7) != 4 && !this.comprobarSiCasillaFueraCuadrante(percepcionJson.get("radar").asArray(), 6)){
                if(minimo >= this.getValorPasos(x, y, 7)){
                    casillas.put(getValorEscaner(x, y, 7), "N");
                }
            }
            if (valoRadar(percepcionJson.get("radar").asArray(), 8) != 2 && valoRadar(percepcionJson.get("radar").asArray(), 8) != 4 && !this.comprobarSiCasillaFueraCuadrante(percepcionJson.get("radar").asArray(), 6)){
                if(minimo >= this.getValorPasos(x, y, 8)){
                    casillas.put(getValorEscaner(x, y, 8), "NE");
                }
            }
            if (valoRadar(percepcionJson.get("radar").asArray(), 11) != 2 && valoRadar(percepcionJson.get("radar").asArray(), 11) != 4 && !this.comprobarSiCasillaFueraCuadrante(percepcionJson.get("radar").asArray(), 6)){
                if(minimo >= this.getValorPasos(x, y, 11)){
                    casillas.put(getValorEscaner(x, y, 11), "W");
                }
            }
            if (valoRadar(percepcionJson.get("radar").asArray(), 13) != 2 && valoRadar(percepcionJson.get("radar").asArray(), 13) != 4 && !this.comprobarSiCasillaFueraCuadrante(percepcionJson.get("radar").asArray(), 6)){
                if(minimo >= this.getValorPasos(x, y, 13)){
                    casillas.put(getValorEscaner(x, y, 13), "E"); 
                }
            }
            if (valoRadar(percepcionJson.get("radar").asArray(), 16) != 2 && valoRadar(percepcionJson.get("radar").asArray(), 16) != 4 && !this.comprobarSiCasillaFueraCuadrante(percepcionJson.get("radar").asArray(), 6)){
                if(minimo >= this.getValorPasos(x, y, 16)){
                    casillas.put(getValorEscaner(x, y, 16), "SW");
                }
            }
            if (valoRadar(percepcionJson.get("radar").asArray(), 17) != 2 && valoRadar(percepcionJson.get("radar").asArray(), 17) != 4 && !this.comprobarSiCasillaFueraCuadrante(percepcionJson.get("radar").asArray(), 6)){
                if(minimo >= this.getValorPasos(x, y, 17)){
                    casillas.put(getValorEscaner(x, y, 17), "S");
                }
            }
            if (valoRadar(percepcionJson.get("radar").asArray(), 18) != 2 && valoRadar(percepcionJson.get("radar").asArray(), 18) != 4 && !this.comprobarSiCasillaFueraCuadrante(percepcionJson.get("radar").asArray(), 6)){
                if(minimo >= this.getValorPasos(x, y, 18)){
                    casillas.put(getValorEscaner(x, y, 18), "SE");
                }
            }            
        } else {
            // Calculamos mínimo
            if (valoRadar(percepcionJson.get("radar").asArray(), 6) != 1 && valoRadar(percepcionJson.get("radar").asArray(), 6) != 2 && valoRadar(percepcionJson.get("radar").asArray(), 6) != 4 && !this.comprobarSiCasillaFueraCuadrante(percepcionJson.get("radar").asArray(), 6)){
                if(minimo >= this.getValorPasos(x, y,6)){
                    minimo = this.getValorPasos(x, y,6);
                }
            }
            if (valoRadar(percepcionJson.get("radar").asArray(), 7) != 1 && valoRadar(percepcionJson.get("radar").asArray(), 7) != 2 && valoRadar(percepcionJson.get("radar").asArray(), 7) != 4 && !this.comprobarSiCasillaFueraCuadrante(percepcionJson.get("radar").asArray(), 6)){
                if(minimo >= this.getValorPasos(x, y, 7)){
                    minimo = this.getValorPasos(x, y, 7);
                }
            }
            if (valoRadar(percepcionJson.get("radar").asArray(), 8) != 1 && valoRadar(percepcionJson.get("radar").asArray(), 8) != 2 && valoRadar(percepcionJson.get("radar").asArray(), 8) != 4 && !this.comprobarSiCasillaFueraCuadrante(percepcionJson.get("radar").asArray(), 6)){
                if(minimo >= this.getValorPasos(x, y, 8)){
                    minimo = this.getValorPasos(x, y, 8);
                }
            }
            if (valoRadar(percepcionJson.get("radar").asArray(), 11) != 1 && valoRadar(percepcionJson.get("radar").asArray(), 11) != 2 && valoRadar(percepcionJson.get("radar").asArray(), 11) != 4 && !this.comprobarSiCasillaFueraCuadrante(percepcionJson.get("radar").asArray(), 6)){
                if(minimo >= this.getValorPasos(x, y, 11)){
                    minimo = this.getValorPasos(x, y, 11);
                }
            }
            if (valoRadar(percepcionJson.get("radar").asArray(), 13) != 1 && valoRadar(percepcionJson.get("radar").asArray(), 13) != 2 && valoRadar(percepcionJson.get("radar").asArray(), 13) != 4 && !this.comprobarSiCasillaFueraCuadrante(percepcionJson.get("radar").asArray(), 6)){
                if(minimo >= this.getValorPasos(x, y, 13)){
                    minimo = this.getValorPasos(x, y, 13); 
                }
            }
            if (valoRadar(percepcionJson.get("radar").asArray(), 16) != 1 && valoRadar(percepcionJson.get("radar").asArray(), 16) != 2 && valoRadar(percepcionJson.get("radar").asArray(), 16) != 4 && !this.comprobarSiCasillaFueraCuadrante(percepcionJson.get("radar").asArray(), 6)){
                if(minimo >= this.getValorPasos(x, y, 16)){
                    minimo = this.getValorPasos(x, y, 16);
                }
            }
            if (valoRadar(percepcionJson.get("radar").asArray(), 17) != 1 && valoRadar(percepcionJson.get("radar").asArray(), 17) != 2 && valoRadar(percepcionJson.get("radar").asArray(), 17) != 4 && !this.comprobarSiCasillaFueraCuadrante(percepcionJson.get("radar").asArray(), 6)){
                if(minimo >= this.getValorPasos(x, y, 17)){
                    minimo = this.getValorPasos(x, y, 17);
                }
            }
            if (valoRadar(percepcionJson.get("radar").asArray(), 18) != 1 && valoRadar(percepcionJson.get("radar").asArray(), 18) != 2 && valoRadar(percepcionJson.get("radar").asArray(), 18) != 4 && !this.comprobarSiCasillaFueraCuadrante(percepcionJson.get("radar").asArray(), 6)){
                if(minimo >= this.getValorPasos(x, y, 18)){
                    minimo = this.getValorPasos(x, y, 18);
                }
            }

            // Añadir casillas
            if (valoRadar(percepcionJson.get("radar").asArray(), 6) != 1 && valoRadar(percepcionJson.get("radar").asArray(), 6) != 2 && valoRadar(percepcionJson.get("radar").asArray(), 6) != 4 && !this.comprobarSiCasillaFueraCuadrante(percepcionJson.get("radar").asArray(), 6)){
                if(minimo >= this.getValorPasos(x, y,6)){
                    casillas.put(getValorEscaner(x, y, 6), "NW");
                }
            }
            if (valoRadar(percepcionJson.get("radar").asArray(), 7) != 1 && valoRadar(percepcionJson.get("radar").asArray(), 7) != 2 && valoRadar(percepcionJson.get("radar").asArray(), 7) != 4 && !this.comprobarSiCasillaFueraCuadrante(percepcionJson.get("radar").asArray(), 6)){
                if(minimo >= this.getValorPasos(x, y, 7)){
                    casillas.put(getValorEscaner(x, y, 7), "N");
                }
            }
            if (valoRadar(percepcionJson.get("radar").asArray(), 8) != 1 && valoRadar(percepcionJson.get("radar").asArray(), 8) != 2 && valoRadar(percepcionJson.get("radar").asArray(), 8) != 4 && !this.comprobarSiCasillaFueraCuadrante(percepcionJson.get("radar").asArray(), 6)){
                if(minimo >= this.getValorPasos(x, y, 8)){
                    casillas.put(getValorEscaner(x, y, 8), "NE");
                }
            }
            if (valoRadar(percepcionJson.get("radar").asArray(), 11) != 1 && valoRadar(percepcionJson.get("radar").asArray(), 11) != 2 && valoRadar(percepcionJson.get("radar").asArray(), 11) != 4 && !this.comprobarSiCasillaFueraCuadrante(percepcionJson.get("radar").asArray(), 6)){
                if(minimo >= this.getValorPasos(x, y, 11)){
                    casillas.put(getValorEscaner(x, y, 11), "W");
                }
            }
            if (valoRadar(percepcionJson.get("radar").asArray(), 13) != 1 && valoRadar(percepcionJson.get("radar").asArray(), 13) != 2 && valoRadar(percepcionJson.get("radar").asArray(), 13) != 4 && !this.comprobarSiCasillaFueraCuadrante(percepcionJson.get("radar").asArray(), 6)){
                if(minimo >= this.getValorPasos(x, y, 13)){
                    casillas.put(getValorEscaner(x, y, 13), "E"); 
                }
            }
            if (valoRadar(percepcionJson.get("radar").asArray(), 16) != 1 && valoRadar(percepcionJson.get("radar").asArray(), 16) != 2 && valoRadar(percepcionJson.get("radar").asArray(), 16) != 4 && !this.comprobarSiCasillaFueraCuadrante(percepcionJson.get("radar").asArray(), 6)){
                if(minimo >= this.getValorPasos(x, y, 16)){
                    casillas.put(getValorEscaner(x, y, 16), "SW");
                }
            }
            if (valoRadar(percepcionJson.get("radar").asArray(), 17) != 1 && valoRadar(percepcionJson.get("radar").asArray(), 17) != 2 && valoRadar(percepcionJson.get("radar").asArray(), 17) != 4 && !this.comprobarSiCasillaFueraCuadrante(percepcionJson.get("radar").asArray(), 6)){
                if(minimo >= this.getValorPasos(x, y, 17)){
                    casillas.put(getValorEscaner(x, y, 17), "S");
                }
            }
            if (valoRadar(percepcionJson.get("radar").asArray(), 18) != 1 && valoRadar(percepcionJson.get("radar").asArray(), 18) != 2 && valoRadar(percepcionJson.get("radar").asArray(), 18) != 4 && !this.comprobarSiCasillaFueraCuadrante(percepcionJson.get("radar").asArray(), 6)){
                if(minimo >= this.getValorPasos(x, y, 18)){
                    casillas.put(getValorEscaner(x, y, 18), "SE");
                }
            }
        }

        Map.Entry<Float,String> casillaResultado = casillas.firstEntry();
                
        return ("move"+casillaResultado.getValue());        
    }
    
     /**
     * 
     * @author Manuel Ros Rodríguez
     * 
     */  
    public boolean comprobarSiCasillaFueraCuadrante(JsonArray radar, int posicion){
        boolean fuera = true;
        
        Pair<Integer,Integer> coordenadas = this.coordenadasCasillaRadar(radar.size(), posicion);
        
        if (cuadrante == 1 && coordenadas.getKey() > 0 && coordenadas.getKey() < tamanoMapa/2 && coordenadas.getValue() > 0 && coordenadas.getValue() < tamanoMapa/2)
            fuera = false;
        else if (cuadrante == 2 && coordenadas.getKey() > 0 && coordenadas.getKey() < tamanoMapa/2 && coordenadas.getValue() > tamanoMapa/2 && coordenadas.getValue() < tamanoMapa)
            fuera = false;
        else if (cuadrante == 3 && coordenadas.getKey() > tamanoMapa/2 && coordenadas.getKey() < tamanoMapa && coordenadas.getValue() > 0 && coordenadas.getValue() < tamanoMapa/2)
            fuera = false;
        else if (cuadrante == 4 && coordenadas.getKey() > tamanoMapa/2 && coordenadas.getKey() < tamanoMapa && coordenadas.getValue() > tamanoMapa/2 && coordenadas.getValue() < tamanoMapa)
            fuera = false;
        
        return (fuera);
    }

    /**
    *
    * Le pasamos la posicion del radar que queremos suponiendo que el radar es 5x5 y sea una casilla que rodeé
    * a nuestro coche
    * 
    * @author Manuel Ros Rodríguez
    */    
    public int valoRadar(JsonArray radar, int posicion){
        int valorCasilla = -1;
        
        int tamanoRadar = radar.size();
        
        if (tamanoRadar <= 9){
            if (posicion == 6){
                valorCasilla = radar.asArray().get(0).asInt();
            } else if (posicion == 7){
                valorCasilla = radar.asArray().get(1).asInt();
            } else if (posicion == 8){
                valorCasilla = radar.asArray().get(2).asInt();
            } else if (posicion == 11){
                valorCasilla = radar.asArray().get(3).asInt();
            } else if (posicion == 13){
                valorCasilla = radar.asArray().get(5).asInt();
            } else if (posicion == 16){
                valorCasilla = radar.asArray().get(6).asInt();
            } else if (posicion == 17){
                valorCasilla = radar.asArray().get(7).asInt();
            } else if (posicion == 18){
                valorCasilla = radar.asArray().get(8).asInt();
            }
        } else if (tamanoRadar <= 25){
            valorCasilla = radar.asArray().get(posicion).asInt();
        } else if (tamanoRadar <= 121){
            if (posicion == 6){
                valorCasilla = radar.asArray().get(48).asInt();
            } else if (posicion == 7){
                valorCasilla = radar.asArray().get(49).asInt();
            } else if (posicion == 8){
                valorCasilla = radar.asArray().get(50).asInt();
            } else if (posicion == 11){
                valorCasilla = radar.asArray().get(59).asInt();
            } else if (posicion == 13){
                valorCasilla = radar.asArray().get(61).asInt();
            } else if (posicion == 16){
                valorCasilla = radar.asArray().get(70).asInt();
            } else if (posicion == 17){
                valorCasilla = radar.asArray().get(71).asInt();
            } else if (posicion == 18){
                valorCasilla = radar.asArray().get(72).asInt();
            }         
        }
        
        return (valorCasilla);
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
