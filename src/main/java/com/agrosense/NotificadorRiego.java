package com.agrosense;

import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class NotificadorRiego {

    private static final int INTERVALO_SEGUNDOS = 30;

    private static final int TOLERANCIA_MINUTOS = 2;
    private static final DateTimeFormatter FORMATO_HORA = DateTimeFormatter.ofPattern("HH:mm");

    private final CalendarioRiegoDAO dao;
    private ScheduledExecutorService planificador;

    public NotificadorRiego(CalendarioRiegoDAO dao) {
        this.dao = dao;
    }

    public void iniciar() {
        if (planificador != null && !planificador.isShutdown()) {
            return;
        }
        planificador = Executors.newSingleThreadScheduledExecutor(tarea -> {
            Thread hilo = new Thread(tarea, "notificador-riego");
            hilo.setDaemon(true); // no impide que el programa termine
            return hilo;
        });
        planificador.scheduleAtFixedRate(this::verificarSeguro, 0, INTERVALO_SEGUNDOS, TimeUnit.SECONDS);
    }

    public void detener() {
        if (planificador != null) {
            planificador.shutdownNow();
        }
    }

    private void verificarSeguro() {
        try {
            verificar(LocalDateTime.now());
        } catch (Exception e) {
            System.out.println("[Notificador] Error al revisar el calendario: " + e.getMessage());
        }
    }

    public int verificar(LocalDateTime ahora) throws SQLException {
        int enviadas = 0;
        for (CalendarioRiego riego : dao.listarActivos()) {
            if (riego.getDiaSemana() != ahora.getDayOfWeek()) {
                continue;
            }
            LocalDateTime programado = ahora.toLocalDate().atTime(riego.getHora());
            boolean llegoLaHora = !programado.isAfter(ahora);
            boolean dentroDeVentana = programado.isAfter(ahora.minusMinutes(TOLERANCIA_MINUTOS));
            if (!llegoLaHora || !dentroDeVentana) {
                continue;
            }

            String mensaje = "Es hora de regar las orquídeas (" + riego.getNombreDia() + " "
                    + riego.getHora().format(FORMATO_HORA) + ")";
            if (!riego.getDescripcion().isEmpty()) {
                mensaje += " - " + riego.getDescripcion();
            }

            if (dao.registrarNotificacion(riego.getId(), ahora.toLocalDate(), mensaje)) {
                mostrarNotificacion(mensaje);
                enviadas++;
            }
        }
        return enviadas;
    }

    public void notificarPrueba() {
        mostrarNotificacion("Esta es una notificación de prueba. Así se verá el aviso de riego.");
    }

    private void mostrarNotificacion(String mensaje) {
        System.out.println();
        System.out.println("\007==============================================================");
        System.out.println("  [RECORDATORIO DE RIEGO]");
        System.out.println("  " + mensaje);
        System.out.println("==============================================================");
    }
}