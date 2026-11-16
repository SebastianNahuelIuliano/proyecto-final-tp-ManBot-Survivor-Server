package com.manbotsurvivor.game.servidor;

import com.manbotsurvivor.game.elementos.ControladorEntrada;

import java.io.IOException;
import java.net.BindException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

// Hilo del servidor: escucha los paquetes UDP de los clientes, los procesa y les responde.
// La logica del juego NO corre aca, corre en PantallaJuego (hilo de LibGDX).
// Los metodos que usan los dos hilos son synchronized para que no se pisen.
public class HiloServer extends Thread {

    private DatagramSocket puertoServer;
    private volatile boolean fin = false;
    private String mensajeError = null;

    private Cliente[] clientes;
    private int cantClientes = 0;
    private ControladorEntrada[] entradas;

    private final int  TIEMPO_ESPERA_RECEIVE = 500;
    private final long TIMEOUT_CLIENTE       = 5000;

    private long[] ultimoContacto = new long[2];

    private final int SEGUNDOS_CUENTA = 3;
    private int cuentaRegresiva = 0;
    private boolean cuentaEnCurso = false;
    private boolean partidaIniciada = false;
    private long inicioCuenta = 0;
    private int ultimoNumeroEnviado = -1;

    private boolean[] quiereJugarOtra = new boolean[2];

    private boolean partidaFinalizada = false;
    private boolean reinicioSolicitado = false;
    private boolean finSesionSolicitado = false;

    // Mejora elegida por cada jugador, pendiente de aplicar en el hilo del juego: {idSeleccion, opcion}
    private int[][] mejorasPendientes = new int[2][];

    public HiloServer(ControladorEntrada[] entradas) {
        super("HiloServer");
        this.entradas = entradas;
        clientes = new Cliente[2];

        try {
            puertoServer = new DatagramSocket(ProtocolosRed.PUERTO_SERVIDOR);
            puertoServer.setSoTimeout(TIEMPO_ESPERA_RECEIVE);
        } catch (BindException evento) {
            mensajeError = "El puerto " + ProtocolosRed.PUERTO_SERVIDOR + " ya esta en uso (¿hay otro servidor abierto?)";
            System.err.println(mensajeError);
            fin = true;
        } catch (SocketException evento) {
            mensajeError = "No se pudo abrir el puerto: " + evento.getMessage();
            System.err.println(mensajeError);
            fin = true;
        }
    }

    public void enviarMensaje(String mensaje, InetAddress ipDestino, int puerto) {
        if (puertoServer == null || puertoServer.isClosed()) {
            return;
        }

        byte[] data = mensaje.getBytes(StandardCharsets.UTF_8);
        DatagramPacket dp = new DatagramPacket(data, data.length, ipDestino, puerto);
        try {
            puertoServer.send(dp);
        } catch (IOException event) {
            System.err.println("No se pudo enviar el mensaje a " + ipDestino.getHostAddress() + ": " + event.getMessage());
        }
    }

    public synchronized void enviarMensajeAtodos(String mensaje) {
        for (int i = 0; i < clientes.length; i++) {
            if (clientes[i] != null) {
                DireccionRed direccionCliente = clientes[i].getDireccionRed();
                enviarMensaje(mensaje, direccionCliente.getIp(), direccionCliente.getPuerto());
            }
        }
    }

    @Override
    public void run() {
        System.out.println("Servidor iniciado en el puerto " + ProtocolosRed.PUERTO_SERVIDOR + ". Esperando conexiones...");

        while (!fin && puertoServer != null && !puertoServer.isClosed()) {

            byte[] data = new byte[ProtocolosRed.TAMAÑO_PAQUETE];
            DatagramPacket dp = new DatagramPacket(data, data.length);

            try {
                puertoServer.receive(dp);
                procesarMensaje(dp);

            } catch (SocketTimeoutException timeout) {
                // Paso medio segundo sin paquetes: seguimos para revisar los timeouts.
            } catch (IOException event) {
                if (puertoServer.isClosed()) {
                    break;
                }

                // En Windows, si un cliente se cerro de golpe, receive() puede tirar
                // "Connection reset". No es grave: seguimos escuchando al otro cliente.
                System.err.println("Error al recibir un paquete: " + event.getMessage());
            }

            revisarTimeouts();
        }

        System.out.println("\n\nHiloServer finalizado.");
    }

