package com.agrosense;

import java.sql.*;

public class AgroSenseManager {
    private static final String URL = "jdbc:sqlite:agrosense.db";

    public static void main(String[] args) {
        try (Connection conn = DriverManager.getConnection(URL)) {
            if (conn != null) {
                Statement stmt = conn.createStatement();
                
                String sqlTabla = "CREATE TABLE IF NOT EXISTS registro_ambiental (" +
                        "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                        "sensor_id TEXT NOT NULL, " +
                        "humedad REAL NOT NULL, " +
                        "temperatura REAL NOT NULL, " +
                        "fecha_registro TIMESTAMP DEFAULT CURRENT_TIMESTAMP)";
                stmt.execute(sqlTabla);

                ResultSet rsCount = stmt.executeQuery("SELECT COUNT(*) AS total FROM registro_ambiental");
                if (rsCount.next() && rsCount.getInt("total") == 0) {
                    String sqlInsert = "INSERT INTO registro_ambiental (sensor_id, humedad, temperatura) VALUES " +
                            "('Sensor_Humedad_01', 59.20, 22.10), " +
                            "('Sensor_Humedad_01', 60.50, 21.90), " +
                            "('Sensor_Humedad_01', 61.80, 21.50), " +
                            "('Sensor_Humedad_02', 48.00, 23.80), " +
                            "('Sensor_Humedad_02', 49.30, 23.60), " +
                            "('Sensor_Humedad_02', 51.10, 23.10)";
                    stmt.executeUpdate(sqlInsert);
                    System.out.println("[EXITO] Base de datos poblada correctamente desde AgroSenseManager.");
                }

                ResultSet rs = stmt.executeQuery("SELECT * FROM registro_ambiental");
                System.out.println("\n=== REGISTROS AMBIENTALES (AgroSense Manager) ===");
                while (rs.next()) {
                    System.out.printf("ID: %d | Sensor: %s | Humedad: %.2f%% | Temp: %.2f°C | Fecha: %s%n",
                            rs.getInt("id"),
                            rs.getString("sensor_id"),
                            rs.getDouble("humedad"),
                            rs.getDouble("temperatura"),
                            rs.getString("fecha_registro"));
                }
            }
        } catch (SQLException e) {
            System.out.println("Error de conexión/SQL: " + e.getMessage());
        }
    }
}
