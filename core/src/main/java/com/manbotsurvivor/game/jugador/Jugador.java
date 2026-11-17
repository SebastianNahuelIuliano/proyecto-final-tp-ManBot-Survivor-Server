package com.manbotsurvivor.game.jugador;

import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.manbotsurvivor.game.elementos.ControladorEntrada;
import com.manbotsurvivor.game.elementos.Mapa;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.Animation;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.graphics.OrthographicCamera;

public class Jugador {
	
	private float posicionX;
	private float posicionY;
	private float velocidad;
	private float ancho;
	private float alto;
	private int experiencia;
	private int nivel = 1;
	private int experienciaNecesaria = 100;
	private int nivelesPendientes = 0;
	private int enemigosEliminados;
	private final VidaCompartida vidaEquipo;
	private boolean conectado;
	private boolean moviendose;
	
	private float limiteMapaAncho;
	private float limiteMapaAlto;
	
	private Texture texturaJugador;
	private Animation<TextureRegion> animacionJugador;
	private float tiempoAnimacion;
	private boolean mirandoDerecha;
	private ControladorEntrada controladorEntrada;
	private Mapa mapa;
	
	public Jugador(float posicionX, float posicionY, float velocidad, ControladorEntrada controladorEntrada, float limiteMapaAncho, float limiteMapaAlto, Mapa mapa, VidaCompartida vidaEquipo) {

	    this.posicionX = posicionX;
	    this.posicionY = posicionY;
	    this.velocidad = velocidad;
	    this.controladorEntrada = controladorEntrada;
	    this.limiteMapaAncho = limiteMapaAncho;
	    this.limiteMapaAlto = limiteMapaAlto;
	    this.mapa = mapa;
	    this.ancho = 32;
	    this.alto = 32;
	    this.experiencia = 0;
	    this.enemigosEliminados = 0;
	    this.vidaEquipo = vidaEquipo;
	    this.conectado = true;
	    this.moviendose = false;
	    
	    texturaJugador = new Texture("manbot.png");

	    TextureRegion[][] cuadros = TextureRegion.split(texturaJugador, 64, 64);

	    TextureRegion[] cuadrosAnimacion = new TextureRegion[5];

	    for (int i = 0; i < cuadrosAnimacion.length; i++) {
	        cuadrosAnimacion[i] = cuadros[0][i];
	    }

	    animacionJugador = new Animation<TextureRegion>(0.15f, cuadrosAnimacion);

	    animacionJugador.setPlayMode(Animation.PlayMode.LOOP_PINGPONG);

	    tiempoAnimacion = 0;
	    mirandoDerecha = true;
	    
	}
	
	public float obtenerPosicionX() {
	    return posicionX;
	}

	public float obtenerPosicionY() {
	    return posicionY;
	}
	
	public float obtenerAncho() {
	    return ancho;
	}

	public float obtenerAlto() {
	    return alto;
	}
	
	public int ganarExperiencia(int cantidad) {
		 if (cantidad <= 0) {
		        return 0;
		    }
	    experiencia += cantidad;
	    int nivelesGanados = 0;
	    
	    while (experiencia >= experienciaNecesaria) {
	        experiencia -= experienciaNecesaria;
	        nivel++;
	        nivelesPendientes++;
	        nivelesGanados++;
	    }
	    
	    return nivelesGanados;
	}
	
	public int obtenerExperiencia() {
	    return experiencia;
	}
	
	public int obtenerNivel() {
	    return nivel;
	}

	public int obtenerExperienciaNecesaria() {
	    return experienciaNecesaria;
	}

	public boolean hayNivelPendiente() {
	    return nivelesPendientes > 0;
	}

	public void consumirNivelPendiente() {
	    if (nivelesPendientes > 0) {
	        nivelesPendientes--;
	    }
	}
	
	public void registrarEnemigoEliminado() {
	    enemigosEliminados++;
	}

	public int obtenerEnemigosEliminados() {
	    return enemigosEliminados;
	}
	
	public void recibirDaño(int daño) {
	    vidaEquipo.recibirDaño(daño);
	}
	