    private void procesarMensaje(DatagramPacket dp) {
        // Usamos getLength() para leer solo los bytes que llegaron
        String mensaje = new String(dp.getData(), 0, dp.getLength(), StandardCharsets.UTF_8).trim();
        InetAddress ipPaquete = dp.getAddress();
        int puertoPaquete = dp.getPort();

        evaluarMensaje(mensaje, ipPaquete, puertoPaquete);
    }

    private synchronized void evaluarMensaje(String mensaje, InetAddress ipPaquete, int puertoPaquete) {

        int numeroCliente = detectarCliente(ipPaquete, puertoPaquete);

        if (numeroCliente != -1) {
            ultimoContacto[numeroCliente] = System.currentTimeMillis();
        }

        // Un cliente nos busca por broadcast: le respondemos directo para que conozca nuestra IP
        if (mensaje.equals(ProtocolosRed.BUSCAR_SERVIDOR)) {
            enviarMensaje(ProtocolosRed.SERVIDOR_ENCONTRADO, ipPaquete, puertoPaquete);
            return;
        }

        if (mensaje.equals(ProtocolosRed.ESTAS_AHI)) {
            if (numeroCliente != -1) {
                enviarMensaje(ProtocolosRed.ESTOY_ACA, ipPaquete, puertoPaquete);
            }
            return;
        }

        if (mensaje.equals(ProtocolosRed.CONEXION)) {
            procesarConexion(ipPaquete, puertoPaquete, numeroCliente);
            return;
        }

        if (numeroCliente == -1) {
            System.err.println("Error, cliente no detectado al revisar el paquete: " + mensaje);
            return;
        }

        if (mensaje.equals(ProtocolosRed.JUGAR_OTRA)) {
            procesarJugarOtraVez(numeroCliente, ipPaquete, puertoPaquete);
            return;
        }

        if (mensaje.equals(ProtocolosRed.VOLVER_MENU)) {
            finalizarSesion();
            return;
        }

        String[] mensajePorPartes = mensaje.split(ProtocolosRed.SEPARADOR);

        try {
            procesarAccionJugador(mensajePorPartes, numeroCliente);
        } catch (NumberFormatException | ArrayIndexOutOfBoundsException error) {
            System.err.println("Mensaje mal formado del jugador " + (numeroCliente + 1) + ": " + mensaje);
        }
    }

    private void procesarConexion(InetAddress ipPaquete, int puertoPaquete, int numeroCliente) {

        // El cliente ya estaba conectado y no le llego nuestra respuesta: se la repetimos
        if (numeroCliente != -1) {
            enviarDatosConexion(numeroCliente);
            return;
        }

        if (cantClientes >= 2) {
            enviarMensaje(ProtocolosRed.ERROR_LIMITE_CLIENTES, ipPaquete, puertoPaquete);
            return;
        }

        int espacio = buscarEspacioLibre();

        if (espacio != -1) {
            DireccionRed direccionRed = new DireccionRed(ipPaquete, puertoPaquete);

            clientes[espacio] = new Cliente(direccionRed, entradas[espacio]);
            ultimoContacto[espacio] = System.currentTimeMillis();
            quiereJugarOtra[espacio] = false;
            mejorasPendientes[espacio] = null;

            cantClientes++;

            System.out.println("Cliente conectado: jugador " + (espacio + 1) + " (" + ipPaquete.getHostAddress() + ":" + puertoPaquete + ")");

            enviarDatosConexion(espacio);

            if (cantClientes == 2 && !partidaIniciada) {
                iniciarCuentaRegresiva();
            }
        }
    }

    private void enviarDatosConexion(int numeroCliente) {
        DireccionRed direccionCliente = clientes[numeroCliente].getDireccionRed();

        enviarMensaje(ProtocolosRed.OK, direccionCliente.getIp(), direccionCliente.getPuerto());

        enviarMensaje(
            ProtocolosRed.NUMERO_JUGADOR +
                ProtocolosRed.SEPARADOR +
                (numeroCliente + 1),
            direccionCliente.getIp(),
            direccionCliente.getPuerto()
        );

        // Si la partida ya estaba en curso (reconexion), entra directamente
        if (partidaIniciada) {
            enviarMensaje(ProtocolosRed.EMPEZAR, direccionCliente.getIp(), direccionCliente.getPuerto());
        }
    }

