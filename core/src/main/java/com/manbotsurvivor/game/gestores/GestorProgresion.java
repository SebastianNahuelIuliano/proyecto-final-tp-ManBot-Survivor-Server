package com.manbotsurvivor.game.gestores;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.manbotsurvivor.game.elementos.SistemaCombate;
import com.manbotsurvivor.game.jugador.Jugador;
import com.manbotsurvivor.game.jugador.Mejora;

public class GestorProgresion {

    private final Jugador jugador;
    private final SistemaCombate combate;
    private final GestorPartida partida;

    private final List<Mejora> opciones = new ArrayList<>();

    private boolean seleccionActiva;

    // Cada seleccion tiene un id distinto. El cliente manda "Mejora_id_opcion":
    // si el paquete llega repetido o atrasado, el id no coincide y se ignora.
    private int idSeleccion;

    public GestorProgresion(
            Jugador jugador,
            SistemaCombate combate,
            GestorPartida partida) {

        this.jugador = jugador;
        this.combate = combate;
        this.partida = partida;
    }

    public void actualizar() {
        if (seleccionActiva || !jugador.hayNivelPendiente() || partida.terminoLaPartida()) {
            return;
        }

        // El tiempo se detiene para los dos jugadores mientras alguno elige
        if (partida.estaEnCurso()) {
            partida.pausar();
        }

        generarOpciones();
        seleccionActiva = true;
    }

    private void generarOpciones() {
        List<Mejora> disponibles = new ArrayList<>();

        if (!combate.obtenerLaser().estaAlMaximo()) {
            disponibles.add(Mejora.MEJORAR_LASER);
        }

        if (!combate.obtenerGranada().estaAlMaximo()) {
            disponibles.add(Mejora.MEJORAR_GRANADA);
        }

        disponibles.add(Mejora.RECUPERAR_VIDA);
        disponibles.add(Mejora.AUMENTAR_VELOCIDAD);

        Collections.shuffle(disponibles);

        opciones.clear();

        for (int i = 0; i < 3 && i < disponibles.size(); i++) {
            opciones.add(disponibles.get(i));
        }

        idSeleccion++;
    }

    // La partida no se reanuda aca: la reanuda PantallaJuego cuando
    // ninguno de los dos jugadores tiene una seleccion pendiente.
    public void elegir(int idSeleccionRecibida, int numero) {
        if (!seleccionActiva || idSeleccionRecibida != idSeleccion || numero < 1 || numero > opciones.size()) {
            return;
        }

        aplicar(opciones.get(numero - 1));

        jugador.consumirNivelPendiente();
        seleccionActiva = false;

        if (jugador.hayNivelPendiente()) {
            generarOpciones();
            seleccionActiva = true;
        }
    }

    // Si el jugador se desconecta mientras elige, se cancela para no dejar la partida pausada
    public void cancelarSeleccion() {
        seleccionActiva = false;
        opciones.clear();
    }

    private void aplicar(Mejora mejora) {
        switch (mejora) {
            case MEJORAR_LASER:
                combate.obtenerLaser().mejorar();
                break;

            case MEJORAR_GRANADA:
                combate.obtenerGranada().mejorar();
                break;

            case RECUPERAR_VIDA:
                jugador.curar(30);
                break;

            case AUMENTAR_VELOCIDAD:
                jugador.aumentarVelocidad(10f);
                break;

            default:
                break;
        }
    }

    public boolean haySeleccionActiva() {
        return seleccionActiva;
    }

    public int obtenerIdSeleccion() {
        return idSeleccion;
    }

    public List<Mejora> obtenerOpciones() {
        return Collections.unmodifiableList(opciones);
    }

}
