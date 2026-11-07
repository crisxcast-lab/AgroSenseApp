package com.agrosense;

import java.sql.SQLException;
import java.text.Normalizer;
import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;

/**
 * Menú de consola para el Avance #2:
 *  - RF1: Establecer calendario de riego
 *  - RF2: Notificar hora de riego (el notificador corre en segundo plano mientras el menú está abierto)
 *
 * Para ejecutarlo: correr el main de esta clase.
 */
public class MenuRiego {
    private static final DateTimeFormatter FORMATO_HORA_ENTRADA = DateTimeFormatter.ofPattern("H:mm");
    private static final DateTimeFormatter FORMATO_FECHA_HORA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    public static void main(String[] args) {
        iniciar();
    }

    public static void iniciar() {
        CalendarioRiegoDAO dao;
        try {
            dao = new CalendarioRiegoDAO();
        } catch (SQLException e) {
            System.out.println("Error de conexión/SQL: " + e.getMessage());
            return;
        }

        NotificadorRiego notificador = new NotificadorRiego(dao);
        notificador.iniciar(); // RF2: queda revisando el calendario en segundo plano

        Scanner sc = new Scanner(System.in);
        while (true) {
            mostrarMenu();
            String opcion = leer(sc, "Seleccione una opción: ");
            if (opcion == null || opcion.equals("0")) {
                break;
            }
            try {
                switch (opcion) {
                    case "1" -> establecerCalendario(sc, dao);
                    case "2" -> verCalendario(dao);
                    case "3" -> cambiarEstado(sc, dao);
                    case "4" -> eliminarRiego(sc, dao);
                    case "5" -> verNotificaciones(dao);
                    case "6" -> notificador.notificarPrueba();
                    default -> System.out.println("Opción no válida.");
                }
            } catch (SQLException e) {
                System.out.println("Error de base de datos: " + e.getMessage());
            }
        }

        notificador.detener();
        System.out.println("Hasta pronto.");
    }

    private static void mostrarMenu() {
        System.out.println("\n=== AGROSENSE - CALENDARIO DE RIEGO ===");
        System.out.println("1. Establecer calendario de riego");
        System.out.println("2. Ver calendario de riego");
        System.out.println("3. Activar / desactivar un riego");
        System.out.println("4. Eliminar un riego");
        System.out.println("5. Ver notificaciones enviadas");
        System.out.println("6. Probar notificación");
        System.out.println("0. Salir");
        System.out.println("(Los recordatorios de riego se avisan automáticamente mientras el programa está abierto)");
    }

    private static void establecerCalendario(Scanner sc, CalendarioRiegoDAO dao) throws SQLException {
        System.out.println("\n--- Establecer calendario de riego ---");
        System.out.println("(Deje un campo vacío para cancelar)");

        Set<DayOfWeek> dias = pedirDias(sc);
        if (dias == null) {
            System.out.println("Operación cancelada.");
            return;
        }
        Set<LocalTime> horas = pedirHoras(sc);
        if (horas == null) {
            System.out.println("Operación cancelada.");
            return;
        }
        String descripcion = leer(sc, "Descripción (opcional, ej: Riego de orquídeas): ");
        if (descripcion == null) {
            descripcion = "";
        }

        System.out.println("\nSe programará el riego para:");
        for (DayOfWeek dia : dias) {
            System.out.println("  - " + CalendarioRiego.NOMBRES_DIAS[dia.getValue() - 1] + ": " + horas);
        }
        String confirmacion = leer(sc, "¿Guardar el calendario? (s/n): ");
        if (confirmacion == null || !confirmacion.equalsIgnoreCase("s")) {
            System.out.println("No se guardó nada.");
            return;
        }

        int nuevos = 0;
        int repetidos = 0;
        for (DayOfWeek dia : dias) {
            for (LocalTime hora : horas) {
                if (dao.guardar(dia, hora, descripcion)) {
                    nuevos++;
                } else {
                    repetidos++;
                }
            }
        }
        System.out.println("[EXITO] Calendario de riego guardado: " + nuevos + " riego(s) nuevo(s)."
                + (repetidos > 0 ? " (" + repetidos + " ya existía(n) y se omitió)" : ""));
    }

    private static Set<DayOfWeek> pedirDias(Scanner sc) {
        while (true) {
            String entrada = leer(sc, "Días de riego (1=Lunes ... 7=Domingo, nombres o 'todos'; ej: 1,3,5 o lunes,jueves): ");
            if (entrada == null || entrada.isEmpty()) {
                return null;
            }
            try {
                return parsearDias(entrada);
            } catch (IllegalArgumentException e) {
                System.out.println("  Entrada inválida: " + e.getMessage());
            }
        }
    }

    private static Set<LocalTime> pedirHoras(Scanner sc) {
        while (true) {
            String entrada = leer(sc, "Horas de riego (formato HH:mm; ej: 08:00 o 08:00,19:30): ");
            if (entrada == null || entrada.isEmpty()) {
                return null;
            }
            try {
                return parsearHoras(entrada);
            } catch (IllegalArgumentException e) {
                System.out.println("  Entrada inválida: " + e.getMessage());
            }
        }
    }

