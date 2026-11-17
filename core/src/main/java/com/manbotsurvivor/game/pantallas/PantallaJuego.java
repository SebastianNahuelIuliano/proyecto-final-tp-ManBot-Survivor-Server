package com.manbotsurvivor.game.pantallas;

import java.util.ArrayList;
import java.util.List;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Screen;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.maps.tiled.renderers.OrthogonalTiledMapRenderer;
import com.badlogic.gdx.utils.viewport.FitViewport;
import com.badlogic.gdx.utils.viewport.Viewport;
import com.manbotsurvivor.game.NavegacionPantallas;
import com.manbotsurvivor.game.elementos.ControladorEntrada;
import com.manbotsurvivor.game.elementos.Mapa;
import com.manbotsurvivor.game.elementos.SistemaCombate;
import com.manbotsurvivor.game.enemigos.ChipEnergia;
import com.manbotsurvivor.game.enemigos.DroneBasico;
import com.manbotsurvivor.game.enemigos.Enemigo;
import com.manbotsurvivor.game.enemigos.JefeMecanico;
import com.manbotsurvivor.game.enemigos.NanoBot;
import com.manbotsurvivor.game.enemigos.RobotTanque;
import com.manbotsurvivor.game.escenas.Hud;
import com.manbotsurvivor.game.escenas.PanelResultado;
import com.manbotsurvivor.game.gestores.GestorEntidades;
import com.manbotsurvivor.game.gestores.GestorOleadas;
import com.manbotsurvivor.game.gestores.GestorPartida;
import com.manbotsurvivor.game.gestores.GestorProgresion;
import com.manbotsurvivor.game.jugador.Jugador;
import com.manbotsurvivor.game.jugador.Mejora;
import com.manbotsurvivor.game.jugador.VidaCompartida;
import com.manbotsurvivor.game.servidor.HiloServer;
import com.manbotsurvivor.game.servidor.ProtocolosRed;

// Pantalla de juego del SERVIDOR: aca corre toda la logica de la partida
// (movimiento, enemigos, combate, oleadas, experiencia y mejoras).
// Los clientes solo mandan sus teclas y dibujan el estado que les enviamos.
public class PantallaJuego implements Screen {
	private static final float ANCHO_MAPA = 100 * 32;
	private static final float ALTO_MAPA = 100 * 32;
	private static final float INTERVALO_TRANSMISION = 0.05f; // 20 envios por segundo
	private static final float DELTA_MAXIMO = 0.1f;

	private final SpriteBatch batch;
	private final NavegacionPantallas navegacion;
	private final int anchoVista;
	private final int altoVista;
	private SistemaCombate sistemaCombate;
	private SistemaCombate sistemaCombate2;
	private GestorEntidades gestorEntidades;
    private OrthographicCamera camaraJuego;
    private Viewport gamePort;
    private Hud hud;
    private Mapa mapa;
    private OrthogonalTiledMapRenderer renderizadorMapa;
    private ControladorEntrada controladorEntrada;
    private ControladorEntrada controladorSegundoJugador;
    private ControladorEntrada controladorServidor;
    private Jugador jugador;
    private Jugador segundoJugador;
    private ArrayList<Jugador> jugadores;
    private ArrayList<Enemigo> enemigos;
    private ArrayList<ChipEnergia> chipsEnergia;
    private ShapeRenderer formaEnemigo;
    private BitmapFont fuentePausa;
    private VidaCompartida vidaEquipo;
    private GestorPartida gestorPartida;
    private GestorOleadas gestorOleadas;
    private GestorProgresion gestorProgresion;
    private GestorProgresion gestorProgresion2;
    private PanelResultado panelResultado;

    private HiloServer hiloServidor;
    private float tiempoTransmision;
    private List<String> ipsLocales;

