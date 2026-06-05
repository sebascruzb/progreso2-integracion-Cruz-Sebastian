package edu.udla.integracion.progreso2.service;

import edu.udla.integracion.progreso2.model.CitaRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Servicio de validación de solicitudes de cita médica.
 * Valida campos obligatorios y registra errores en archivo de log.
 */
@Service
public class CitaValidationService {

    private static final Logger logger = LoggerFactory.getLogger(CitaValidationService.class);
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Value("${app.errors.path}")
    private String errorsPath;

    @Value("${app.errors.filename}")
    private String errorsFilename;

    /**
     * Valida una solicitud de cita.
     * @param cita La cita a validar
     * @return Lista de errores encontrados (vacía si es válida)
     */
    public List<String> validar(CitaRequest cita) {
        List<String> errores = new ArrayList<>();

        if (cita == null) {
            errores.add("El payload de la solicitud está vacío o es nulo");
            return errores;
        }

        if (cita.getIdCita() == null || cita.getIdCita().isBlank()) {
            errores.add("El campo 'idCita' es obligatorio");
        }

        if (cita.getPaciente() == null || cita.getPaciente().isBlank()) {
            errores.add("El campo 'paciente' es obligatorio");
        }

        if (cita.getCorreo() == null || cita.getCorreo().isBlank()) {
            errores.add("El campo 'correo' es obligatorio");
        }

        if (cita.getEspecialidad() == null || cita.getEspecialidad().isBlank()) {
            errores.add("El campo 'especialidad' es obligatorio");
        }

        if (cita.getFechaCita() == null || cita.getFechaCita().isBlank()) {
            errores.add("El campo 'fechaCita' es obligatorio");
        }

        if (cita.getSede() == null || cita.getSede().isBlank()) {
            errores.add("El campo 'sede' es obligatorio");
        }

        if (cita.getValor() == null || cita.getValor().compareTo(BigDecimal.ZERO) <= 0) {
            errores.add("El campo 'valor' debe ser mayor a 0");
        }

        return errores;
    }

    /**
     * Indica si la cita es válida.
     */
    public boolean esValida(CitaRequest cita) {
        return validar(cita).isEmpty();
    }

    /**
     * Registra un error de validación en el archivo de log.
     */
    public void registrarError(CitaRequest cita, List<String> errores) {
        try {
            Files.createDirectories(Paths.get(errorsPath));
            String rutaArchivo = errorsPath + "/" + errorsFilename;

            try (PrintWriter pw = new PrintWriter(new FileWriter(rutaArchivo, true))) {
                String timestamp = LocalDateTime.now().format(FORMATTER);
                String idCita = (cita != null && cita.getIdCita() != null) ? cita.getIdCita() : "N/A";
                String payload = (cita != null) ? cita.toString() : "payload nulo";

                pw.println("=== ERROR DE VALIDACIÓN ===");
                pw.println("Fecha/Hora : " + timestamp);
                pw.println("idCita     : " + idCita);
                pw.println("Motivos    : " + String.join(" | ", errores));
                pw.println("Payload    : " + payload);
                pw.println();

                logger.warn("Cita rechazada [{}]: {}", idCita, String.join(", ", errores));
            }
        } catch (IOException e) {
            logger.error("Error al escribir en log de errores: {}", e.getMessage());
        }
    }

    /**
     * Registra un error de procesamiento (no de validación).
     */
    public void registrarErrorProcesamiento(String idCita, String motivo, String payload) {
        try {
            Files.createDirectories(Paths.get(errorsPath));
            String rutaArchivo = errorsPath + "/" + errorsFilename;

            try (PrintWriter pw = new PrintWriter(new FileWriter(rutaArchivo, true))) {
                String timestamp = LocalDateTime.now().format(FORMATTER);

                pw.println("=== ERROR DE PROCESAMIENTO ===");
                pw.println("Fecha/Hora : " + timestamp);
                pw.println("idCita     : " + (idCita != null ? idCita : "N/A"));
                pw.println("Motivo     : " + motivo);
                pw.println("Payload    : " + payload);
                pw.println();

                logger.error("Error de procesamiento [{}]: {}", idCita, motivo);
            }
        } catch (IOException e) {
            logger.error("Error al escribir en log de errores: {}", e.getMessage());
        }
    }
}
