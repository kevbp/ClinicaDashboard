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
    private final JLabel statusLabel = new JLabel("Listo - Selecciona un servicio para controlarlo individualmente.");

    // AJUSTA ESTA RUTA SEGÚN TU CARPETA (Ej: "." si el dashboard está junto a las carpetas, o ".." si está dentro)
    private static final String ROOT_PATH = "..";

    public ClinicaDashboard() {
        setTitle("Panel de Control - Clínica Integral (Todos los Módulos)");
        setSize(1200, 850);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(new BorderLayout());

        // 1. CARGAR TODOS LOS SERVICIOS
        initServices();

        // 2. CONFIGURACIÓN DE LA TABLA
        String[] columns = {"ID", "Servicio", "Estado", "PID", "Ruta JAR (Editable)"};

        model = new DefaultTableModel(columns, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return column == 4; // Solo la ruta es editable
            }
        };

        table = new JTable(model);
        table.setRowHeight(28);
        table.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION); // Selección única para control individual

        // Listener para editar rutas manualmente si es necesario
        model.addTableModelListener(e -> {
            if (e.getType() == javax.swing.event.TableModelEvent.UPDATE) {
                int row = e.getFirstRow();
                int col = e.getColumn();
                if (col == 4) {
                    String newPath = (String) model.getValueAt(row, col);
                    services.get(row).jarPath = newPath;
                }
            }
        });

        // Anchos de columna
        table.getColumnModel().getColumn(0).setPreferredWidth(40);  // ID
        table.getColumnModel().getColumn(1).setPreferredWidth(250); // Nombre
        table.getColumnModel().getColumn(2).setPreferredWidth(100); // Estado
        table.getColumnModel().getColumn(3).setPreferredWidth(60);  // PID
        table.getColumnModel().getColumn(4).setPreferredWidth(400); // Ruta

        // Renderizado de colores según estado
        table.setDefaultRenderer(Object.class, new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
                Component c = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
                Microservice s = services.get(row);
                String status = (String) model.getValueAt(row, 2);

                if ("CORRIENDO".equals(status)) {
                    c.setForeground(new Color(0, 128, 0)); // Verde
                    c.setFont(c.getFont().deriveFont(Font.BOLD));
                } else if (status.startsWith("ERROR")) {
                    c.setForeground(Color.RED);
                    c.setFont(c.getFont().deriveFont(Font.BOLD));
                } else if ("INICIANDO...".equals(status)) {
                    c.setForeground(Color.ORANGE);
                } else {
                    c.setForeground(Color.GRAY);
                    c.setFont(c.getFont().deriveFont(Font.PLAIN));
                }

                if (isSelected) {
                    c.setBackground(new Color(230, 240, 255));
                } else {
                    c.setBackground(Color.WHITE);
                }

                return c;
            }
        });

        // MENÚ CONTEXTUAL (CLIC DERECHO) - CONTROL INDIVIDUAL
        JPopupMenu popup = new JPopupMenu();
        JMenuItem itemStart = new JMenuItem("▶ Iniciar Servicio");
        JMenuItem itemStop = new JMenuItem("⏹ Detener Servicio");
        JMenuItem itemLog = new JMenuItem("📄 Ver Log");

        itemStart.addActionListener(e -> actionOnSelected(true));
        itemStop.addActionListener(e -> actionOnSelected(false));
        itemLog.addActionListener(e -> showLogDialog());

        popup.add(itemStart);
        popup.add(itemStop);
        popup.addSeparator();
        popup.add(itemLog);
        table.setComponentPopupMenu(popup);

        // Scroll
        add(new JScrollPane(table), BorderLayout.CENTER);

        // 3. PANEL DE BOTONES (INFERIOR)
        JPanel botPanel = new JPanel();
        botPanel.setLayout(new BoxLayout(botPanel, BoxLayout.Y_AXIS));

        // Fila 1: Control Individual
        JPanel pnlIndividual = new JPanel(new FlowLayout(FlowLayout.CENTER));
        pnlIndividual.setBorder(BorderFactory.createTitledBorder("Control Individual (Seleccione una fila)"));
        JButton btnStartOne = new JButton("▶ Iniciar Seleccionado");
        JButton btnStopOne = new JButton("⏹ Detener Seleccionado");
        JButton btnLog = new JButton("📄 Ver Log");

        btnStartOne.addActionListener(e -> actionOnSelected(true));
        btnStopOne.addActionListener(e -> actionOnSelected(false));
        btnLog.addActionListener(e -> showLogDialog());

        pnlIndividual.add(btnStartOne);
        pnlIndividual.add(btnStopOne);
        pnlIndividual.add(btnLog);

        // Fila 2: Control Global
        JPanel pnlGlobal = new JPanel(new FlowLayout(FlowLayout.CENTER));
        pnlGlobal.setBorder(BorderFactory.createTitledBorder("Control Masivo"));
        JButton btnStartAll = new JButton("🚀 INICIAR TODO (Secuencia)");
        JButton btnStopAll = new JButton("💀 DETENER TODO");

        btnStartAll.setBackground(new Color(200, 255, 200));
        btnStopAll.setBackground(new Color(255, 200, 200));

        btnStartAll.addActionListener(e -> startAllSequence());
        btnStopAll.addActionListener(e -> stopAll());

        pnlGlobal.add(btnStartAll);
        pnlGlobal.add(btnStopAll);

        botPanel.add(pnlIndividual);
        botPanel.add(pnlGlobal);

        add(botPanel, BorderLayout.SOUTH);
        add(statusLabel, BorderLayout.NORTH);

        // Carga inicial de datos en tabla
        refreshTableData();

        // Timer para refrescar estado (PID, status) cada 2s
        new Timer(2000, e -> refreshTableStatus()).start();
    }

    // --- REGISTRO DE TODOS LOS MICROSERVICIOS ---
    private void initServices() {
        int id = 1;
        // Infraestructura
        addService(id++, "Eureka Server", "eurekaserver/target/EurekaServer-0.0.1-SNAPSHOT.jar");

        // APIs Base
        addService(id++, "Api Medico", "apimedico/target/ApiMedico-0.0.1-SNAPSHOT.jar");
        addService(id++, "Api Paciente", "apipaciente/target/ApiPaciente-0.0.1-SNAPSHOT.jar");
        addService(id++, "Api Empleado", "apiempleado/target/ApiEmpleado-0.0.1-SNAPSHOT.jar");
        addService(id++, "Api Especialidad", "apiespecialidad/target/ApiEspecialidad-0.0.1-SNAPSHOT.jar");
        addService(id++, "Api Consultorio", "apiconsultorio/target/ApiConsultorio-0.0.1-SNAPSHOT.jar");
        addService(id++, "Api Medicamento", "apimedicamento/target/ApiMedicamento-0.0.1-SNAPSHOT.jar");
        addService(id++, "Api Tipo Analisis", "apitipoanalisis/target/ApiTipoAnalisis-0.0.1-SNAPSHOT.jar");

        // APIs Negocio
        addService(id++, "Api Disponibilidad", "apidisponibilidad/target/ApiDisponibilidad-0.0.1-SNAPSHOT.jar");
        addService(id++, "Api Horario", "apihorario/target/ApiHorario-0.0.1-SNAPSHOT.jar");
        addService(id++, "Api SlotHorario", "apislothorario/target/ApiSlotHorario-0.0.1-SNAPSHOT.jar");
        addService(id++, "Api Cita", "apicita/target/ApiCita-0.0.1-SNAPSHOT.jar");
        addService(id++, "Api Historia", "apihistoria/target/ApiHistoria-0.0.1-SNAPSHOT.jar");
        addService(id++, "Api Boleta", "apiboleta/target/ApiBoleta-0.0.1-SNAPSHOT.jar");
        addService(id++, "Api Atencion", "apiatencion/target/ApiAtencion-0.0.1-SNAPSHOT.jar");
        addService(id++, "Api Receta (BD)", "apireceta/target/ApiReceta-0.0.1-SNAPSHOT.jar");
        addService(id++, "Api Detalle Receta", "apidetallereceta/target/ApiDetalleReceta-0.0.1-SNAPSHOT.jar");
        addService(id++, "Api Ficha Analisis (BD)", "apifichaanalisis/target/ApiFichaAnalisis-0.0.1-SNAPSHOT.jar");
        addService(id++, "Api Detalle Ficha", "apidetallefichaanalisis/target/ApiDetalleFichaAnalisis-0.0.1-SNAPSHOT.jar");

        // APIs de Estado (Memoria/Sesion)
        addService(id++, "Api Nueva Atencion", "apinuevaatencion/target/ApiNuevaAtencion-0.0.1-SNAPSHOT.jar");
        addService(id++, "Api Nueva Receta", "apinuevareceta/target/ApiNuevaReceta-0.0.1-SNAPSHOT.jar");
        addService(id++, "Api Cesta Receta", "apicestareceta/target/ApiCestaReceta-0.0.1-SNAPSHOT.jar");
        addService(id++, "Api Nueva Ficha", "apinuevafichaanalisis/target/ApiNuevaFichaAnalisis-0.0.1-SNAPSHOT.jar");
        addService(id++, "Api Cesta Ficha", "apicestafichaanalisis/target/ApiCestaFichaAnalisis-0.0.1-SNAPSHOT.jar");

        // Orquestadores
        addService(id++, "Gestión Horario", "apigestionhorario/target/ApiGestionHorario-0.0.1-SNAPSHOT.jar");
        addService(id++, "Gestión Cita", "apigestioncita/target/ApiGestionCita-0.0.1-SNAPSHOT.jar");
        addService(id++, "Gestión Boleta", "apigestionboleta/target/ApiGestionBoleta-0.0.1-SNAPSHOT.jar");
        addService(id++, "Gestión Historia", "apigestionhistoria/target/ApiGestionHistoria-0.0.1-SNAPSHOT.jar");
        addService(id++, "Gestión Atencion", "apigestionatencion/target/ApiGestionAtencion-0.0.1-SNAPSHOT.jar");
        addService(id++, "Gestión Receta", "apigestionreceta/target/ApiGestionReceta-0.0.1-SNAPSHOT.jar");
        addService(id++, "Gestión Ficha Analisis", "apigestionfichaanalisis/target/ApiGestionFichaAnalisis-0.0.1-SNAPSHOT.jar");

        // Frontend
        addService(id++, ">>> CLINICA WEB <<<", "clinicaweb/target/ClinicaWeb-0.0.1-SNAPSHOT.jar");
    }

    private void addService(int id, String name, String path) {
        services.add(new Microservice(id, name, path));
    }

    // --- LÓGICA DE CONTROL INDIVIDUAL ---
    private void actionOnSelected(boolean start) {
        int row = table.getSelectedRow();
        if (row == -1) {
            JOptionPane.showMessageDialog(this, "Por favor, selecciona un servicio de la lista.");
            return;
        }
        Microservice s = services.get(row);
        if (start) {
            if (!s.isRunning()) {
                new Thread(() -> {
                    s.manualStop = false;
                    startService(s);
                    statusLabel.setText("Iniciando: " + s.name);
                    refreshTableData();
                }).start();
            } else {
                statusLabel.setText("El servicio " + s.name + " ya está corriendo.");
            }
        } else {
            if (s.isRunning()) {
                s.manualStop = true;
                stopService(s);
                statusLabel.setText("Detenido: " + s.name);
                refreshTableData();
            }
        }
    }

    // --- LÓGICA DE CONTROL MASIVO ---
    private void startAllSequence() {
        new Thread(() -> {
            // 1. Eureka
            Microservice eureka = services.get(0);
            if (!eureka.isRunning()) {
                statusLabel.setText("Iniciando Eureka (Espere 10s)...");
                startService(eureka);
                try {
                    Thread.sleep(10000);
                } catch (Exception e) {
                }
            }
            // 2. Resto
            for (int i = 1; i < services.size(); i++) {
                Microservice s = services.get(i);
                if (!s.isRunning()) {
                    statusLabel.setText("Iniciando " + s.name + "...");
                    startService(s);
                    try {
                        Thread.sleep(1500);
                    } catch (Exception e) {
                    }
                }
            }
            statusLabel.setText("Todos los servicios iniciados.");
            refreshTableData();
        }).start();
    }

    private void stopAll() {
        new Thread(() -> {
            statusLabel.setText("Deteniendo todo...");
            for (Microservice s : services) {
                s.manualStop = true;
                stopService(s);
            }
            refreshTableData();
            statusLabel.setText("Sistema detenido.");
        }).start();
    }

    // --- MÉTODOS DE PROCESO ---
    private void startService(Microservice s) {
        try {
            s.errorDetected = false;
            s.clearLog();
            File jar = new File(ROOT_PATH, s.jarPath);
            if (!jar.exists()) {
                s.appendLog("ERROR: No se encuentra el JAR en " + jar.getAbsolutePath());
                s.errorDetected = true;
                return;
            }
            ProcessBuilder pb = new ProcessBuilder("java", "-jar", jar.getAbsolutePath());
            pb.directory(new File(ROOT_PATH));
            pb.redirectErrorStream(true);
            s.process = pb.start();

            new Thread(() -> {
                try (BufferedReader r = new BufferedReader(new InputStreamReader(s.process.getInputStream()))) {
                    String line;
                    while ((line = r.readLine()) != null) {
                        s.appendLog(line);
                        if (line.contains("APPLICATION FAILED TO START")) {
                            s.errorDetected = true;
                        }
                    }
                } catch (Exception e) {
                }
            }).start();

        } catch (Exception e) {
            s.errorDetected = true;
            s.appendLog(e.getMessage());
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

    // --- UTILS ---
    private void refreshTableData() {
        SwingUtilities.invokeLater(() -> {
            int sel = table.getSelectedRow();
            model.setRowCount(0);
            for (Microservice s : services) {
                model.addRow(new Object[]{s.id, s.name, getStatus(s), s.isRunning() ? s.process.pid() : "-", s.jarPath});
            }
            if (sel != -1 && sel < model.getRowCount()) {
                table.setRowSelectionInterval(sel, sel);
            }
        });
    }

    private void refreshTableStatus() {
        SwingUtilities.invokeLater(() -> {
            if (table.isEditing()) {
                return;
            }
            for (int i = 0; i < services.size(); i++) {
                Microservice s = services.get(i);
                // Detectar crash
                if (s.process != null && !s.process.isAlive() && !s.manualStop && s.process.exitValue() != 0 && s.process.exitValue() != 143) {
                    s.errorDetected = true;
                }
                String st = getStatus(s);
                if (!st.equals(model.getValueAt(i, 2))) {
                    model.setValueAt(st, i, 2);
                    model.setValueAt(s.isRunning() ? s.process.pid() : "-", i, 3);
                }
            }
        });
    }

    private String getStatus(Microservice s) {
        if (s.errorDetected) {
            return "ERROR (Ver Log)";
        }
        if (s.isRunning()) {
            return "CORRIENDO";
        }
        if (s.process != null && s.process.isAlive()) {
            return "INICIANDO...";
        }
        return "DETENIDO";
    }

    private void showLogDialog() {
        int row = table.getSelectedRow();
        if (row == -1) {
            return;
        }
        Microservice s = services.get(row);
        JTextArea ta = new JTextArea(s.getFullLog());
        ta.setEditable(false);
        JOptionPane.showMessageDialog(this, new JScrollPane(ta), "Log: " + s.name, JOptionPane.INFORMATION_MESSAGE);
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new ClinicaDashboard().setVisible(true));
    }

    static class Microservice {

        int id;
        String name, jarPath;
        Process process;
        boolean errorDetected = false, manualStop = false;
        StringBuilder log = new StringBuilder();

        public Microservice(int id, String name, String jarPath) {
            this.id = id;
            this.name = name;
            this.jarPath = jarPath;
        }

        public boolean isRunning() {
            return process != null && process.isAlive();
        }

        public void clearLog() {
            log.setLength(0);
        }

        public void appendLog(String l) {
            if (log.length() > 20000) {
                log.delete(0, 5000);
            }
            log.append(l).append("\n");
        }

        public String getLastLogLines() {
            return log.length() > 200 ? log.substring(log.length() - 200) : log.toString();
        }

        public String getFullLog() {
            return log.toString();
        }
    }
}
