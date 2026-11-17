package com.manbotsurvivor.game.jugador;

	//Si un jugador recibe daño baja la vida de los dos, y si llega a 0 pierden los dos.
	public class VidaCompartida {
	
	 private final int VIDA_MAXIMA;
	 private int vida;
	
	 public VidaCompartida(int vidaMaxima) {
	     this.VIDA_MAXIMA = vidaMaxima;
	     this.vida = vidaMaxima;
	 }
	
	 public void recibirDaño(int daño) {
	     vida -= daño;
	
	     if (vida < 0) {
	         vida = 0;
	     }
	 }
	
	 public void curar(int cantidad) {
	     if (cantidad <= 0) {
	         return;
	     }
	
	     vida = Math.min(VIDA_MAXIMA, vida + cantidad);
	 }
	
	 public int obtenerVida() {
	     return vida;
	 }
	
	 public int obtenerVidaMaxima() {
	     return VIDA_MAXIMA;
	 }
	
	 public boolean estaViva() {
	     return vida > 0;
	 }
	}
