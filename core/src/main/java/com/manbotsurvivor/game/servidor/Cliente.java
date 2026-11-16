package com.manbotsurvivor.game.servidor;

import com.manbotsurvivor.game.elementos.ControladorEntrada;

public class Cliente {

    private DireccionRed direccionRed;
    private ControladorEntrada controladorEntrada;

    public Cliente(DireccionRed direccionRed, ControladorEntrada controladorEntrada) {
        this.direccionRed = direccionRed;
        this.controladorEntrada = controladorEntrada;
    }

    public DireccionRed getDireccionRed() {
        return direccionRed;
    }

    public ControladorEntrada getControladorEntrada() {
        return controladorEntrada;
    }
}
