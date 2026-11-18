package com.manbotsurvivor.game.gestores;

import java.util.List;

import com.badlogic.gdx.math.MathUtils;
import com.manbotsurvivor.game.elementos.Mapa;
import com.manbotsurvivor.game.enemigos.DroneBasico;
import com.manbotsurvivor.game.enemigos.Enemigo;
import com.manbotsurvivor.game.enemigos.JefeMecanico;
import com.manbotsurvivor.game.enemigos.NanoBot;
import com.manbotsurvivor.game.enemigos.RobotTanque;
import com.manbotsurvivor.game.jugador.Jugador;

public class GestorOleadas {

    private static final float SEGUNDOS_POR_NIVEL = 60f;
    private static final int ENEMIGOS_BASE = 8;
    private static final int ENEMIGOS_POR_NIVEL = 4;
    private static final float CRECIMIENTO_VIDA = 1.15f;
    private static final float AUMENTO_VELOCIDAD = 0.05f;
    private static final float FACTOR_VELOCIDAD_MAXIMO = 1.6f;

    private final List<Enemigo> enemigos;
    private final List<Jugador> jugadores;
    private final Mapa mapa;
    private final GestorPartida partida;

    private final float anchoMapa;
    private final float altoMapa;

    private int oleadaActual = 0;
    private float tiempoEntreOleadas = 0f;
    private boolean esperandoSiguienteOleada = false;

    private boolean jefeAparecio = false;
    private JefeMecanico jefe;

    public GestorOleadas(
            List<Enemigo> enemigos,
            List<Jugador> jugadores,
            Mapa mapa,
            GestorPartida partida,
            float anchoMapa,
            float altoMapa) {

        this.enemigos = enemigos;
        this.jugadores = jugadores;
        this.mapa = mapa;
        this.partida = partida;
        this.anchoMapa = anchoMapa;
        this.altoMapa = altoMapa;

        iniciarOleada(1);
    }

    public void actualizar(float delta) {
        if (!partida.estaEnCurso()) {
            return;
        }

        if (!jefeAparecio && partida.obtenerTiempoEnJuego() >= 900f) {
            aparecerJefe();
        }

        if (jefeAparecio) {
            if (jefe != null && !jefe.estaVivo()) {
                partida.registrarVictoria();
            }

            return;
        }

        if (!enemigos.isEmpty()) {
            esperandoSiguienteOleada = false;
            tiempoEntreOleadas = 0f;
            return;
        }

        if (!esperandoSiguienteOleada) {
            esperandoSiguienteOleada = true;
            tiempoEntreOleadas = 0f;
            return;
        }

        tiempoEntreOleadas += delta;

        if (tiempoEntreOleadas >= 2f) {
            iniciarOleada(oleadaActual + 1);
        }
    }

    private void iniciarOleada(int numero) {
        oleadaActual = numero;
        esperandoSiguienteOleada = false;
        tiempoEntreOleadas = 0f;

        int nivelDificultad = (int) (partida.obtenerTiempoEnJuego() / SEGUNDOS_POR_NIVEL) + 1;

        int cantidad = ENEMIGOS_BASE + nivelDificultad * ENEMIGOS_POR_NIVEL;

        float factorVida = (float) Math.pow(CRECIMIENTO_VIDA, nivelDificultad - 1);
        float factorVelocidad = Math.min(
                FACTOR_VELOCIDAD_MAXIMO,
                1f + (nivelDificultad - 1) * AUMENTO_VELOCIDAD);

        int separacionTanques = nivelDificultad >= 4 ? 4 : 7;

        for (int i = 0; i < cantidad; i++) {
            float[] posicion = buscarPosicionEnemigo();

            Enemigo enemigo;

            if (i % separacionTanques == separacionTanques - 1) {
                enemigo = new RobotTanque(posicion[0], posicion[1], 20, mapa);
                
            } else if (i % 2 == 0) {
                enemigo = new NanoBot(posicion[0], posicion[1], mapa);
                
            } else {
                enemigo = new DroneBasico(posicion[0], posicion[1], 50, mapa);
                
            }

            enemigo.aumentarDificultad(factorVida, factorVelocidad);
            enemigos.add(enemigo);
        }
    }

    private void aparecerJefe() {
        jefeAparecio = true;

        float[] posicion = buscarPosicionEnemigo();

        jefe = new JefeMecanico(posicion[0], posicion[1], mapa);

        enemigos.add(jefe);
    }

    private float[] buscarPosicionEnemigo() {
        float x = 0f;
        float y = 0f;

        for (int intento = 0; intento < 100; intento++) {
            x = MathUtils.random(0f, anchoMapa - 48f);
            y = MathUtils.random(0f, altoMapa - 48f);

            if (mapa.hayColision(x, y, 32, 32)) {
                continue;
            }

            boolean lejosDeTodos = true;

            for (Jugador jugador : jugadores) {
                float dx = x - jugador.obtenerPosicionX();
                float dy = y - jugador.obtenerPosicionY();

                if (dx * dx + dy * dy < 150f * 150f) {
                    lejosDeTodos = false;
                    break;
                }
            }

            if (lejosDeTodos) {
                return new float[] { x, y };
            }
        }

        return new float[] { x, y };
    }

    public int obtenerOleadaActual() {
        return oleadaActual;
    }

    public boolean jefeAparecio() {
        return jefeAparecio;
    }

    public JefeMecanico obtenerJefe() {
        return jefe;
    }
}