    public PantallaJuego(SpriteBatch batch, NavegacionPantallas navegacion, int anchoVista, int altoVista) {

    	this.batch = batch;
        this.navegacion = navegacion;
        this.anchoVista = anchoVista;
        this.altoVista = altoVista;

        mapa = new Mapa();
        renderizadorMapa = new OrthogonalTiledMapRenderer(mapa.obtenerMapa());

        camaraJuego = new OrthographicCamera();
        gamePort = new FitViewport(anchoVista, altoVista, camaraJuego);
        camaraJuego.zoom = 2.0f;
        camaraJuego.position.set(anchoVista / 2, altoVista / 2, 0);
        camaraJuego.update();

        formaEnemigo = new ShapeRenderer();
        fuentePausa = new BitmapFont();
        panelResultado = new PanelResultado();

        // Las teclas de los jugadores llegan por red y se guardan en estos controladores
        controladorEntrada = new ControladorEntrada();
        controladorSegundoJugador = new ControladorEntrada(true);

        // Teclado de la computadora del servidor (solo para ESC)
        controladorServidor = new ControladorEntrada();

        crearPartida();

        hiloServidor = new HiloServer(new ControladorEntrada[] { controladorEntrada, controladorSegundoJugador });
        ipsLocales = HiloServer.obtenerIpsLocales();
    }

    // Crea (o vuelve a crear) todos los objetos de una partida nueva
    private void crearPartida() {

        if (jugador != null) {
            jugador.disponer();
            segundoJugador.disponer();
            sistemaCombate.disponer();
            sistemaCombate2.disponer();
            hud.disponer();
        }

        controladorEntrada.aplicarTeclasRemotas(false, false, false, false);
        controladorSegundoJugador.aplicarTeclasRemotas(false, false, false, false);

        vidaEquipo = new VidaCompartida(100);
        enemigos = new ArrayList<>();
        chipsEnergia = new ArrayList<>();

        jugador = new Jugador(200, 100, 100, controladorEntrada, ANCHO_MAPA, ALTO_MAPA, mapa, vidaEquipo);
        segundoJugador = new Jugador(260, 100, 100, controladorSegundoJugador, ANCHO_MAPA, ALTO_MAPA, mapa, vidaEquipo);

        jugadores = new ArrayList<>();
        jugadores.add(jugador);
        jugadores.add(segundoJugador);

        sistemaCombate = new SistemaCombate(jugador, enemigos, Color.RED);
        sistemaCombate2 = new SistemaCombate(segundoJugador, enemigos, Color.CYAN);

        gestorEntidades = new GestorEntidades(jugadores, enemigos, chipsEnergia);
        gestorPartida = new GestorPartida();
        gestorOleadas = new GestorOleadas(enemigos, jugadores, mapa, gestorPartida, ANCHO_MAPA, ALTO_MAPA);

        hud = new Hud(batch, jugador, segundoJugador, gestorPartida);
        hud.resize(Gdx.graphics.getWidth(), Gdx.graphics.getHeight());

        gestorProgresion = new GestorProgresion(jugador, sistemaCombate, gestorPartida);
        gestorProgresion2 = new GestorProgresion(segundoJugador, sistemaCombate2, gestorPartida);

        tiempoTransmision = 0;
    }

    @Override
    public void show() {
        Gdx.input.setInputProcessor(controladorServidor);
        hiloServidor.start();
    }

    @Override
    public void render(float delta) {

        delta = Math.min(delta, DELTA_MAXIMO);

        Gdx.gl.glClearColor(0, 0, 0, 1);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);

        if (controladorServidor.consumirPulsacionEscape()) {
            navegacion.mostrarMenu(); // Cierra el servidor (hide)
            return;
        }

        // Puerto ocupado u otro error al abrir el socket
        if (hiloServidor.isFin()) {
            dibujarTextos(Color.RED, "NO SE PUDO INICIAR EL SERVIDOR", String.valueOf(hiloServidor.getMensajeError()), "ESC - Volver al menu");
            return;
        }

        hiloServidor.actualizarCuentaRegresiva();

        // Volvieron al menu o se desconectaron todos: preparamos una partida nueva
        if (hiloServidor.consumirFinSesion()) {
            crearPartida();
        }

        // Los dos pidieron jugar otra vez
        if (hiloServidor.isReinicioSolicitado()) {
            crearPartida();
            hiloServidor.confirmarReinicio();
            hiloServidor.reiniciarPartida();
        }

        if (!hiloServidor.isPartidaIniciada()) {
            dibujarEspera();
            return;
        }

