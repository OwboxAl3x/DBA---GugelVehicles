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
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import javafx.util.Pair;

/**
 *
 * @author Adrian Martin Jaimez
 * @author Manuel Ros Rodríguez
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
    private String volador;
    private int tamanoMapa;
    
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
        volador = "";
        tamanoMapa = 0;
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
            if (msg.getSender().toString().contains("Cerastes")){
                System.out.println("cola:"+msg.toString());
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
            System.out.print("ejecutado");
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
        boolean salirSubscribe = false;
        ACLMessage inbox = null;
        ACLMessage inbox2 = null;
        ACLMessage inbox3 = null;
        ACLMessage inbox4 = null;
        JsonObject json = null;
        boolean salir;
        
        while (!salirSubscribe){
            salir = false;
            while (!salir){
                json= new JsonObject();
                json.add("world", mapa);
                this.enviarMensaje(new AgentID("Cerastes"), json, null, ACLMessage.SUBSCRIBE, null, null);
                System.out.println("probando1");
                
                inbox = this.recibirMensaje(mensajesServidor);
                
                if (inbox.getContent().contains("trace")){
                    System.out.println("traza recibida: "+inbox.toString());
                    inbox = this.recibirMensaje(mensajesServidor); 
                }
                System.out.println("probando2"+inbox.toString());
                
                // Si es un inform, terminamos
                if (inbox.getPerformativeInt() == ACLMessage.INFORM)
                    salir = true;
                
                salirSubscribe = true;
            }
        
            conversationID = inbox.getConversationId();
            
            // Ahora los coches van a hacer login
            json = new JsonObject();
            json.add("logueate",conversationID); 
            this.enviarMensaje(new AgentID(nombreCoche1), json, null, ACLMessage.REQUEST, null, nombreCoche1);
            this.enviarMensaje(new AgentID(nombreCoche2), json, null, ACLMessage.REQUEST, null, nombreCoche2);
            this.enviarMensaje(new AgentID(nombreCoche3), json, null, ACLMessage.REQUEST, null, nombreCoche3);
            this.enviarMensaje(new AgentID(nombreCoche4), json, null, ACLMessage.REQUEST, null, nombreCoche4);

            inbox = this.recibirMensaje(mensajesCoches);
            System.out.println(inbox.toString());

            inbox2 = this.recibirMensaje(mensajesCoches);

            inbox3 = this.recibirMensaje(mensajesCoches);

            inbox4 = this.recibirMensaje(mensajesCoches);

            // Comprobamos si hay algún volador
            if (Json.parse(inbox.getContent()).asObject().get("capabilities").asObject().get("fly").asBoolean() == true)
                volador = nombreCoche1;
            else if (Json.parse(inbox2.getContent()).asObject().get("capabilities").asObject().get("fly").asBoolean() == true)
                volador = nombreCoche2;
            else if (Json.parse(inbox3.getContent()).asObject().get("capabilities").asObject().get("fly").asBoolean() == true)
                volador = nombreCoche3;
            else if (Json.parse(inbox4.getContent()).asObject().get("capabilities").asObject().get("fly").asBoolean() == true)
                volador = nombreCoche4;    

            // Comprobamos si hay algún camión
            boolean camion = false;
            if (Json.parse(inbox.getContent()).asObject().get("capabilities").asObject().get("fuelrate").asInt() == 4 || Json.parse(inbox2.getContent()).asObject().get("capabilities").asObject().get("fuelrate").asInt() == 4 ||
                    Json.parse(inbox3.getContent()).asObject().get("capabilities").asObject().get("fuelrate").asInt() == 4 || Json.parse(inbox4.getContent()).asObject().get("capabilities").asObject().get("fuelrate").asInt() == 4)
                camion = true;

            if (!volador.isEmpty() && camion){
                // Hemos conseguido lo que queríamos y podemos dejar de hacer subscribe
                salirSubscribe = true;
            } else {
                // No hemos conseguido lo que queríamos, mandamos cancel y vamos a repetir el subscribe
                this.enviarMensaje(new AgentID(nombreCoche1), new JsonObject(), null, ACLMessage.CANCEL, null, null);
                this.enviarMensaje(new AgentID(nombreCoche2), new JsonObject(), null, ACLMessage.CANCEL, null, null);
                this.enviarMensaje(new AgentID(nombreCoche3), new JsonObject(), null, ACLMessage.CANCEL, null, null);
                this.enviarMensaje(new AgentID(nombreCoche4), new JsonObject(), null, ACLMessage.CANCEL, null, null);    
                this.enviarMensaje(new AgentID("Cerastes"), new JsonObject(), null, ACLMessage.CANCEL, null, null);
                
                // Recibimos AGREE y traza               
                inbox = this.recibirMensaje(mensajesServidor);
                
                inbox = this.recibirMensaje(mensajesServidor);   
            }
        }
        
        // Vamos a comprobar si algún coche ha aparecido abajo del todo y si lo ha hecho tendremos el tamaño del mapa
        if (Json.parse(inbox.getContent()).asObject().get("x").asInt() > 30)
            tamanoMapa = Json.parse(inbox.getContent()).asObject().get("x").asInt();
        else if (Json.parse(inbox2.getContent()).asObject().get("x").asInt() > 30)
            tamanoMapa = Json.parse(inbox.getContent()).asObject().get("x").asInt();
        else if (Json.parse(inbox3.getContent()).asObject().get("x").asInt() > 30)
            tamanoMapa = Json.parse(inbox.getContent()).asObject().get("x").asInt();
        else if (Json.parse(inbox4.getContent()).asObject().get("x").asInt() > 30)
            tamanoMapa = Json.parse(inbox.getContent()).asObject().get("x").asInt();
        
        // Redondeamos por las paredes limites del mapa
        if (tamanoMapa != 0 && tamanoMapa < 100)
            tamanoMapa = 100;
        if (tamanoMapa != 0 && tamanoMapa > 100 && tamanoMapa < 300)
            tamanoMapa = 300;
        if (tamanoMapa != 0 && tamanoMapa > 300 && tamanoMapa < 500)
            tamanoMapa = 500;
        
        if (tamanoMapa == 0){
           // No tenemos el tamaño del mapa, vamos a pedirle al volador que compruebe el tamaño
            this.enviarMensaje(new AgentID(volador), null, "calcularTamanoMapa", ACLMessage.REQUEST, null, volador); 
            
            ACLMessage otroInbox = this.recibirMensaje(mensajesCoches);

            tamanoMapa = Json.parse(otroInbox.getContent()).asObject().get("tamanoMapa").asInt();
        }
        
        // Como tenemos el tamaño del mapa, vamos a realizar el reparto de cuadrantes
        
        // Asignación de cuadrantes
        ArrayList<Pair> posiciones = new ArrayList<Pair>();
        posiciones.add(new Pair(Json.parse(inbox.getContent()).asObject().get("x").asInt(),Json.parse(inbox.getContent()).asObject().get("y").asInt()));
        posiciones.add(new Pair(Json.parse(inbox2.getContent()).asObject().get("x").asInt(),Json.parse(inbox.getContent()).asObject().get("y").asInt()));
        posiciones.add(new Pair(Json.parse(inbox3.getContent()).asObject().get("x").asInt(),Json.parse(inbox.getContent()).asObject().get("y").asInt()));
        posiciones.add(new Pair(Json.parse(inbox4.getContent()).asObject().get("x").asInt(),Json.parse(inbox.getContent()).asObject().get("y").asInt()));
        ArrayList<Integer> asignacion = this.asignarCuadrantes(posiciones);
        
        // Coche 1
        json = new JsonObject();
        json.add("empieza",asignacion.get(0));
        json.add("tamanoMapa",tamanoMapa);
        this.enviarMensaje(new AgentID(nombreCoche1), json, null, ACLMessage.REQUEST, null, nombreCoche1); 

        // Coche 2
        json = new JsonObject();
        json.add("empieza",asignacion.get(1));
        json.add("tamanoMapa",tamanoMapa);
        this.enviarMensaje(new AgentID(nombreCoche2), json, null, ACLMessage.REQUEST, null, nombreCoche2);  

        // Coche 3
        json = new JsonObject();
        json.add("empieza",asignacion.get(2));
        json.add("tamanoMapa",tamanoMapa);
        this.enviarMensaje(new AgentID(nombreCoche3), json, null, ACLMessage.REQUEST, null, nombreCoche3); 

        // Coche 4
        json = new JsonObject();
        json.add("empieza",asignacion.get(3));
        json.add("tamanoMapa",tamanoMapa);
        this.enviarMensaje(new AgentID(nombreCoche4), json, null, ACLMessage.REQUEST, null, nombreCoche4); 

        // Recibimos confirmaciones de los coches
        inbox = this.recibirMensaje(mensajesCoches);

        inbox = this.recibirMensaje(mensajesCoches);

        inbox = this.recibirMensaje(mensajesCoches);

        inbox = this.recibirMensaje(mensajesCoches);
    }
    
    public ACLMessage recibirMensaje(MessageQueue cola) throws InterruptedException{
        while (cola.isEmpty()){this.sleep(500);}
        return (cola.Pop());
    }
    
    /**
    *
    * @author Manuel Ros Rodríguez
    */   
    public ArrayList<Integer> asignarCuadrantes(ArrayList<Pair> posiciones){
        ArrayList<Integer> asignacion = new ArrayList<Integer>();
        boolean cuadranteOcupado1 = false;
        boolean cuadranteOcupado2 = false;
        boolean cuadranteOcupado3 = false;
        boolean cuadranteOcupado4 = false;
        int x,y = 0;
        
        for (int i=0; i<4; i++){
            x = (int) posiciones.get(i).getKey();
            y = (int) posiciones.get(i).getValue();
            if (x > 0 && x < tamanoMapa/2 && y > 0 && y < tamanoMapa/2 && !cuadranteOcupado1){
                asignacion.add(1);
                cuadranteOcupado1 = true;
            } else if (x > 0 && x < tamanoMapa/2 && y > tamanoMapa/2 && y < tamanoMapa && !cuadranteOcupado2){
                asignacion.add(2);
                cuadranteOcupado2 = true;
            } else if (x > tamanoMapa/2 && x < tamanoMapa && y > 0 && y < tamanoMapa/2 && !cuadranteOcupado3){
                asignacion.add(3);
                cuadranteOcupado3 = true;
            } else if (x > tamanoMapa/2 && x < tamanoMapa && y > tamanoMapa/2 && y < tamanoMapa && !cuadranteOcupado4){
                asignacion.add(4); 
                cuadranteOcupado4 = true;
            } else {
                asignacion.add(0);
            }
        }
        
        // Para los coches que no tienen un cuadrante asignado
        for (int i=0; i<4; i++){
            if (asignacion.get(i) == 0){
                x = (int) posiciones.get(i).getKey();
                y = (int) posiciones.get(i).getValue();
                
                // Probaremos con los cuadrantes más cercanos al que él está
                if (x > 0 && x < tamanoMapa/2 && y > 0 && y < tamanoMapa/2){
                    if (!cuadranteOcupado2){
                        asignacion.set(i, 2);
                        cuadranteOcupado2 = true;
                    } else if (!cuadranteOcupado3){
                        asignacion.set(i, 3);
                        cuadranteOcupado3 = true;
                    } else if (!cuadranteOcupado4){
                        asignacion.set(i, 4);
                        cuadranteOcupado4 = true;
                    }
                } else if (x > 0 && x < tamanoMapa/2 && y > tamanoMapa/2 && y < tamanoMapa){
                    if (!cuadranteOcupado1){
                        asignacion.set(i, 1);
                        cuadranteOcupado1 = true;
                    } else if (!cuadranteOcupado4){
                        asignacion.set(i, 4);
                        cuadranteOcupado4 = true;
                    } else if (!cuadranteOcupado3){
                        asignacion.set(i, 3);
                        cuadranteOcupado3 = true;
                    }
                } else if (x > tamanoMapa/2 && x < tamanoMapa && y > 0 && y < tamanoMapa/2){
                    if (!cuadranteOcupado1){
                        asignacion.set(i, 1);
                        cuadranteOcupado1 = true;
                    } else if (!cuadranteOcupado4){
                        asignacion.set(i, 4);
                        cuadranteOcupado4 = true;
                    } else if (!cuadranteOcupado2){
                        asignacion.set(i, 2);
                        cuadranteOcupado2 = true;
                    }
                } else if (x > tamanoMapa/2 && x < tamanoMapa && y > tamanoMapa/2 && y < tamanoMapa){
                    if (!cuadranteOcupado3){
                        asignacion.set(i, 3);
                        cuadranteOcupado3 = true;
                    } else if (!cuadranteOcupado2){
                        asignacion.set(i, 2);
                        cuadranteOcupado2 = true;
                    } else if (!cuadranteOcupado1){
                        asignacion.set(i, 1);
                        cuadranteOcupado1 = true;
                    }
                }
            }
        }
        
        return (asignacion);
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
            fos = new FileOutputStream(conversationID+"_"+mapa+".png");
            fos.write(data);
            fos.close();
            System.out.println("Imagen creada");
        } catch (IOException ex) {
            System.out.println("Fallo al crear la imagen");
        }
    }
    
    /**
    * 
    * Hace el logout al servidor y a los coches después de recibir que los coches han terminado
    * Si recibe un INFORM crea la imagen, si recibe un AGREE busca el siguiente mensaje
    *
    * @author Alejandro García
    */
    public void logout() throws InterruptedException{
        
        ACLMessage inbox = null;
        ACLMessage inbox2 = null;
        JsonObject json = null;
        
        this.enviarMensaje(new AgentID(nombreCoche1), new JsonObject(), null, ACLMessage.CANCEL, null, null);
        this.enviarMensaje(new AgentID(nombreCoche2), new JsonObject(), null, ACLMessage.CANCEL, null, null);
        this.enviarMensaje(new AgentID(nombreCoche3), new JsonObject(), null, ACLMessage.CANCEL, null, null);
        this.enviarMensaje(new AgentID(nombreCoche4), new JsonObject(), null, ACLMessage.CANCEL, null, null);    
        this.enviarMensaje(new AgentID("Cerastes"), new JsonObject(), null, ACLMessage.CANCEL, null, null);

        // Recibimos AGREE o traza               
        while (mensajesServidor.isEmpty()){}
        inbox = mensajesServidor.Pop(); 
        
        // Comprobamos si es un agree o la traza
        if (inbox.getPerformativeInt() == ACLMessage.INFORM){
            json = Json.parse(inbox.getContent()).asObject();
            this.crearImagen(json);
        } else if (inbox.getPerformativeInt() == ACLMessage.AGREE){
            while (mensajesServidor.isEmpty()){}
            inbox2 = mensajesServidor.Pop();
            json = Json.parse(inbox2.getContent()).asObject();
            this.crearImagen(json);
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