    private void procesarAccionJugador(String[] mensajePorPartes, int numeroCliente) {
        final int ACCION = 0;

        if (mensajePorPartes[ACCION].equals(ProtocolosRed.TECLAS)) {

            // Se manda el estado de las 4 teclas juntas: si se pierde un paquete, el siguiente lo corrige
            clientes[numeroCliente].getControladorEntrada().aplicarTeclasRemotas(
                mensajePorPartes[1].equals("1"),
                mensajePorPartes[2].equals("1"),
                mensajePorPartes[3].equals("1"),
                mensajePorPartes[4].equals("1")
            );

        } else if (mensajePorPartes[ACCION].equals(ProtocolosRed.MEJORA)) {

            mejorasPendientes[numeroCliente] = new int[] {
                Integer.parseInt(mensajePorPartes[1]),
                Integer.parseInt(mensajePorPartes[2])
            };

        } else if (mensajePorPartes[ACCION].equals(ProtocolosRed.SE_SALIO_DEL_JUEGO)) {
            desconectarCliente(numeroCliente);
        }
    }

    // Al terminar la partida los dos jugadores tienen que pedir jugar otra vez para empezar una nueva
    private void procesarJugarOtraVez(int numeroCliente, InetAddress ipPaquete, int puertoPaquete) {

        if (!partidaFinalizada) {
            return;
        }

        quiereJugarOtra[numeroCliente] = true;

        enviarMensaje(ProtocolosRed.ESPERANDO_NUEVA_PARTIDA, ipPaquete, puertoPaquete);

        if (cantClientes == 2 && quiereJugarOtra[0] && quiereJugarOtra[1]) {

            reinicioSolicitado = true;
            partidaFinalizada = false;

            enviarMensajeAtodos(ProtocolosRed.NUEVA_PARTIDA);
        }
    }

    // Un jugador eligio "volver al menu" en la pantalla final: termina la sesion de los dos
    private void finalizarSesion() {

        enviarMensajeAtodos(ProtocolosRed.FIN_SESION);

        for (int i = 0; i < clientes.length; i++) {
            if (clientes[i] != null) {
                entradas[i].aplicarTeclasRemotas(false, false, false, false);
                clientes[i] = null;
            }
        }

        cantClientes = 0;
        finSesionSolicitado = true;
        reiniciarEstado();

        System.out.println("Sesion finalizada. Esperando nuevos jugadores...");
    }

    public synchronized void reiniciarPartida() {
        quiereJugarOtra = new boolean[2];
        mejorasPendientes = new int[2][];

        long ahora = System.currentTimeMillis();
        for (int i = 0; i < ultimoContacto.length; i++) {
            ultimoContacto[i] = ahora;
        }

        reiniciarEstado();

        if (cantClientes == 2) {
            iniciarCuentaRegresiva();
        }
    }

    private void reiniciarEstado() {
        partidaIniciada = false;
        cuentaEnCurso = false;
        cuentaRegresiva = 0;
        ultimoNumeroEnviado = -1;
        partidaFinalizada = false;
        quiereJugarOtra = new boolean[2];
    }

    private int buscarEspacioLibre() {
        int espacio = -1;
        int i = 0;
        while (i < clientes.length && espacio == -1) {
            if (clientes[i] == null) {
                espacio = i;
            }
            i++;
        }

        return espacio;
    }

    private int detectarCliente(InetAddress ipPaquete, int puertoPaquete) {
        int numeroCliente = -1;
        for (int i = 0; i < clientes.length; i++) {
            if (clientes[i] != null) {
                DireccionRed direccionClienteActual = clientes[i].getDireccionRed();
                if (ipPaquete.equals(direccionClienteActual.getIp()) && puertoPaquete == direccionClienteActual.getPuerto()) {
                    numeroCliente = i;
                }
            }
        }
        return numeroCliente;
    }

    private synchronized void revisarTimeouts() {
        long ahora = System.currentTimeMillis();

        for (int i = 0; i < clientes.length; i++) {
            if (clientes[i] != null && ahora - ultimoContacto[i] > TIMEOUT_CLIENTE) {

                System.out.println("Jugador " + (i + 1) + " dejó de responder (cierre brusco o caída de red).");
                desconectarCliente(i);
            }
        }
    }

