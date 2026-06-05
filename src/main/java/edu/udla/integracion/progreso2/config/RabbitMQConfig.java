package edu.udla.integracion.progreso2.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.*;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configuración de RabbitMQ para el sistema Salud360.
 *
 * Define:
 * - billing.queue: cola Point-to-Point para facturación (un solo consumidor)
 * - appointments.events: exchange fanout para Publish/Subscribe
 * - notifications.queue: suscriptora del evento de cita confirmada
 * - analytics.queue: suscriptora del evento de cita confirmada
 */
@Configuration
public class RabbitMQConfig {

    private static final Logger logger = LoggerFactory.getLogger(RabbitMQConfig.class);

    // -------------------------------------------------------------------------
    // RF2 - Point-to-Point: cola de facturación
    // -------------------------------------------------------------------------

    /**
     * Cola Point-to-Point para el sistema de facturación.
     * Solo un consumidor puede procesar cada mensaje.
     * durable=true: los mensajes sobreviven reinicios del broker.
     */
    @Bean
    public Queue billingQueue() {
        logger.info("Configurando cola Point-to-Point: billing.queue");
        return QueueBuilder.durable("billing.queue").build();
    }

    // -------------------------------------------------------------------------
    // RF3 - Publish/Subscribe: exchange fanout + colas suscriptoras
    // -------------------------------------------------------------------------

    /**
     * Exchange tipo fanout para distribuir el evento CITA_CONFIRMADA
     * a todos los consumidores suscritos simultáneamente.
     */
    @Bean
    public FanoutExchange appointmentsExchange() {
        logger.info("Configurando exchange Pub/Sub (fanout): appointments.events");
        return new FanoutExchange("appointments.events", true, false);
    }

    /**
     * Cola para el sistema de notificaciones al paciente.
     */
    @Bean
    public Queue notificationsQueue() {
        return QueueBuilder.durable("notifications.queue").build();
    }

    /**
     * Cola para el sistema de analítica operativa.
     */
    @Bean
    public Queue analyticsQueue() {
        return QueueBuilder.durable("analytics.queue").build();
    }

    /**
     * Binding: notifications.queue se suscribe al exchange appointments.events.
     * Recibirá una copia de cada evento CITA_CONFIRMADA publicado.
     */
    @Bean
    public Binding notificationsBinding(Queue notificationsQueue, FanoutExchange appointmentsExchange) {
        logger.info("Binding: notifications.queue → appointments.events");
        return BindingBuilder.bind(notificationsQueue).to(appointmentsExchange);
    }

    /**
     * Binding: analytics.queue se suscribe al exchange appointments.events.
     * Recibirá una copia independiente de cada evento CITA_CONFIRMADA publicado.
     */
    @Bean
    public Binding analyticsBinding(Queue analyticsQueue, FanoutExchange appointmentsExchange) {
        logger.info("Binding: analytics.queue → appointments.events");
        return BindingBuilder.bind(analyticsQueue).to(appointmentsExchange);
    }
}
