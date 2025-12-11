package com.mycompany.clinicadashboard;

import javax.swing.*;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class ClinicaDashboard extends JFrame {

    private final JTable table;
    private final DefaultTableModel model;
    private final List<Microservice> services = new ArrayList<>();
    private final JLabel statusLabel = new JLabel("Listo - Haz doble clic en la ruta para editarla.");

    // IMPORTANTE: Ajusta esto ("." o "..") según dónde esté tu carpeta del dashboard respecto a los proyectos
    private static final String ROOT_PATH = "..";

    public ClinicaDashboard() {
        setTitle("Panel de Control - Clínica v3.0 (Con Atención)");
        setSize(1100, 750);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(new BorderLayout());

        // 1. Definir los servicios
        initServices();

        // 2. Configurar Tabla
        String[] columns = {"Servicio", "Estado", "PID", "Ruta JAR (Editable)"};

        model = new DefaultTableModel(columns, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return column == 3; // Solo la ruta es editable
            }
        };

        table = new JTable(model);
        table.setRowHeight(35);
        table.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

        // Listener para guardar cambios cuando editas la ruta
        model.addTableModelListener(e -> {
            if (e.getType() == javax.swing.event.TableModelEvent.UPDATE) {
                int row = e.getFirstRow();
                int col = e.getColumn();
                if (col == 3) {
                    String newPath = (String) model.getValueAt(row, col);
                    services.get(row).jarPath = newPath;
                    statusLabel.setText("Ruta actualizada para: " + services.get(row).name);
                }
            }
        });

        // Configurar ancho de columnas
        table.getColumnModel().getColumn(0).setPreferredWidth(200); // Nombre
        table.getColumnModel().getColumn(1).setPreferredWidth(120); // Estado
        table.getColumnModel().getColumn(2).setPreferredWidth(80);  // PID
        table.getColumnModel().getColumn(3).setPreferredWidth(500); // Ruta

        updateTableUI(); // Carga inicial

        // Renderizado de colores y Tooltips
        table.setDefaultRenderer(Object.class, new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
                Component c = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);

                Microservice service = services.get(row);
                String statusText = (String) model.getValueAt(row, 1);

                // Lógica de Colores
                if ("CORRIENDO".equals(statusText)) {
                    c.setForeground(new Color(0, 150, 0)); // Verde oscuro
                    c.setFont(c.getFont().deriveFont(Font.BOLD));
                    setToolTipText("El servicio está funcionando correctamente.");
                } else if (statusText.startsWith("ERROR")) {
                    c.setForeground(Color.RED); // Rojo para errores
                    c.setFont(c.getFont().deriveFont(Font.BOLD));
                    // Mostrar las últimas líneas del log en el tooltip
                    setToolTipText("<html><b>Error detectado:</b><br>" + service.getLastLogLines() + "</html>");
                } else if ("INICIANDO...".equals(statusText)) {
                    c.setForeground(Color.ORANGE);
                    c.setFont(c.getFont().deriveFont(Font.ITALIC));
                    setToolTipText("Esperando arranque...");
                } else {
                    c.setForeground(Color.BLACK);
                    c.setFont(c.getFont().deriveFont(Font.PLAIN));
                    setToolTipText(null);
                }

                // Color de fondo selección
                if (isSelected) {
                    c.setBackground(new Color(220, 240, 255));
                } else {
                    c.setBackground(Color.WHITE);
                }

                return c;
            }
        });

        // Menú Contextual (Clic Derecho)
        JPopupMenu popupMenu = new JPopupMenu();
        JMenuItem startItem = new JMenuItem("▶ Iniciar este servicio");
        JMenuItem stopItem = new JMenuItem("⏹ Detener este servicio");
        JMenuItem logItem = new JMenuItem("📄 Ver último log (Consola)");

        startItem.addActionListener(e -> actionOnSelected(true));
        stopItem.addActionListener(e -> actionOnSelected(false));
        logItem.addActionListener(e -> showLogDialog());

        popupMenu.add(startItem);
        popupMenu.add(stopItem);
        popupMenu.addSeparator();
        popupMenu.add(logItem);
        table.setComponentPopupMenu(popupMenu);

        // Seleccionar fila al hacer clic derecho
        table.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                if (SwingUtilities.isRightMouseButton(e)) {
                    int row = table.rowAtPoint(e.getPoint());
                    if (row >= 0 && row < table.getRowCount()) {
                        table.setRowSelectionInterval(row, row);
                    }
                }
            }
        });

        JScrollPane scrollPane = new JScrollPane(table);
        add(scrollPane, BorderLayout.CENTER);

        // 3. Panel de Control (Botonera)
        JPanel controlPanel = new JPanel();
        controlPanel.setLayout(new BoxLayout(controlPanel, BoxLayout.Y_AXIS));

        // Grupo: Acciones Globales
        JPanel groupAll = new JPanel(new FlowLayout(FlowLayout.CENTER, 15, 10));
        groupAll.setBorder(BorderFactory.createTitledBorder("Control Global"));
        JButton btnStartAll = new JButton("▶ INICIAR TODO (Secuencia)");
        JButton btnStopAll = new JButton("⏹ DETENER TODO");

        btnStartAll.setBackground(new Color(200, 255, 200));
        btnStopAll.setBackground(new Color(255, 200, 200));

        groupAll.add(btnStartAll);
        groupAll.add(btnStopAll);

        // Grupo: Acciones Individuales
        JPanel groupSingle = new JPanel(new FlowLayout(FlowLayout.CENTER, 15, 10));
        groupSingle.setBorder(BorderFactory.createTitledBorder("Control Individual"));
        JButton btnStartOne = new JButton("▶ Iniciar");
        JButton btnStopOne = new JButton("⏹ Detener");
        JButton btnRefresh = new JButton("🔄 Refrescar Tabla");

        groupSingle.add(btnStartOne);
        groupSingle.add(btnStopOne);
        groupSingle.add(btnRefresh);

        controlPanel.add(groupAll);
        controlPanel.add(groupSingle);

        add(controlPanel, BorderLayout.SOUTH);
        add(statusLabel, BorderLayout.NORTH);

        // Eventos de botones
        btnStartAll.addActionListener(e -> startAllSequence());
        btnStopAll.addActionListener(e -> stopAll());
        btnStartOne.addActionListener(e -> actionOnSelected(true));
        btnStopOne.addActionListener(e -> actionOnSelected(false));
        btnRefresh.addActionListener(e -> updateTableUI());

        // Timer para actualizar estado visual cada 2 segundos
        new Timer(2000, e -> refreshTableStatusOnly()).start();
    }

    // --- 1. DEFINICIÓN DE SERVICIOS ---
    private void initServices() {
        // Infraestructura
        addService("Eureka Server", "eurekaserver/target/EurekaServer-0.0.1-SNAPSHOT.jar");

        // APIs Base
        addService("Api Medico", "apimedico/target/ApiMedico-0.0.1-SNAPSHOT.jar");
        addService("Api Paciente", "apipaciente/target/ApiPaciente-0.0.1-SNAPSHOT.jar");
        addService("Api Empleado", "apiempleado/target/ApiEmpleado-0.0.1-SNAPSHOT.jar");
        addService("Api Especialidad", "apiespecialidad/target/ApiEspecialidad-0.0.1-SNAPSHOT.jar");
        addService("Api Consultorio", "apiconsultorio/target/ApiConsultorio-0.0.1-SNAPSHOT.jar");

        // APIs Negocio
        addService("Api Disponibilidad", "apidisponibilidad/target/ApiDisponibilidad-0.0.1-SNAPSHOT.jar");
        addService("Api Horario", "apihorario/target/ApiHorario-0.0.1-SNAPSHOT.jar");
        addService("Api SlotHorario", "apislothorario/target/ApiSlotHorario-0.0.1-SNAPSHOT.jar");
        addService("Api Cita", "apicita/target/ApiCita-0.0.1-SNAPSHOT.jar");
        addService("Api Historia", "apihistoria/target/ApiHistoria-0.0.1-SNAPSHOT.jar");
        addService("Api Boleta", "apiboleta/target/ApiBoleta-0.0.1-SNAPSHOT.jar");

        // --- NUEVOS SERVICIOS DE ATENCIÓN ---
        addService("Api Atencion", "apiatencion/target/ApiAtencion-0.0.1-SNAPSHOT.jar");
        addService("Api Nueva Atencion", "apinuevaatencion/target/ApiNuevaAtencion-0.0.1-SNAPSHOT.jar");

        // APIs Orquestadoras
        addService("Api Gestion Cita", "apigestioncita/target/ApiGestionCita-0.0.1-SNAPSHOT.jar");
        addService("Api Gestion Boleta", "apigestionboleta/target/ApiGestionBoleta-0.0.1-SNAPSHOT.jar");
        addService("Api Gestion Historia", "apigestionhistoria/target/ApiGestionHistoria-0.0.1-SNAPSHOT.jar");
        addService("Api Gestion Horario", "apigestionhorario/target/ApiGestionHorario-0.0.1-SNAPSHOT.jar");

        // --- ORQUESTADOR ATENCIÓN ---
        addService("Api Gestion Atencion", "apigestionatencion/target/ApiGestionAtencion-0.0.1-SNAPSHOT.jar");

        // Frontend
        addService("Clinica Web", "clinicaweb/target/ClinicaWeb-0.0.1-SNAPSHOT.jar");
    }

    private void addService(String name, String path) {
        services.add(new Microservice(name, path));
    }

    // --- ACCIONES ---
    private void actionOnSelected(boolean start) {
        int row = table.getSelectedRow();
        if (row == -1) {
            JOptionPane.showMessageDialog(this, "Selecciona un servicio en la tabla.");
            return;
        }
        Microservice s = services.get(row);
        if (start) {
            if (s.isRunning()) {
                statusLabel.setText("Aviso: " + s.name + " ya está corriendo.");
            } else {
                new Thread(() -> {
                    s.manualStop = false; // Resetear bandera manual
                    startService(s);
                    statusLabel.setText("Intentando iniciar: " + s.name);
                    updateTableUI();
                }).start();
            }
        } else {
            if (!s.isRunning()) {
                statusLabel.setText("Aviso: " + s.name + " ya está detenido.");
            } else {
                s.manualStop = true; // Marcar como detenido manualmente para que no salga error
                stopService(s);
                statusLabel.setText("Detenido: " + s.name);
                updateTableUI();
            }
        }
    }

    private void showLogDialog() {
        int row = table.getSelectedRow();
        if (row == -1) {
            return;
        }
        Microservice s = services.get(row);
        JTextArea ta = new JTextArea(s.getFullLog());
        ta.setEditable(false);
        JScrollPane sp = new JScrollPane(ta);
        sp.setPreferredSize(new Dimension(600, 400));
        JOptionPane.showMessageDialog(this, sp, "Log de " + s.name, JOptionPane.INFORMATION_MESSAGE);
    }

    private void startAllSequence() {
        new Thread(() -> {
            statusLabel.setText("Iniciando secuencia completa...");

            // 1. Eureka primero
            Microservice eureka = services.get(0);
            if (!eureka.isRunning()) {
                startService(eureka);
                statusLabel.setText("Esperando a Eureka (15s)...");
                try {
                    Thread.sleep(15000);
                } catch (Exception e) {
                }
            }

            // 2. El resto en orden
            for (int i = 1; i < services.size(); i++) {
                Microservice s = services.get(i);
                if (!s.isRunning()) {
                    statusLabel.setText("Iniciando " + s.name + "...");
                    startService(s);
                    try {
                        Thread.sleep(1000);
                    } catch (Exception e) {
                    }
                }
            }
            statusLabel.setText("Secuencia finalizada.");
            updateTableUI();
        }).start();
    }

    private void stopAll() {
        new Thread(() -> {
            statusLabel.setText("Deteniendo servicios...");
            for (Microservice s : services) {
                s.manualStop = true;
                stopService(s);
            }
            updateTableUI();
            statusLabel.setText("Todos los servicios detenidos.");
        }).start();
    }

    private void startService(Microservice s) {
        try {
            s.errorDetected = false; // Resetear error
            s.clearLog();

            File jarFile = new File(ROOT_PATH, s.jarPath);
            if (!jarFile.exists()) {
                s.appendLog("ERROR: Archivo JAR no encontrado en: " + jarFile.getAbsolutePath());
                s.errorDetected = true;
                SwingUtilities.invokeLater(this::updateTableUI);
                return;
            }

            ProcessBuilder pb = new ProcessBuilder("java", "-jar", jarFile.getAbsolutePath());
            pb.directory(new File(ROOT_PATH));
            pb.redirectErrorStream(true); // Combinar stdout y stderr
            s.process = pb.start();

            // Consumir stream en hilo aparte para leer errores
            new Thread(() -> {
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(s.process.getInputStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        s.appendLog(line);
                        // Detección simple de error en el log (opcional)
                        if (line.contains("APPLICATION FAILED TO START")) {
                            s.errorDetected = true;
                        }
                    }
                } catch (Exception e) {
                    s.appendLog("Error leyendo stream: " + e.getMessage());
                }
            }).start();

        } catch (Exception e) {
            s.errorDetected = true;
            s.appendLog("Excepción al iniciar: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void stopService(Microservice s) {
        if (s.isRunning()) {
            s.process.destroy();
            try {
                if (!s.process.waitFor(2, TimeUnit.SECONDS)) {
                    s.process.destroyForcibly();
                }
            } catch (Exception e) {
            }
        }
    }

    // --- ACTUALIZACIÓN UI ---
    private void updateTableUI() {
        SwingUtilities.invokeLater(() -> {
            int selectedRow = table.getSelectedRow();
            model.setRowCount(0);
            for (Microservice s : services) {
                model.addRow(new Object[]{
                    s.name,
                    getStatusString(s),
                    s.isRunning() ? String.valueOf(s.process.pid()) : "-",
                    s.jarPath
                });
            }
            if (selectedRow >= 0 && selectedRow < table.getRowCount()) {
                table.setRowSelectionInterval(selectedRow, selectedRow);
            }
        });
    }

    private void refreshTableStatusOnly() {
        SwingUtilities.invokeLater(() -> {
            if (table.isEditing()) {
                return;
            }

            for (int i = 0; i < services.size(); i++) {
                Microservice s = services.get(i);

                // Lógica de detección de cierre inesperado
                if (s.process != null && !s.process.isAlive()) {
                    int exitVal = s.process.exitValue();
                    // 143 = SIGTERM (stop normal), 0 = Normal. Otro valor = Crash
                    if (exitVal != 0 && exitVal != 143 && !s.manualStop) {
                        s.errorDetected = true;
                    }
                }

                String currentStatusInTable = (String) model.getValueAt(i, 1);
                String realStatus = getStatusString(s);

                // Solo actualizar si cambió el texto para no parpadear
                if (!currentStatusInTable.equals(realStatus)) {
                    model.setValueAt(realStatus, i, 1);
                    model.setValueAt(s.isRunning() ? String.valueOf(s.process.pid()) : "-", i, 2);
                }
            }
        });
    }

    private String getStatusString(Microservice s) {
        if (s.errorDetected) {
            return "ERROR (Ver Log)";
        }
        if (s.isRunning()) {
            return "CORRIENDO";
        }
        if (s.process != null && s.process.isAlive()) {
            return "INICIANDO..."; // Raro pero posible
        }
        return "DETENIDO";
    }

    public static void main(String[] args) {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception e) {
        }
        SwingUtilities.invokeLater(() -> new ClinicaDashboard().setVisible(true));
    }

    // Clase auxiliar interna
    static class Microservice {

        String name;
        String jarPath;
        Process process;
        boolean errorDetected = false;
        boolean manualStop = false; // Para distinguir stop usuario vs crash

        // Guardamos logs para mostrar error
        private StringBuilder logBuffer = new StringBuilder();

        public Microservice(String name, String jarPath) {
            this.name = name;
            this.jarPath = jarPath;
        }

        public boolean isRunning() {
            return process != null && process.isAlive();
        }

        public void clearLog() {
            logBuffer.setLength(0);
        }

        public void appendLog(String line) {
            // Guardar solo las ultimas ~50 lineas para no saturar memoria
            if (logBuffer.length() > 10000) {
                logBuffer.delete(0, 2000);
            }
            logBuffer.append(line).append("\n");
        }

        public String getLastLogLines() {
            String content = logBuffer.toString();
            String[] lines = content.split("\n");
            // Devolver ultimas 3 lineas para el tooltip
            int count = 0;
            StringBuilder sb = new StringBuilder();
            for (int i = lines.length - 1; i >= 0 && count < 4; i--) {
                if (!lines[i].trim().isEmpty()) {
                    sb.insert(0, lines[i] + "<br>");
                    count++;
                }
            }
            return sb.toString();
        }

        public String getFullLog() {
            return logBuffer.toString();
        }
    }
}
