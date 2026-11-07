package com.agrosense;

import java.sql.*;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;


public class CalendarioRiegoDAO {
    private static final String URL_POR_DEFECTO = "jdbc:sqlite:agrosense.db";

    private final String url;

    public CalendarioRiegoDAO() throws SQLException {
        this(URL_POR_DEFECTO);
    }

    public CalendarioRiegoDAO(String url) throws SQLException {
        this.url = url;
        crearTablas();
    }

    private Connection conectar() throws SQLException {
        return DriverManager.getConnection(url);
    }

    private void crearTablas() throws SQLException {
        try (Connection conn = conectar(); Statement stmt = conn.createStatement()) {
            stmt.execute("CREATE TABLE IF NOT EXISTS calendario_riego (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    "dia_semana INTEGER NOT NULL CHECK (dia_semana BETWEEN 1 AND 7), " +
                    "hora TEXT NOT NULL, " +
                    "descripcion TEXT, " +
                    "activo INTEGER NOT NULL DEFAULT 1, " +
                    "fecha_creacion TIMESTAMP DEFAULT CURRENT_TIMESTAMP, " +
                    "UNIQUE (dia_semana, hora))");

            // Bitácora de avisos: evita avisar dos veces el mismo riego el mismo día
            stmt.execute("CREATE TABLE IF NOT EXISTS notificacion_riego (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    "calendario_id INTEGER NOT NULL, " +
                    "fecha_riego TEXT NOT NULL, " +
                    "mensaje TEXT NOT NULL, " +
                    "fecha_envio TIMESTAMP DEFAULT CURRENT_TIMESTAMP, " +
                    "UNIQUE (calendario_id, fecha_riego))");
        }
    }

    public synchronized boolean guardar(DayOfWeek dia, LocalTime hora, String descripcion) throws SQLException {
        String sql = "INSERT OR IGNORE INTO calendario_riego (dia_semana, hora, descripcion) VALUES (?, ?, ?)";
        try (Connection conn = conectar(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, dia.getValue());
            ps.setString(2, hora.toString());
            ps.setString(3, descripcion);
            return ps.executeUpdate() > 0;
        }
    }

    public synchronized List<CalendarioRiego> listar() throws SQLException {
        return consultar("SELECT * FROM calendario_riego ORDER BY dia_semana, hora");
    }

    public synchronized List<CalendarioRiego> listarActivos() throws SQLException {
        return consultar("SELECT * FROM calendario_riego WHERE activo = 1 ORDER BY dia_semana, hora");
    }

    private List<CalendarioRiego> consultar(String sql) throws SQLException {
        List<CalendarioRiego> lista = new ArrayList<>();
        try (Connection conn = conectar();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                lista.add(new CalendarioRiego(
                        rs.getInt("id"),
                        DayOfWeek.of(rs.getInt("dia_semana")),
                        LocalTime.parse(rs.getString("hora")),
                        rs.getString("descripcion"),
                        rs.getInt("activo") == 1));
            }
        }
        return lista;
    }

    public synchronized boolean cambiarEstado(int id, boolean activo) throws SQLException {
        String sql = "UPDATE calendario_riego SET activo = ? WHERE id = ?";
        try (Connection conn = conectar(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, activo ? 1 : 0);
            ps.setInt(2, id);
            return ps.executeUpdate() > 0;
        }
    }

    public synchronized boolean eliminar(int id) throws SQLException {
        try (Connection conn = conectar()) {
            conn.setAutoCommit(false);
            try (PreparedStatement psNot = conn.prepareStatement("DELETE FROM notificacion_riego WHERE calendario_id = ?");
                 PreparedStatement psCal = conn.prepareStatement("DELETE FROM calendario_riego WHERE id = ?")) {
                psNot.setInt(1, id);
                psNot.executeUpdate();
                psCal.setInt(1, id);
                boolean borrado = psCal.executeUpdate() > 0;
                conn.commit();
                return borrado;
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            }
        }
    }

    public synchronized boolean registrarNotificacion(int calendarioId, LocalDate fecha, String mensaje) throws SQLException {
        String sql = "INSERT OR IGNORE INTO notificacion_riego (calendario_id, fecha_riego, mensaje) VALUES (?, ?, ?)";
        try (Connection conn = conectar(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, calendarioId);
            ps.setString(2, fecha.toString());
            ps.setString(3, mensaje);
            return ps.executeUpdate() > 0;
        }
    }

    public synchronized List<String> listarNotificaciones(int limite) throws SQLException {
        List<String> lista = new ArrayList<>();
        String sql = "SELECT fecha_envio, mensaje FROM notificacion_riego ORDER BY id DESC LIMIT ?";
        try (Connection conn = conectar(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, limite);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    lista.add(rs.getString("fecha_envio") + " | " + rs.getString("mensaje"));
                }
            }
        }
        return lista;
    }
}