	public int obtenerVida() {
	    return vidaEquipo.obtenerVida();
	}
	
	
	public boolean estaVivo() {
		return conectado && vidaEquipo.estaViva();
	}
	
	public int obtenerVidaMaxima() {
	    return vidaEquipo.obtenerVidaMaxima();
	}
	
	public void curar(int cantidad) {
	    vidaEquipo.curar(cantidad);
	}
	
	public boolean estaConectado() {
	    return conectado;
	}
	
	public void desconectar() {
	    conectado = false;
	    moviendose = false;
	    controladorEntrada.aplicarTeclasRemotas(false, false, false, false);
	}
	
	public void reconectar(float nuevaPosicionX, float nuevaPosicionY) {
	    conectado = true;
	    posicionX = nuevaPosicionX;
	    posicionY = nuevaPosicionY;
	}
	
	public boolean estaMoviendose() {
	    return moviendose;
	}
	
	public boolean estaMirandoDerecha() {
	    return mirandoDerecha;
	}
	
	public void aumentarVelocidad(float cantidad) {
	    if (cantidad > 0) {
	        velocidad += cantidad;
	    }
	}

	public void mover(float movimientoX, float movimientoY) {

	    float nuevaPosicionX = posicionX + movimientoX;
	    float nuevaPosicionY = posicionY + movimientoY;

	    if (!mapa.hayColision(nuevaPosicionX, posicionY, ancho, alto)) {
	        posicionX = nuevaPosicionX;
	    }

	    if (!mapa.hayColision(posicionX, nuevaPosicionY, ancho, alto)) {
	        posicionY = nuevaPosicionY;
	    }

	    if (posicionX < 0) {
	        posicionX = 0;
	    }

	    if (posicionY < 0) {
	        posicionY = 0;
	    }

	    if (posicionX + ancho > limiteMapaAncho) {
	        posicionX = limiteMapaAncho - ancho;
	    }

	    if (posicionY + alto > limiteMapaAlto) {
	        posicionY = limiteMapaAlto - alto;
	    }

	}
	
	public void actualizar(float delta) {
		
		if (!estaVivo()) {
			moviendose = false;
	        return;
	    }

	    float movimientoX = 0;
	    float movimientoY = 0;

	    if (controladorEntrada.estaArriba() ||
	        controladorEntrada.consumirPulsacionArriba()) {

	    	 movimientoY += 1;
	    }

	    if (controladorEntrada.estaAbajo() ||
	        controladorEntrada.consumirPulsacionAbajo()) {

	    	movimientoY -= 1;
	    }

	    if (controladorEntrada.estaIzquierda() ||
	        controladorEntrada.consumirPulsacionIzquierda()) {

	    	movimientoX -= 1;
	        mirandoDerecha = false;
	    }

	    if (controladorEntrada.estaDerecha() ||
	        controladorEntrada.consumirPulsacionDerecha()) {

	    	movimientoX += 1;
	        mirandoDerecha = true;
	    }
	    
	    float longitudMovimiento = (float) Math.sqrt(movimientoX * movimientoX + movimientoY * movimientoY);

	    if (longitudMovimiento > 0) {
	        movimientoX = movimientoX / longitudMovimiento * velocidad * delta;
	        movimientoY = movimientoY / longitudMovimiento * velocidad * delta;
	    }

	    moviendose = movimientoX != 0 || movimientoY != 0;

	    if (moviendose) {
	        tiempoAnimacion += delta;
	    } else {
	        tiempoAnimacion = 0;
	    }

	    mover(movimientoX, movimientoY);
	}
	
	public void dibujar(OrthographicCamera camaraJuego, SpriteBatch lote) {
		
		 if (!estaVivo()) {
		        return;
		    }

	    TextureRegion cuadroActual = new TextureRegion(animacionJugador.getKeyFrame(tiempoAnimacion));

	    if (!mirandoDerecha) {
	        cuadroActual.flip(true, false);
	    }
	    
	    lote.draw(cuadroActual, posicionX, posicionY, ancho, alto);
	}
	
	public void disponer() {
	    texturaJugador.dispose();
	}
	
}
