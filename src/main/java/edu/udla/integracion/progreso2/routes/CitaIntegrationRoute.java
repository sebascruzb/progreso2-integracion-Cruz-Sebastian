package edu.udla.integracion.progreso2.routes;

import com.fasterxml.jackson.databind.ObjectMapper;
import edu.udla.integracion.progreso2.model.CitaRequest;
import edu.udla.integracion.progreso2.service.CitaValidationService;
import org.apache.camel.Exchange;
import org.apache.camel.LoggingLevel;
import org.apache.camel.builder.RouteBuilder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.FileWriter;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

/**
 * Rutas de integración Apache Camel para el sistema Salud360.
 *
 * Patrones implementados:
 *  - Point-to-Point Channel : billing.queue        (un solo consumidor)
 *  - Publish/Subscribe      : appointments.events  (múltiples consumidores via fanout)
 *  - File Transfer          : auditoria-citas.csv  (sistema legado)
 *
 * Usa el componente camel-spring-rabbitmq (disponible en Camel 4.x)
 */
@Component
public class CitaIntegrationRoute extends RouteBuilder {

    @Autowired
    private CitaValidationService validationService;

    @Autowired
    private ObjectMapper objectMapper;

    @Value("${app.outbox.path}")
    private String outboxPath;

    @Value("${app.csv.filename}")
    private String csvFilename;

    // ── URIs del componente spring-rabbitmq (Camel 4.x) ─────────────────
    // Point-to-Point: envío directo a la cola de facturación
    private static final String BILLING_QUEUE =
        "spring-rabbitmq:billing.queue?routingKey=billing.queue";

    // Publish/Subscribe: publicar al exchange fanout (distribuye a todas las colas suscriptas)
    private static final String APPOINTMENTS_EXCHANGE =
        "spring-rabbitmq:appointments.events";

    @Override
    public void configure() throws Exception {

        // ── Manejo global de excepciones ─────────────────────────────────
        onException(Exception.class)
            .handled(true)
            .log(LoggingLevel.ERROR, "Error en ruta Camel: ${exception.message}")
            .process(exchange -> {
                Exception ex = exchange.getProperty(Exchange.EXCEPTION_CAUGHT, Exception.class);
                CitaRequest cita = exchange.getIn().getBody(CitaRequest.class);
                String idCita  = (cita != null) ? cita.getIdCita() : "DESCONOCIDO";
                String payload = (cita != null) ? cita.toString() : String.valueOf(exchange.getIn().getBody());
                validationService.registrarErrorProcesamiento(
                    idCita,
                    ex != null ? ex.getMessage() : "Error desconocido",
                    payload
                );
            });

        // ── RUTA PRINCIPAL ────────────────────────────────────────────────
        from("direct:procesar-cita")
            .routeId("ruta-principal-cita")
            .log(LoggingLevel.INFO, "=== Procesando cita: ${body.idCita} ===")
            .to("direct:facturacion-p2p")
            .to("direct:evento-pubsub")
            .to("direct:generar-csv")
            .log(LoggingLevel.INFO, "=== Cita ${body.idCita} distribuida a todos los sistemas ===");

        // ── RF2: POINT-TO-POINT → billing.queue ──────────────────────────
        from("direct:facturacion-p2p")
            .routeId("ruta-facturacion-p2p")
            .log(LoggingLevel.INFO, "[P2P] Enviando COMANDO_FACTURAR_CITA: ${body.idCita}")
            .process(exchange -> {
                CitaRequest cita = exchange.getIn().getBody(CitaRequest.class);
                Map<String, Object> msg = new HashMap<>();
                msg.put("idCita",       cita.getIdCita());
                msg.put("paciente",     cita.getPaciente());
                msg.put("especialidad", cita.getEspecialidad());
                msg.put("valor",        cita.getValor());
                msg.put("tipoMensaje",  "COMANDO_FACTURAR_CITA");
                exchange.getIn().setBody(objectMapper.writeValueAsString(msg));
                exchange.getIn().setHeader("Content-Type", "application/json");
            })
            .to(BILLING_QUEUE)
            .log(LoggingLevel.INFO, "[P2P] Mensaje publicado en billing.queue");

        // ── RF3: PUBLISH/SUBSCRIBE → appointments.events (fanout) ────────
        from("direct:evento-pubsub")
            .routeId("ruta-evento-pubsub")
            .log(LoggingLevel.INFO, "[PUB/SUB] Publicando CITA_CONFIRMADA: ${body.idCita}")
            .process(exchange -> {
                CitaRequest cita = exchange.getIn().getBody(CitaRequest.class);
                Map<String, Object> evento = new HashMap<>();
                evento.put("idCita",       cita.getIdCita());
                evento.put("paciente",     cita.getPaciente());
                evento.put("correo",       cita.getCorreo());
                evento.put("especialidad", cita.getEspecialidad());
                evento.put("fechaCita",    cita.getFechaCita());
                evento.put("sede",         cita.getSede());
                evento.put("tipoEvento",   "CITA_CONFIRMADA");
                exchange.getIn().setBody(objectMapper.writeValueAsString(evento));
                exchange.getIn().setHeader("Content-Type", "application/json");
            })
            .to(APPOINTMENTS_EXCHANGE)
            .log(LoggingLevel.INFO, "[PUB/SUB] Evento publicado en appointments.events");

        // ── RF4: FILE TRANSFER → auditoria-citas.csv ─────────────────────
        from("direct:generar-csv")
            .routeId("ruta-generar-csv")
            .log(LoggingLevel.INFO, "[CSV] Escribiendo auditoría: ${body.idCita}")
            .process(exchange -> {
                CitaRequest cita = exchange.getIn().getBody(CitaRequest.class);
                Files.createDirectories(Paths.get(outboxPath));
                String ruta  = outboxPath + "/" + csvFilename;
                boolean nuevo = !Files.exists(Paths.get(ruta));
                try (PrintWriter pw = new PrintWriter(new FileWriter(ruta, true))) {
                    if (nuevo) pw.println("idCita,paciente,correo,especialidad,fechaCita,sede,valor");
                    pw.printf("%s,%s,%s,%s,%s,%s,%s%n",
                        csv(cita.getIdCita()), csv(cita.getPaciente()), csv(cita.getCorreo()),
                        csv(cita.getEspecialidad()), csv(cita.getFechaCita()),
                        csv(cita.getSede()), cita.getValor().toPlainString());
                }
            })
            .log(LoggingLevel.INFO, "[CSV] Registro escrito en auditoria-citas.csv");
    }

    private String csv(String v) {
        if (v == null) return "";
        if (v.contains(",") || v.contains("\"") || v.contains("\n"))
            return "\"" + v.replace("\"", "\"\"") + "\"";
        return v;
    }
}