    static Set<DayOfWeek> parsearDias(String texto) {
        Set<DayOfWeek> dias = EnumSet.noneOf(DayOfWeek.class);
        for (String token : texto.split("[,;\\s]+")) {
            if (token.isEmpty()) {
                continue;
            }
            String t = normalizar(token);
            if (t.equals("todos") || t.equals("todo")) {
                dias.addAll(EnumSet.allOf(DayOfWeek.class));
            } else if (t.matches("[1-7]")) {
                dias.add(DayOfWeek.of(Integer.parseInt(t)));
            } else if (t.length() >= 3) {
                DayOfWeek encontrado = null;
                for (DayOfWeek dia : DayOfWeek.values()) {
                    // Acepta el nombre completo o una abreviatura (lun, mar, mie, ...)
                    if (normalizar(CalendarioRiego.NOMBRES_DIAS[dia.getValue() - 1]).startsWith(t)) {
                        encontrado = dia;
                    }
                }
                if (encontrado == null) {
                    throw new IllegalArgumentException("'" + token + "' no es un día válido");
                }
                dias.add(encontrado);
            } else {
                throw new IllegalArgumentException("'" + token + "' no es un día válido");
            }
        }
        if (dias.isEmpty()) {
            throw new IllegalArgumentException("debe indicar al menos un día");
        }
        return dias;
    }

    static Set<LocalTime> parsearHoras(String texto) {
        Set<LocalTime> horas = new TreeSet<>();
        for (String token : texto.split("[,;\\s]+")) {
            if (token.isEmpty()) {
                continue;
            }
            try {
                horas.add(LocalTime.parse(token, FORMATO_HORA_ENTRADA));
            } catch (DateTimeParseException e) {
                throw new IllegalArgumentException("'" + token + "' no es una hora válida (use HH:mm, ej: 08:30)");
            }
        }
        if (horas.isEmpty()) {
            throw new IllegalArgumentException("debe indicar al menos una hora");
        }
        return horas;
    }

    private static String normalizar(String s) {
        return Normalizer.normalize(s, Normalizer.Form.NFD).replaceAll("\\p{M}", "").toLowerCase();
    }

    private static void verCalendario(CalendarioRiegoDAO dao) throws SQLException {
        List<CalendarioRiego> calendario = dao.listar();
        System.out.println("\n=== CALENDARIO DE RIEGO ===");
        if (calendario.isEmpty()) {
            System.out.println("Aún no hay riegos programados. Use la opción 1 para crearlos.");
            return;
        }
        System.out.printf("%-4s %-10s %-6s %-9s %s%n", "ID", "Día", "Hora", "Estado", "Descripción");
        for (CalendarioRiego r : calendario) {
            System.out.printf("%-4d %-10s %-6s %-9s %s%n",
                    r.getId(), r.getNombreDia(), r.getHora(),
                    r.isActivo() ? "Activo" : "Inactivo", r.getDescripcion());
        }

        LocalDateTime ahora = LocalDateTime.now();
        LocalDateTime proximo = null;
        for (CalendarioRiego r : calendario) {
            if (r.isActivo()) {
                LocalDateTime candidato = r.proximaOcurrencia(ahora);
                if (proximo == null || candidato.isBefore(proximo)) {
                    proximo = candidato;
                }
            }
        }
        System.out.println(proximo == null
                ? "\nNo hay riegos activos."
                : "\nPróximo riego: " + proximo.format(FORMATO_FECHA_HORA));
    }


    private static void cambiarEstado(Scanner sc, CalendarioRiegoDAO dao) throws SQLException {
        verCalendario(dao);
        if (dao.listar().isEmpty()) {
            return;
        }
        Integer id = pedirId(sc, "\nID del riego a activar/desactivar (vacío para cancelar): ");
        if (id == null) {
            return;
        }
        for (CalendarioRiego r : dao.listar()) {
            if (r.getId() == id) {
                boolean nuevoEstado = !r.isActivo();
                dao.cambiarEstado(id, nuevoEstado);
                System.out.println("Riego " + r + " ahora está " + (nuevoEstado ? "ACTIVO." : "INACTIVO."));
                return;
            }
        }
        System.out.println("No existe un riego con ID " + id + ".");
    }

    private static void eliminarRiego(Scanner sc, CalendarioRiegoDAO dao) throws SQLException {
        verCalendario(dao);
        if (dao.listar().isEmpty()) {
            return;
        }
        Integer id = pedirId(sc, "\nID del riego a eliminar (vacío para cancelar): ");
        if (id == null) {
            return;
        }
        System.out.println(dao.eliminar(id) ? "Riego eliminado." : "No existe un riego con ID " + id + ".");
    }

    private static Integer pedirId(Scanner sc, String mensaje) {
        String entrada = leer(sc, mensaje);
        if (entrada == null || entrada.isEmpty()) {
            return null;
        }
        try {
            return Integer.parseInt(entrada);
        } catch (NumberFormatException e) {
            System.out.println("ID inválido.");
            return null;
        }
    }

    private static void verNotificaciones(CalendarioRiegoDAO dao) throws SQLException {
        List<String> notificaciones = dao.listarNotificaciones(20);
        System.out.println("\n=== NOTIFICACIONES ENVIADAS (últimas 20) ===");
        if (notificaciones.isEmpty()) {
            System.out.println("Aún no se ha enviado ninguna notificación.");
        }
        for (String n : notificaciones) {
            System.out.println(n);
        }
    }

    private static String leer(Scanner sc, String mensaje) {
        System.out.print(mensaje);
        return sc.hasNextLine() ? sc.nextLine().trim() : null;
    }
}
