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
    private MessageQueue mensajesCoordinadorObjetivo;
    private MessageQueue mensajesCoches;
    private MessageQueue mensajesServidor;
    private String conversationID;
    private String replyWith;
    private int tamanoMapa;
    private int cuadrante;
    private boolean puedoVolar;
    private int prioridad;
    private int consumo;
    
    private double bateria = 0.0;
    private boolean finRefuel = false;
    private boolean objetivoEncontrado = false;
    private boolean irCuadrante = true;
    private boolean escanerCuadranteCreado = false;
    private boolean escanerObjetivoCreado = false;
    private boolean ningunMovimiento = false;
    private boolean soyUnoConElObjetivo = false;
    private int x;
    private int y;
    private int xObjetivo;
    private int yObjetivo;
    private int xObjetivoCuadrante;
    private int yObjetivoCuadrante;
    private ArrayList<ArrayList<Integer>> mapaPasos = new ArrayList<>();
    private ArrayList<ArrayList<Float>> mapaEscaner = new ArrayList<>();
    
    private int x_izq_barrido;
    private int x_der_barrido;
    private int y_barrido;
    private int y_final;
    private boolean barrido_preparado = false;
    private boolean barrido_derecho = true;
    private boolean barrido_vertical = false;
    
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
        mensajesCoordinadorObjetivo = new MessageQueue(30);
        conversationID = "";
        replyWith = "";
        tamanoMapa = 0;
        cuadrante = 0;
        puedoVolar = false;
        prioridad = 0;
        consumo = 0;
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
                if (msg.getContent().contains("objetivoEncontrado")){
                    mensajesCoordinadorObjetivo.Push(msg);
                } else {
                    mensajesCoordinador.Push(msg);
                }
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
            this.comportamiento();
        } catch (InterruptedException e){
            System.out.println("Error al sacar mensaje de la cola");
        }
    }
    
    /**
    *
    * Inicia sesión en el servidor y se inicializa
    * 
    * @author Manuel Ros Rodríguez
    */
    public void login() throws InterruptedException{
        boolean salirSubscribe = false;
        ACLMessage inbox = null;
        JsonObject json;
        
        while (!salirSubscribe){
            puedoVolar = false;
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
            
            
            // Obtenemos el consumo
            consumo = mensajeCoordinador.get("capabilities").asObject().get("fuelrate").asInt();
            
            // Terminamos de preparar el mensaje para el coordinador y se lo enviamos
            mensajeCoordinador.add("x", Json.parse(inbox.getContent()).asObject().get("result").asObject().get("x").asInt());
            mensajeCoordinador.add("y", Json.parse(inbox.getContent()).asObject().get("result").asObject().get("y").asInt());
            
            this.enviarMensaje(new AgentID(nombreCoordinador), mensajeCoordinador, null, ACLMessage.INFORM, null, this.getName());

            inbox = this.recibirMensaje(mensajesCoordinador);

            // Si recibimos un CANCEL, repetimos el bucle
            if (inbox.getPerformativeInt() != ACLMessage.CANCEL)
                salirSubscribe = true;
        }
        
        cuadrante = Json.parse(inbox.getContent()).asObject().get("empieza").asInt();
        tamanoMapa = Json.parse(inbox.getContent()).asObject().get("tamanoMapa").asInt();
        
        json = new JsonObject();
        json.add("result", "OK");
        this.enviarMensaje(new AgentID(nombreCoordinador), json, null, ACLMessage.INFORM, null, this.getName()); 
    }
    
    public ACLMessage recibirMensaje(MessageQueue cola) throws InterruptedException{
        while (cola.isEmpty()){this.sleep(1);}
        return (cola.Pop());
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
     * Método principal de los coches, donde se decide que acción va a realizar
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
            this.enviarMensaje(new AgentID("Cerastes"), null, "", ACLMessage.QUERY_REF, conversationID, replyWith);
            inbox = this.recibirMensaje(mensajesServidor);
            replyWith = inbox.getReplyWith();
            JsonObject percepcionJson = Json.parse(inbox.getContent()).asObject().get("result").asObject();
            // Actualizamos nuestra posición
            x = percepcionJson.get("x").asInt();
            y = percepcionJson.get("y").asInt();
            // Actualizamos el mapa de los pasos
            this.actualizarMapaPasos(percepcionJson);
            // Si el coordinador nos manda un mensaje es porque alguien ha encontrado el objetivo
            if (!mensajesCoordinadorObjetivo.isEmpty()){
                inbox = mensajesCoordinadorObjetivo.Pop();
                xObjetivo = Json.parse(inbox.getContent()).asObject().get("objetivoEncontrado").asObject().get("x").asInt();
                yObjetivo = Json.parse(inbox.getContent()).asObject().get("objetivoEncontrado").asObject().get("y").asInt();
                if (irCuadrante)
                    irCuadrante = false;
                objetivoEncontrado = true;
            }
            // Comprobamos si hemos llegado al cuadrante
            if (!puedoVolar && cuadrante == 1 && y >= 0 && y < tamanoMapa/2 && x >= 0 && x < tamanoMapa/2){
                irCuadrante = false;
            } else if (!puedoVolar && cuadrante == 2 && y >= 0 && y < tamanoMapa/2 && x >= tamanoMapa/2 && x < tamanoMapa){
                irCuadrante = false;
            } else if (!puedoVolar && cuadrante == 3 && y >= tamanoMapa/2 && y < tamanoMapa && x >= 0 && x < tamanoMapa/2){
                irCuadrante = false;
            } else if (!puedoVolar && cuadrante == 4 && y >= tamanoMapa/2 && y < tamanoMapa && x >= tamanoMapa/2 && x < tamanoMapa){
                irCuadrante = false;
            } else if (puedoVolar && x == xObjetivoCuadrante && y == yObjetivoCuadrante){
                irCuadrante = false;
            }
            // Inicializamos bateria
            if (bateria == 0.0)
                bateria = Json.parse(inbox.getContent()).asObject().get("result").asObject().get("battery").asInt();
            // Comprobamos bateria
            if (bateria <= consumo){
                json = new JsonObject();
                json.add("command","refuel");
                this.enviarMensaje(new AgentID("Cerastes"), json, null, ACLMessage.REQUEST, conversationID, replyWith);
                inbox = this.recibirMensaje(mensajesServidor);
                replyWith = inbox.getReplyWith();
                
                if (inbox.getPerformativeInt() == ACLMessage.REFUSE)
                    finRefuel = true;
            }
            
            // Comprobamos si estamos en el objetivo, en ese caso se avisa al coordinador
            JsonArray radar = percepcionJson.get("sensor").asArray();
            int posicionRadar = -1;
            if (!objetivoEncontrado){
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
            }
            
            if (percepcionJson.get("goal").asBoolean()){
                soyUnoConElObjetivo = true;
                System.out.println(this.getName()+" Estoy en el objetivo");
            }
            
            // Comprobamos en que modo estamos y nos movemos
            if ((finRefuel && bateria <= consumo) || soyUnoConElObjetivo){
                json = new JsonObject();
                if (soyUnoConElObjetivo)
                    json.add("heTerminado","si");
                else
                    json.add("heTerminado","no");
                this.enviarMensaje(new AgentID(nombreCoordinador), json, null, ACLMessage.INFORM, null, null);
                salir = true;
            } else {
                String movimiento;
                
                if (irCuadrante){
                    if (!escanerCuadranteCreado)
                        this.construirEscanerCuadrante(tamanoMapa); // *size de esto?
                    movimiento = this.irObjetivo(percepcionJson);
                } else if (!objetivoEncontrado){
                    movimiento = this.explorar(percepcionJson);
                } else {
                    if (!escanerObjetivoCreado)
                        this.construirEscanerObjetivo(tamanoMapa); // *size de esto? 
                    movimiento = this.irObjetivo(percepcionJson);
                }
                
                if (!movimiento.equals("ninguno")){
                    json = new JsonObject();
                    json.add("command",movimiento);
                    this.enviarMensaje(new AgentID("Cerastes"), json, null, ACLMessage.REQUEST, conversationID, replyWith);
                    inbox = this.recibirMensaje(mensajesServidor);
                    replyWith = inbox.getReplyWith();

                    bateria = bateria - consumo;
                }
            }          
        }
        
        // El coche ha terminado y espera el cancel del coordinador
        inbox = this.recibirMensaje(mensajesCoordinador);
    }

     /**
     * Algoritmo de exploración utilizado por los coches no voladores.
     * En caso de ser volador desde aquí se llama al algoritmo de exploración para coches voladores
     * 
     * @author Manuel Ros Rodríguez
     * @author Alejandro García
     * 
     */     
    public String explorar(JsonObject percepcionJson) throws InterruptedException{ 
        String movimiento = "";
        
        if (puedoVolar){
            movimiento = this.explorarVolar(percepcionJson);
        } else {
            TreeMap<Float,String> casillas = new TreeMap<Float,String>();
            
            if (comprobarCasillaPermitida(percepcionJson, 6) && !this.comprobarSiCasillaFueraCuadrante(percepcionJson.get("sensor").asArray(), 6)){
                casillas.put((float) getValorPasos(x, y, 6), "NW");
            }
            if (comprobarCasillaPermitida(percepcionJson, 7) && !this.comprobarSiCasillaFueraCuadrante(percepcionJson.get("sensor").asArray(), 7)){
                casillas.put((float) getValorPasos(x, y, 7), "N");
            }
            if (comprobarCasillaPermitida(percepcionJson, 8) && !this.comprobarSiCasillaFueraCuadrante(percepcionJson.get("sensor").asArray(), 8)){
                casillas.put((float) getValorPasos(x, y, 8), "NE");
            }
            if (comprobarCasillaPermitida(percepcionJson, 11) && !this.comprobarSiCasillaFueraCuadrante(percepcionJson.get("sensor").asArray(), 11)){
                casillas.put((float) getValorPasos(x, y, 11), "W");
            }
            if (comprobarCasillaPermitida(percepcionJson, 13) && !this.comprobarSiCasillaFueraCuadrante(percepcionJson.get("sensor").asArray(), 13)){
                casillas.put((float) getValorPasos(x, y, 13), "E");
            }
            if (comprobarCasillaPermitida(percepcionJson, 16) && !this.comprobarSiCasillaFueraCuadrante(percepcionJson.get("sensor").asArray(), 16)){
                casillas.put((float) getValorPasos(x, y, 16), "SW");
            }
            if (comprobarCasillaPermitida(percepcionJson, 17) && !this.comprobarSiCasillaFueraCuadrante(percepcionJson.get("sensor").asArray(), 17)){
                casillas.put((float) getValorPasos(x, y, 17), "S");
            }
            if (comprobarCasillaPermitida(percepcionJson, 18) && !this.comprobarSiCasillaFueraCuadrante(percepcionJson.get("sensor").asArray(), 18)){
                casillas.put((float) getValorPasos(x, y, 18), "SE");
            }         
        
            movimiento = this.trafico(casillas, percepcionJson.get("sensor").asArray().size());
            if (!movimiento.equals("ninguno"))
                movimiento = "move"+movimiento;
        }
        
        return (movimiento);
    }

     /**
     * Algoritmo de exploración utilizado por el coche volador
     * 
     * @author Fernando Ruiz Hernández
     * 
     */  
    public String explorarVolar(JsonObject percepcionJson) throws InterruptedException{
        boolean fin = false;
        String movimiento = "";
        if (barrido_vertical) {
            if (y >= y_barrido) {
                barrido_vertical = false;
                barrido_derecho = !barrido_derecho;
                barrido_preparado = false;
            }
            else if (valoRadar(percepcionJson.get("sensor").asArray(), 17) == 2) {
                fin = true;
            }
        }
        else {
            if (barrido_derecho) {
                if (x >= x_der_barrido || valoRadar(percepcionJson.get("sensor").asArray(), 13) == 2) {
                    barrido_vertical = true;
                    y_barrido += 3;
                    barrido_preparado = false;
                }
            }
            else {
                if (x <= x_izq_barrido || valoRadar(percepcionJson.get("sensor").asArray(), 11) == 2) {
                    barrido_vertical = true;
                    y_barrido += 3;
                    barrido_preparado = false;
                }
            }
        }
        if (y_barrido>y_final) {
            fin = true;
        }
        if (fin) {
            return ("ninguno");
        }
        if (!barrido_preparado) {
            int x_objetivo;
            int y_objetivo = y_barrido;
            if (barrido_derecho)
                x_objetivo = x_der_barrido;
            else
                x_objetivo = x_izq_barrido;
            construirEscaner(x_objetivo, y_objetivo, tamanoMapa);
            barrido_preparado = true;
        }
        movimiento = irObjetivo(percepcionJson);        
        return (movimiento);
    }
    
     /**
     * Devuelve las coordenadas correspondientes a la posición del radar que le demos
     * 
     * @author Fernando Ruiz Hernández
     * @author Manuel Ros Rodríguez
     * 
     */    
    public Pair<Integer,Integer> coordenadasCasillaRadar(int tamanoRadar, int posicion){
        int casilla_x = x;
        int casilla_y = y;
        
        if (tamanoRadar <= 9){
            casilla_x = x - 1 + posicion%3;
            casilla_y = y - 1 + posicion/3;
        } else if (tamanoRadar <= 25){
            casilla_x = x - 2 + posicion%5;
            casilla_y = y - 2 + posicion/5;
        } else if (tamanoRadar <= 121){
            casilla_x = x - 5 + posicion%11;
            casilla_y = y - 5 + posicion/11; 
        }
        
        return new Pair(casilla_x, casilla_y);
    }
    
    /**
     * Método que se encarga de comunicar el movimiento elegido al servidor y elegir otro si fuese necesario
     * 
     * @author Manuel Ros Rodríguez
     * 
     */ 
    public String trafico(TreeMap<Float,String> casillas, int tamanoRadar) throws InterruptedException{
        boolean salir = false;
        String puntoCardinal = "";
        
        while (!salir){
            if (casillas.size() == 0){
                puntoCardinal = "ninguno";
                this.enviarMensaje(new AgentID(nombreCoordinador), null, "ningunMovimiento", ACLMessage.INFORM, null, null);
                salir = true;
            } else {
                Map.Entry<Float,String> casillaResultado = casillas.firstEntry();
                casillas.remove(casillaResultado.getKey());

                puntoCardinal = casillaResultado.getValue();

                int posicion = this.dePCardinalACasilla(puntoCardinal, tamanoRadar);

                Pair<Integer,Integer> coordenadas = this.coordenadasCasillaRadar(tamanoRadar, posicion);
                // Le preguntamos al coordinador si podemos movernos a esa posición
                JsonObject json = new JsonObject();
                JsonObject jsonInterno = new JsonObject();
                jsonInterno.add("x",coordenadas.getKey());
                jsonInterno.add("y",coordenadas.getValue());
                jsonInterno.add("opciones",casillas.size());
                json.add("puedoMoverme",jsonInterno);
                this.enviarMensaje(new AgentID(nombreCoordinador), json, null, ACLMessage.QUERY_IF, null, null);

                ACLMessage inbox = this.recibirMensaje(mensajesCoordinador);
                // Nos permite hacer el movimiento
                if (Json.parse(inbox.getContent()).asObject().get("result").asString().equals("si"))
                    salir = true;
            }
        }
        

        return (puntoCardinal);
    }
    
     /**
     * Convierte un String que contiene un punto cardinal a la posición del radar que correspondería
     * 
     * @author Manuel Ros Rodríguez
     * 
     */ 
    public int dePCardinalACasilla(String puntoCardinal, int tamanoRadar){
        int resultado = -1;
                
        if (tamanoRadar <= 9){
            switch (puntoCardinal){
                case "NW":
                    resultado = 0;
                    break;
                case "N":
                    resultado = 1;
                    break;
                case "NE":
                    resultado = 2;
                    break;
                case "W":
                    resultado = 3;
                    break;
                case "E":
                    resultado = 5;
                    break;
                case "SW":
                    resultado = 6;
                    break;
                case "S":
                    resultado = 7;
                    break;
                case "SE":
                    resultado = 8;
                    break;  
            }            
        } else if (tamanoRadar <= 25){
            switch (puntoCardinal){
                case "NW":
                    resultado = 6;
                    break;
                case "N":
                    resultado = 7;
                    break;
                case "NE":
                    resultado = 8;
                    break;
                case "W":
                    resultado = 11;
                    break;
                case "E":
                    resultado = 13;
                    break;
                case "SW":
                    resultado = 16;
                    break;
                case "S":
                    resultado = 17;
                    break;
                case "SE":
                    resultado = 18;
                    break;  
            }
        } else if (tamanoRadar <= 121){
            switch (puntoCardinal){
                case "NW":
                    resultado = 48;
                    break;
                case "N":
                    resultado = 49;
                    break;
                case "NE":
                    resultado = 50;
                    break;
                case "W":
                    resultado = 59;
                    break;
                case "E":
                    resultado = 61;
                    break;
                case "SW":
                    resultado = 70;
                    break;
                case "S":
                    resultado = 71;
                    break;
                case "SE":
                    resultado = 72;
                    break;  
            }
        }
        
        return (resultado);
    }
    
    /**
     * Obtiene el valor del escaner de una casilla (5x5)
     * @author Fernando Ruiz Hernández
     */
    
    public float getValorEscaner(int x, int y, int casilla) {
        int casilla_x = x - 2 + casilla%5;
        int casilla_y = y - 2 + casilla/5;
        float valor;
        if (casilla_x < 0 || casilla_y<0 || casilla_x>=mapaEscaner.size() || casilla_y>=mapaEscaner.size())
            valor = Float.MAX_VALUE;
        else
            valor = this.mapaEscaner.get(casilla_y).get(casilla_x);
        return valor;
    }
    
    /**
     * Obtiene el valor de pasos de una casilla (5x5)
     * @author Fernando Ruiz Hernández
     */
    
    public int getValorPasos(int x, int y, int casilla) {
        int casilla_x = x - 2 + casilla%5;
        int casilla_y = y - 2 + casilla/5;
        int valor;
        if (casilla_x < 0 || casilla_y<0 || casilla_x>=mapaPasos.size() || casilla_y>=mapaPasos.size())
            valor = Integer.MAX_VALUE;
        else
            valor = this.mapaPasos.get(casilla_y).get(casilla_x);
        return valor;
    }
    
    /**
     * Actualiza el mapa de los pasos. Cada casilla contiene el número de veces
     * que se ha pasado por ella.
     * @author Fernando Ruiz Hernández
     * @param percepcion Objeto JSON con la percepción recibida
     */
    public void actualizarMapaPasos(JsonObject percepcion) {       
        // Ajustar tamaño del mapa si es necesario
        int sizeNuevo = x + 3;
        if (sizeNuevo < y + 3)
            sizeNuevo = y + 3;
        if (sizeNuevo > mapaPasos.size())
            this.extenderMapaPasos(sizeNuevo);
        
        // Actualizar casillas
        int casilla_valor = mapaPasos.get(y).get(x);
        mapaPasos.get(y).set(x, casilla_valor+1);
        
        int tamano_sensor = 3;
        
        if (percepcion.get("sensor").asArray().size() <= 9)
            tamano_sensor = 3;
        else if (percepcion.get("sensor").asArray().size() <= 25)
            tamano_sensor = 5;
        else if (percepcion.get("sensor").asArray().size() <= 121)
            tamano_sensor = 11;
        int casilla_x;
        int casilla_y;
        for (int i=0; i<tamano_sensor; i++)
            for (int j=0; j<tamano_sensor; j++) {
                casilla_x = x + i - (tamano_sensor/2);
                casilla_y = y + j - (tamano_sensor/2);
                if (casilla_x >= 0 && casilla_x <mapaPasos.size() && casilla_y >= 0 && casilla_y <mapaPasos.size()){
                    casilla_valor = mapaPasos.get(casilla_y).get(casilla_x);
                    mapaPasos.get(casilla_y).set(casilla_x, casilla_valor+1);
                }
                    
            }
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
     * Construye el mapaEscaner hacia una casilla arbitraria
     * @author Fernando Ruiz Hernández
     */
    public void construirEscaner(int x_objetivo, int y_objetivo, int size) {
        mapaEscaner.clear();
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
     * Construye el mapaEscaner teniendo como objetivo su cuadrante
     * @author Fernando Ruiz Hernández
     */
    public void construirEscanerCuadrante(int size) {
        if (puedoVolar) {
            xObjetivoCuadrante = ((cuadrante-1) % 2) * (size/2);
            yObjetivoCuadrante = ((cuadrante-1) / 2) * (size/2);
            x_izq_barrido = xObjetivoCuadrante;
            x_der_barrido = xObjetivoCuadrante + size/2 - 1;
            y_barrido = yObjetivoCuadrante;
            y_final = yObjetivoCuadrante + size/2 - 1;
        }
        else {
            xObjetivoCuadrante = (size * (((cuadrante-1) % 2)))/2;
            yObjetivoCuadrante = (size * (((cuadrante-1) / 2)))/2;
        }
        construirEscaner(xObjetivoCuadrante, yObjetivoCuadrante, size);
    }
    
    /**
     * Construye el mapaEscaner hacia el objetivo real
     * @author Fernando Ruiz Hernández
     */
    public void construirEscanerObjetivo(int size) {
        construirEscaner(xObjetivo, yObjetivo, size);
    }
    
    /**
     * Comprueba si puede ir a la casilla.
     * 
     * @author Fernando Ruiz Hernández
     * 
     */
    public boolean comprobarCasillaPermitida(JsonObject percepcionJson, int casilla) {
        // Obstáculo
        if (!puedoVolar && (valoRadar(percepcionJson.get("sensor").asArray(), casilla) == 1))
            return false;
        // Borde del mundo
        if (valoRadar(percepcionJson.get("sensor").asArray(), casilla) == 2)
            return false;
        // Otro vehículo
        if (valoRadar(percepcionJson.get("sensor").asArray(), casilla) == 4)
            return false;
        // Permitido
        return true;
                  
    }
    
    /**
     * Se mueve hacia el objetivo.
     * 
     * @author Fernando Ruiz Hernández
     * 
     */
    public String irObjetivo(JsonObject percepcionJson) throws InterruptedException{
        // Algoritmo de cálculo de movimiento
        int minimo = Integer.MAX_VALUE;

        TreeMap<Float,String> casillas = new TreeMap<Float,String>();

        // Calculamos mínimo
        if (comprobarCasillaPermitida(percepcionJson, 6)){
            if(minimo >= this.getValorPasos(x, y,6)){
                minimo = this.getValorPasos(x, y,6);
            }
        }
        if (comprobarCasillaPermitida(percepcionJson, 7)){
            if(minimo >= this.getValorPasos(x, y, 7)){
                minimo = this.getValorPasos(x, y, 7);
            }
        }
        if (comprobarCasillaPermitida(percepcionJson, 8)){
            if(minimo >= this.getValorPasos(x, y, 8)){
                minimo = this.getValorPasos(x, y, 8);
            }
        }
        if (comprobarCasillaPermitida(percepcionJson, 11)){
            if(minimo >= this.getValorPasos(x, y, 11)){
                minimo = this.getValorPasos(x, y, 11);
            }
        }
        if (comprobarCasillaPermitida(percepcionJson, 13)){
            if(minimo >= this.getValorPasos(x, y, 13)){
                minimo = this.getValorPasos(x, y, 13); 
            }
        }
        if (comprobarCasillaPermitida(percepcionJson, 16)){
            if(minimo >= this.getValorPasos(x, y, 16)){
                minimo = this.getValorPasos(x, y, 16);
            }
        }
        if (comprobarCasillaPermitida(percepcionJson, 17)){
            if(minimo >= this.getValorPasos(x, y, 17)){
                minimo = this.getValorPasos(x, y, 17);
            }
        }
        if (comprobarCasillaPermitida(percepcionJson, 18)){
            if(minimo >= this.getValorPasos(x, y, 18)){
                minimo = this.getValorPasos(x, y, 18);
            }
        }
        
        // Añadir casillas
        if (comprobarCasillaPermitida(percepcionJson, 6)){
            if(minimo >= this.getValorPasos(x, y,6)){
                casillas.put(getValorEscaner(x, y, 6), "NW");
            }
        }
        if (comprobarCasillaPermitida(percepcionJson, 7)){
            if(minimo >= this.getValorPasos(x, y, 7)){
                casillas.put(getValorEscaner(x, y, 7), "N");
            }
        }
        if (comprobarCasillaPermitida(percepcionJson, 8)){
            if(minimo >= this.getValorPasos(x, y, 8)){
                casillas.put(getValorEscaner(x, y, 8), "NE");
            }
        }
        if (comprobarCasillaPermitida(percepcionJson, 11)){
            if(minimo >= this.getValorPasos(x, y, 11)){
                casillas.put(getValorEscaner(x, y, 11), "W");
            }
        }
        if (comprobarCasillaPermitida(percepcionJson, 13)){
            if(minimo >= this.getValorPasos(x, y, 13)){
                casillas.put(getValorEscaner(x, y, 13), "E"); 
            }
        }
        if (comprobarCasillaPermitida(percepcionJson, 16)){
            if(minimo >= this.getValorPasos(x, y, 16)){
                casillas.put(getValorEscaner(x, y, 16), "SW");
            }
        }
        if (comprobarCasillaPermitida(percepcionJson, 17)){
            if(minimo >= this.getValorPasos(x, y, 17)){
                casillas.put(getValorEscaner(x, y, 17), "S");
            }
        }
        if (comprobarCasillaPermitida(percepcionJson, 18)){
            if(minimo >= this.getValorPasos(x, y, 18)){
                casillas.put(getValorEscaner(x, y, 18), "SE");
            }
        } 

        String movimiento = this.trafico(casillas,percepcionJson.get("sensor").asArray().size());
        
        if (movimiento.equals("ninguno"))
            return ("ninguno");
        else 
            return ("move"+movimiento);        
    }
    
     /**
     * Comprueba si la casilla está fuera del cuadrante asignado al agente
     * 
     * @author Manuel Ros Rodríguez
     * 
     */  
    public boolean comprobarSiCasillaFueraCuadrante(JsonArray radar, int posicion){
        boolean fuera = true;
        
        Pair<Integer,Integer> coordenadas = this.coordenadasCasillaRadar(radar.size(), this.transformarPosicion(posicion, radar.size()));
        
        if (cuadrante == 1 && coordenadas.getValue() >= 0 && coordenadas.getValue() < tamanoMapa/2 && coordenadas.getKey() >= 0 && coordenadas.getKey() < tamanoMapa/2)
            fuera = false;
        else if (cuadrante == 2 && coordenadas.getValue() >= 0 && coordenadas.getValue() < tamanoMapa/2 && coordenadas.getKey() >= tamanoMapa/2 && coordenadas.getKey() < tamanoMapa)
            fuera = false;
        else if (cuadrante == 3 && coordenadas.getValue() >= tamanoMapa/2 && coordenadas.getValue() < tamanoMapa && coordenadas.getKey() >= 0 && coordenadas.getKey() < tamanoMapa/2)
            fuera = false;
        else if (cuadrante == 4 && coordenadas.getValue() >= tamanoMapa/2 && coordenadas.getValue() < tamanoMapa && coordenadas.getKey() >= tamanoMapa/2 && coordenadas.getKey() < tamanoMapa)
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
                valorCasilla = radar.get(0).asInt();
            } else if (posicion == 7){
                valorCasilla = radar.get(1).asInt();
            } else if (posicion == 8){
                valorCasilla = radar.get(2).asInt();
            } else if (posicion == 11){
                valorCasilla = radar.get(3).asInt();
            } else if (posicion == 12) {
                valorCasilla = radar.get(4).asInt();
            } else if (posicion == 13){
                valorCasilla = radar.get(5).asInt();
            } else if (posicion == 16){
                valorCasilla = radar.get(6).asInt();
            } else if (posicion == 17){
                valorCasilla = radar.get(7).asInt();
            } else if (posicion == 18){
                valorCasilla = radar.get(8).asInt();
            }
        } else if (tamanoRadar <= 25){
            valorCasilla = radar.get(posicion).asInt();
        } else if (tamanoRadar <= 121){
            if (posicion == 6){
                valorCasilla = radar.get(48).asInt();
            } else if (posicion == 7){
                valorCasilla = radar.get(49).asInt();
            } else if (posicion == 8){
                valorCasilla = radar.get(50).asInt();
            } else if (posicion == 11){
                valorCasilla = radar.get(59).asInt();
            } else if (posicion == 12) {
                valorCasilla = radar.get(60).asInt();
            } else if (posicion == 13){
                valorCasilla = radar.get(61).asInt();
            } else if (posicion == 16){
                valorCasilla = radar.get(70).asInt();
            } else if (posicion == 17){
                valorCasilla = radar.get(71).asInt();
            } else if (posicion == 18){
                valorCasilla = radar.get(72).asInt();
            }         
        }
        
        return (valorCasilla);
    }
    
    /**
    *
    * Le pasamos la posicion del radar suponiendo que el radar es 5x5 y el tamaño del radar y nos devuelve la posición
    * adaptada al tamaño del radar real
    * 
    * @author Manuel Ros Rodríguez
    */    
    public int transformarPosicion(int posicion, int tamanoRadar){
        int posicionTransformada = -1;
        
        if (tamanoRadar <= 9){
            if (posicion == 6){
                posicionTransformada = 0;
            } else if (posicion == 7){
                posicionTransformada = 1;
            } else if (posicion == 8){
                posicionTransformada = 2;
            } else if (posicion == 11){
                posicionTransformada = 3;
            } else if (posicion == 12) {
                posicionTransformada = 4;
            } else if (posicion == 13){
                posicionTransformada = 5;
            } else if (posicion == 16){
                posicionTransformada = 6;
            } else if (posicion == 17){
                posicionTransformada = 7;
            } else if (posicion == 18){
                posicionTransformada = 8;
            }
        } else if (tamanoRadar <= 25){
            posicionTransformada = posicion;
        } else if (tamanoRadar <= 121){
            if (posicion == 6){
                posicionTransformada = 48;
            } else if (posicion == 7){
                posicionTransformada = 49;
            } else if (posicion == 8){
                posicionTransformada = 50;
            } else if (posicion == 11){
                posicionTransformada = 59;
            } else if (posicion == 12){
                posicionTransformada = 60;
            } else if (posicion == 13){
                posicionTransformada = 61;
            } else if (posicion == 16){
                posicionTransformada = 70;
            } else if (posicion == 17){
                posicionTransformada = 71;
            } else if (posicion == 18){
                posicionTransformada = 72;
            }         
        }
        
        return (posicionTransformada);
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