        sincronizarConexiones();
        aplicarMejorasRecibidas();
        actualizarLogica(delta);

        tiempoTransmision += delta;

        if (tiempoTransmision >= INTERVALO_TRANSMISION) {
            tiempoTransmision = 0;
            realizarTransmisionDatosJuego();
        }

        dibujarPartida();
    }

    // =====================================================================
    // Logica del juego
    // =====================================================================

    private void actualizarLogica(float delta) {

        gestorPartida.actualizar(delta, vidaEquipo.obtenerVida());

        if (gestorPartida.estaEnCurso()) {
            jugador.actualizar(delta);
            segundoJugador.actualizar(delta);

            for (Enemigo enemigo : enemigos) {
                enemigo.actualizar(delta, jugadores, enemigos);
            }

            sistemaCombate.actualizar(delta);
            sistemaCombate2.actualizar(delta);

            gestorEntidades.eliminarEnemigosMuertos();
            gestorEntidades.recogerChips();

            gestorOleadas.actualizar(delta);

            gestorPartida.actualizar(0, vidaEquipo.obtenerVida());
        }

        // Los dos eligen su mejora al mismo tiempo, cada uno en su computadora
        if (jugador.estaConectado()) {
            gestorProgresion.actualizar();
        }

        if (segundoJugador.estaConectado()) {
            gestorProgresion2.actualizar();
        }

        // Cuando ninguno esta eligiendo, el tiempo vuelve a correr
        if (gestorPartida.estaPausada() && !haySeleccionActiva()) {
            gestorPartida.reanudar();
        }

        if (gestorPartida.terminoLaPartida()) {
            hiloServidor.marcarPartidaFinalizada();
        }
    }

    private boolean haySeleccionActiva() {
        return gestorProgresion.haySeleccionActiva() || gestorProgresion2.haySeleccionActiva();
    }

    // Compara quien esta conectado en el hilo de red con el estado de cada Jugador
    private void sincronizarConexiones() {

        for (int i = 0; i < jugadores.size(); i++) {

            Jugador actual = jugadores.get(i);
            Jugador companero = jugadores.get((i + 1) % jugadores.size());
            boolean conectado = hiloServidor.estaConectado(i);

            if (conectado && !actual.estaConectado()) {
                // Reconexion: aparece al lado del compañero
                actual.reconectar(companero.obtenerPosicionX(), companero.obtenerPosicionY());
            } else if (!conectado && actual.estaConectado()) {
                // Desconexion: el otro jugador sigue solo
                actual.desconectar();
                obtenerProgresion(i).cancelarSeleccion();
            }
        }
    }

    private void aplicarMejorasRecibidas() {

        for (int i = 0; i < jugadores.size(); i++) {

            int[] mejora = hiloServidor.consumirMejora(i);

            if (mejora != null) {
                obtenerProgresion(i).elegir(mejora[0], mejora[1]);
            }
        }
    }

    private GestorProgresion obtenerProgresion(int indice) {
        return indice == 0 ? gestorProgresion : gestorProgresion2;
    }

    private SistemaCombate obtenerCombate(int indice) {
        return indice == 0 ? sistemaCombate : sistemaCombate2;
    }

    // =====================================================================
    // Envio del estado a los clientes
    // =====================================================================

    private void realizarTransmisionDatosJuego() {

        String S = ProtocolosRed.SEPARADOR;

        boolean jefeVivo = gestorOleadas.jefeAparecio()
                && gestorOleadas.obtenerJefe() != null
                && gestorOleadas.obtenerJefe().estaVivo();

        // La experiencia y el nivel son compartidos, alcanza con mandar los del jugador 1
        hiloServidor.enviarMensajeAtodos(
            ProtocolosRed.PARTIDA +
            S + gestorPartida.obtenerEstado().ordinal() +
            S + Math.round(gestorPartida.obtenerTiempoEnJuego() * 1000) +
            S + vidaEquipo.obtenerVida() +
            S + vidaEquipo.obtenerVidaMaxima() +
            S + jugador.obtenerNivel() +
            S + jugador.obtenerExperiencia() +
            S + jugador.obtenerExperienciaNecesaria() +
            S + (jefeVivo ? 1 : 0)
        );

        for (int i = 0; i < jugadores.size(); i++) {

            Jugador jugadorActual = jugadores.get(i);
            GestorProgresion progresion = obtenerProgresion(i);

            hiloServidor.enviarMensajeAtodos(
                ProtocolosRed.JUGADOR +
                S + (i + 1) +
                S + (jugadorActual.estaConectado() ? 1 : 0) +
                S + Math.round(jugadorActual.obtenerPosicionX()) +
                S + Math.round(jugadorActual.obtenerPosicionY()) +
                S + (jugadorActual.estaMirandoDerecha() ? 1 : 0) +
                S + (jugadorActual.estaMoviendose() ? 1 : 0) +
                S + jugadorActual.obtenerEnemigosEliminados()
            );

            StringBuilder opciones = new StringBuilder();

            for (Mejora mejora : progresion.obtenerOpciones()) {
                if (opciones.length() > 0) {
                    opciones.append(ProtocolosRed.SEPARADOR_OPCION);
                }
                opciones.append(mejora.ordinal());
            }

            hiloServidor.enviarMensajeAtodos(
                ProtocolosRed.SELECCION +
                S + (i + 1) +
                S + progresion.obtenerIdSeleccion() +
                S + (progresion.haySeleccionActiva() ? 1 : 0) +
                S + opciones
            );
        }

        StringBuilder datosEnemigos = new StringBuilder();

        for (Enemigo enemigo : enemigos) {
            if (datosEnemigos.length() > 0) {
                datosEnemigos.append(ProtocolosRed.SEPARADOR_ELEMENTO);
            }
            datosEnemigos.append(enemigo.obtenerId()).append(',')
                    .append(enemigo.obtenerTipo()).append(',')
                    .append(Math.round(enemigo.obtenerPosicionX())).append(',')
                    .append(Math.round(enemigo.obtenerPosicionY()));
        }

        hiloServidor.enviarMensajeAtodos(ProtocolosRed.ENEMIGOS + S + datosEnemigos);

        StringBuilder datosProyectiles = new StringBuilder();

        for (int i = 0; i < jugadores.size(); i++) {
            obtenerCombate(i).agregarProyectilesAlMensaje(datosProyectiles, i + 1);
        }

        hiloServidor.enviarMensajeAtodos(ProtocolosRed.PROYECTILES + S + datosProyectiles);

        StringBuilder datosChips = new StringBuilder();

        for (ChipEnergia chip : chipsEnergia) {
            if (datosChips.length() > 0) {
                datosChips.append(ProtocolosRed.SEPARADOR_ELEMENTO);
            }
            datosChips.append(Math.round(chip.obtenerPosicionX())).append(',')
                    .append(Math.round(chip.obtenerPosicionY()));
        }

        hiloServidor.enviarMensajeAtodos(ProtocolosRed.CHIPS + S + datosChips);
    }

    // =====================================================================
    // Dibujo (el servidor tambien muestra la partida)
    // =====================================================================

    private void dibujarPartida() {

        float centroX;
        float centroY;

        if (jugador.estaVivo() && segundoJugador.estaVivo()) {
            centroX = (jugador.obtenerPosicionX() + segundoJugador.obtenerPosicionX()) / 2f + 16f;
            centroY = (jugador.obtenerPosicionY() + segundoJugador.obtenerPosicionY()) / 2f + 16f;
        } else if (jugador.estaVivo()) {
            centroX = jugador.obtenerPosicionX() + 16f;
            centroY = jugador.obtenerPosicionY() + 16f;
        } else {
            centroX = segundoJugador.obtenerPosicionX() + 16f;
            centroY = segundoJugador.obtenerPosicionY() + 16f;
        }

        float mitadAnchoCamara = camaraJuego.viewportWidth * camaraJuego.zoom / 2;
        float mitadAltoCamara = camaraJuego.viewportHeight * camaraJuego.zoom / 2;

        centroX = Math.max(mitadAnchoCamara, Math.min(ANCHO_MAPA - mitadAnchoCamara, centroX));
        centroY = Math.max(mitadAltoCamara, Math.min(ALTO_MAPA - mitadAltoCamara, centroY));

        camaraJuego.position.set(centroX, centroY, 0);
        camaraJuego.update();

        gamePort.apply();
        renderizadorMapa.setView(camaraJuego);
        renderizadorMapa.render();

        batch.setProjectionMatrix(camaraJuego.combined);
        batch.begin();

        jugador.dibujar(camaraJuego, batch);

        batch.setColor(0.5f, 0.8f, 1f, 1f);
        segundoJugador.dibujar(camaraJuego, batch);
        batch.setColor(Color.WHITE);

        for (Enemigo enemigo : enemigos) {
            enemigo.dibujar(camaraJuego, batch);
        }
        sistemaCombate.dibujarGranadas(batch);
        sistemaCombate2.dibujarGranadas(batch);

        batch.end();

        formaEnemigo.setProjectionMatrix(camaraJuego.combined);
        formaEnemigo.begin(ShapeRenderer.ShapeType.Filled);

        sistemaCombate.dibujarDisparo(formaEnemigo);
        sistemaCombate2.dibujarDisparo(formaEnemigo);

        for (ChipEnergia chip : chipsEnergia) {
            chip.dibujar(formaEnemigo);
        }

        formaEnemigo.end();

        batch.setProjectionMatrix(hud.obtenerCamara().combined);

        if (gestorPartida.terminoLaPartida()) {
            fuentePausa.getData().setScale(0.7f);
            panelResultado.dibujar(batch, fuentePausa, gestorPartida, jugador, segundoJugador, hud.obtenerCamara().viewportWidth, hud.obtenerCamara().viewportHeight);
        } else {
            hud.actualizar();
            hud.dibujar();
        }

        batch.begin();
        fuentePausa.getData().setScale(0.45f);
        fuentePausa.setColor(Color.ORANGE);
        fuentePausa.draw(batch, "SERVIDOR - Jugadores: " + hiloServidor.getCantClientes() + "/2"
                + (gestorPartida.estaPausada() ? " - Eligiendo mejoras" : "")
                + " - ESC cerrar servidor", 10, 15);
        fuentePausa.setColor(Color.WHITE);
        batch.end();
    }

    private void dibujarEspera() {

        int cuenta = hiloServidor.getCuentaRegresiva();

        dibujarTextos(Color.YELLOW,
                "SERVIDOR INICIADO - Puerto UDP " + ProtocolosRed.PUERTO_SERVIDOR,
                "IP: " + (ipsLocales.isEmpty() ? "sin red" : String.join("  ", ipsLocales)),
                cuenta > 0
                        ? "La partida empieza en " + cuenta
                        : "Esperando jugadores (" + hiloServidor.getCantClientes() + "/2)",
                "ESC - Cerrar servidor y volver al menu");
    }

    private void dibujarTextos(Color colorTitulo, String... lineas) {

        batch.setProjectionMatrix(hud.obtenerCamara().combined);
        batch.begin();

        fuentePausa.getData().setScale(0.7f);

        for (int i = 0; i < lineas.length; i++) {
            fuentePausa.setColor(i == 0 ? colorTitulo : Color.WHITE);
            fuentePausa.draw(batch, lineas[i], 30, 150 - i * 25);
        }

        fuentePausa.setColor(Color.WHITE);
        batch.end();
    }

    @Override
    public void resize(int width, int height) {

    	gamePort.update(width, height);
    	hud.resize(width, height);

    }

    @Override
    public void pause() {
    }

    @Override
    public void resume() {
    }

    @Override
    public void hide() {
    	Gdx.input.setInputProcessor(null);

    	if (hiloServidor != null) {
    	    hiloServidor.finalizarHilo();
    	}
    }

    @Override
    public void dispose() {

        if (hiloServidor != null) {
            hiloServidor.finalizarHilo();
        }

        jugador.disponer();
        segundoJugador.disponer();
        sistemaCombate.disponer();
        sistemaCombate2.disponer();

        DroneBasico.disponerTextura();
        RobotTanque.disponerTextura();
        NanoBot.disponerTextura();
        JefeMecanico.disponerTextura();

        hud.disponer();
        fuentePausa.dispose();

        mapa.dispose();
        renderizadorMapa.dispose();
        formaEnemigo.dispose();
    }
}