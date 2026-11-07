package com.agrosense;

import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.TemporalAdjusters;

public class CalendarioRiego {

    public static final String[] NOMBRES_DIAS = {
            "Lunes", "Martes", "Miércoles", "Jueves", "Viernes", "Sábado", "Domingo"
    };

    private final int id;
    private final DayOfWeek diaSemana;
    private final LocalTime hora;
    private final String descripcion;
    private final boolean activo;

    public CalendarioRiego(int id, DayOfWeek diaSemana, LocalTime hora, String descripcion, boolean activo) {
        this.id = id;
        this.diaSemana = diaSemana;
        this.hora = hora;
        this.descripcion = descripcion == null ? "" : descripcion;
        this.activo = activo;
    }

    public int getId() {
        return id;
    }

    public DayOfWeek getDiaSemana() {
        return diaSemana;
    }

    public LocalTime getHora() {
        return hora;
    }

    public String getDescripcion() {
        return descripcion;
    }

    public boolean isActivo() {
        return activo;
    }

    public String getNombreDia() {
        return NOMBRES_DIAS[diaSemana.getValue() - 1];
    }

    public LocalDateTime proximaOcurrencia(LocalDateTime desde) {
        LocalDateTime proxima = desde.toLocalDate()
                .with(TemporalAdjusters.nextOrSame(diaSemana))
                .atTime(hora);
        if (proxima.isBefore(desde)) {
            proxima = proxima.plusWeeks(1);
        }
        return proxima;
    }

    @Override
    public String toString() {
        return getNombreDia() + " " + hora;
    }
}