    private void desconectarCliente(int i) {
        if (clientes[i] == null) return;

        // Que su robot no siga caminando solo
        entradas[i].aplicarTeclasRemotas(false, false, false, false);

        clientes[i] = null;
        cantClientes--;
        quiereJugarOtra[i] = false;
        mejorasPendientes[i] = null;

        System.out.println("Jugador " + (i + 1) + " desconectado. Jugadores conectados: " + cantClientes);

        if (cuentaEnCurso) {
            cancelarCuenta();
        }

        if (cantClientes == 0) {
            // No queda nadie: la proxima vez que se conecten empieza una partida nueva
            reiniciarEstado();
            finSesionSolicitado = true;
            return;
        }

        // Segun el documento, el jugador que queda sigue jugando solo
        enviarMensajeAtodos(ProtocolosRed.JUGADOR_DESCONECTADO);
    }

    private void iniciarCuentaRegresiva() {
        if (!cuentaEnCurso && !partidaIniciada) {
            ultimoNumeroEnviado = -1;
            inicioCuenta = System.currentTimeMillis();
            cuentaEnCurso = true;
        }
    }

    public synchronized void actualizarCuentaRegresiva() {
        if (cuentaEnCurso) {
            long transcurrido = System.currentTimeMillis() - inicioCuenta;
            int restante = SEGUNDOS_CUENTA - (int) (transcurrido / 1000);

            if (restante <= 0) {
                cuentaRegresiva = 0;
                cuentaEnCurso = false;
                partidaIniciada = true;

                enviarMensajeAtodos(ProtocolosRed.EMPEZAR);
                return;
            }

            // Solo mandamos el mensaje cuando cambia el número (una vez por segundo)
            if (restante != ultimoNumeroEnviado) {
                ultimoNumeroEnviado = restante;
                cuentaRegresiva = restante;
                enviarMensajeAtodos(ProtocolosRed.CUENTA_REGRESIVA + ProtocolosRed.SEPARADOR + restante);
            }
        }
    }

    private void cancelarCuenta() {
        cuentaRegresiva = 0;
        cuentaEnCurso = false;
        enviarMensajeAtodos(ProtocolosRed.CUENTA_REGRESIVA + ProtocolosRed.SEPARADOR + 0);
    }

    // Avisa a los clientes que el servidor se cierra y libera el puerto
    public void finalizarHilo() {
        if (!fin && puertoServer != null && !puertoServer.isClosed()) {
            enviarMensajeAtodos(ProtocolosRed.SERVIDOR_CERRADO);
        }

        fin = true;

        if (puertoServer != null && !puertoServer.isClosed()) {
            puertoServer.close();
        }

        try {
            join(1000); // Esperamos a que el hilo termine de verdad
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    // Devuelve la mejora pendiente {idSeleccion, opcion} del jugador y la borra, o null
    public synchronized int[] consumirMejora(int indice) {
        int[] mejora = mejorasPendientes[indice];
        mejorasPendientes[indice] = null;
        return mejora;
    }

    public synchronized boolean estaConectado(int indice) {
        return clientes[indice] != null;
    }

    public synchronized int getCantClientes() {
        return cantClientes;
    }

    public synchronized int getCuentaRegresiva() {
        return cuentaRegresiva;
    }

    public synchronized boolean isPartidaIniciada() {
        return partidaIniciada;
    }

    public boolean isFin() {
        return fin;
    }

    public String getMensajeError() {
        return mensajeError;
    }

    public synchronized void marcarPartidaFinalizada() {
        partidaFinalizada = true;
    }

    public synchronized boolean isReinicioSolicitado() {
        return reinicioSolicitado;
    }

    public synchronized void confirmarReinicio() {
        reinicioSolicitado = false;
    }

    // true una sola vez cuando la sesion termino (volver al menu o se fueron todos)
    public synchronized boolean consumirFinSesion() {
        boolean valor = finSesionSolicitado;
        finSesionSolicitado = false;
        return valor;
    }

    // IPs de esta computadora, para mostrarlas en la ventana del servidor
    public static List<String> obtenerIpsLocales() {
        List<String> ips = new ArrayList<>();

        try {
            for (NetworkInterface interfaz : Collections.list(NetworkInterface.getNetworkInterfaces())) {
                if (!interfaz.isUp() || interfaz.isLoopback()) {
                    continue;
                }

                for (InetAddress ip : Collections.list(interfaz.getInetAddresses())) {
                    if (ip instanceof Inet4Address) {
                        ips.add(ip.getHostAddress());
                    }
                }
            }
        } catch (SocketException error) {
            System.err.println("No se pudieron leer las IP locales: " + error.getMessage());
        }

        return ips;
    }
}