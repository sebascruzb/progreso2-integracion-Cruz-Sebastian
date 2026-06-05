package edu.udla.integracion.progreso2.controller;

import edu.udla.integracion.progreso2.model.CitaRequest;
import edu.udla.integracion.progreso2.service.CitaValidationService;
import org.apache.camel.ProducerTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Controlador REST para el registro de citas médicas de Salud360.
 *
 * RF1: Expone el endpoint POST /api/citas para registrar solicitudes de cita.
 * Valida los datos recibidos y, si son válidos, inicia el flujo de integración
 * mediante Apache Camel.
 */
@RestController
@RequestMapping("/api")
public class CitaController {

    private static final Logger logger = LoggerFactory.getLogger(CitaController.class);
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Autowired
    private CitaValidationService validationService;

    @Autowired
    private ProducerTemplate producerTemplate;

    /**
     * RF1 - Registrar una solicitud de cita médica.
     *
     * Si los datos son válidos, se inicia el flujo de integración:
     *   → billing.queue (Point-to-Point) → RF2
     *   → appointments.events (Pub/Sub) → RF3
     *   → auditoria-citas.csv (File Transfer) → RF4
     *
     * Si los datos son inválidos, se rechaza con HTTP 400 y se registra el error.
     */
    @PostMapping("/citas")
    public ResponseEntity<Map<String, Object>> registrarCita(@RequestBody CitaRequest cita) {

        logger.info("=== Solicitud recibida: POST /api/citas - idCita: {} ===",
                    cita != null ? cita.getIdCita() : "null");

        Map<String, Object> respuesta = new HashMap<>();
        String timestamp = LocalDateTime.now().format(FORMATTER);

        // --- Validación de datos ---
        List<String> errores = validationService.validar(cita);

        if (!errores.isEmpty()) {
            // Datos inválidos: registrar error y responder 400
            validationService.registrarError(cita, errores);

            respuesta.put("estado", "RECHAZADA");
            respuesta.put("timestamp", timestamp);
            respuesta.put("idCita", cita != null ? cita.getIdCita() : null);
            respuesta.put("errores", errores);
            respuesta.put("mensaje", "La solicitud de cita fue rechazada por datos inválidos.");

            logger.warn("Cita rechazada. Errores: {}", errores);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(respuesta);
        }

        // --- Procesamiento válido: iniciar flujo Camel ---
        try {
            producerTemplate.sendBody("direct:procesar-cita", cita);

            respuesta.put("estado", "CONFIRMADA");
            respuesta.put("timestamp", timestamp);
            respuesta.put("idCita", cita.getIdCita());
            respuesta.put("paciente", cita.getPaciente());
            respuesta.put("mensaje", "Cita registrada y distribuida exitosamente a todos los sistemas.");
            respuesta.put("sistemas", new String[]{
                "billing.queue (facturación - Point-to-Point)",
                "appointments.events → notifications.queue (notificaciones - Pub/Sub)",
                "appointments.events → analytics.queue (analítica - Pub/Sub)",
                "data/outbox/auditoria-citas.csv (sistema legado - File Transfer)"
            });

            logger.info("Cita {} procesada y distribuida exitosamente.", cita.getIdCita());
            return ResponseEntity.status(HttpStatus.CREATED).body(respuesta);

        } catch (Exception e) {
            // Error inesperado en procesamiento
            validationService.registrarErrorProcesamiento(
                cita.getIdCita(),
                "Error inesperado en procesamiento: " + e.getMessage(),
                cita.toString()
            );

            respuesta.put("estado", "ERROR");
            respuesta.put("timestamp", timestamp);
            respuesta.put("idCita", cita.getIdCita());
            respuesta.put("mensaje", "Error interno al procesar la cita: " + e.getMessage());

            logger.error("Error procesando cita {}: {}", cita.getIdCita(), e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(respuesta);
        }
    }

    /**
     * Health check endpoint para verificar que la API está activa.
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> health() {
        Map<String, String> resp = new HashMap<>();
        resp.put("estado", "UP");
        resp.put("servicio", "Salud360 - Integración de Citas");
        resp.put("timestamp", LocalDateTime.now().format(FORMATTER));
        return ResponseEntity.ok(resp);
    }
}
