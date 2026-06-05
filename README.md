# Progreso 2 – Integración de Sistemas: Salud360

**Nombre del estudiante:** [Tu Nombre Completo]  
**Materia:** Integración de Sistemas  
**Evaluación:** Examen Práctico Progreso 2  
**Fecha:** Junio 2026

---

## Descripción de la solución

Solución de integración para el sistema **Salud360**, que automatiza el flujo de registro de citas médicas confirmadas mediante:

- Una **API REST** (Spring Boot) para recibir solicitudes de cita.
- **Apache Camel** para orquestar el flujo de integración.
- **RabbitMQ** para mensajería (Point-to-Point y Publish/Subscribe).
- **Archivos CSV** para integrar el sistema legado de auditoría.

---

## Tecnologías utilizadas

| Tecnología | Versión | Uso |
|---|---|---|
| Java | 17 | Lenguaje principal |
| Spring Boot | 3.2.0 | Framework web y DI |
| Apache Camel | 4.3.0 | Rutas de integración |
| RabbitMQ | 3.12 | Broker de mensajería |
| Docker Compose | – | Levantar RabbitMQ |
| Maven | 3.x | Gestión de dependencias |

---

## Instrucciones para levantar RabbitMQ

**Prerequisito:** Tener Docker Desktop instalado y en ejecución.

```bash
# Desde la raíz del proyecto:
docker-compose up -d

# Verificar que está corriendo:
docker-compose ps

# Acceder al panel de administración:
# URL: http://localhost:15672
# Usuario: admin
# Contraseña: admin123
```

Las colas y el exchange se crean automáticamente al iniciar la aplicación.

---

## Instrucciones para ejecutar la aplicación

```bash
# 1. Levantar RabbitMQ primero (ver sección anterior)

# 2. Compilar el proyecto
mvn clean install -DskipTests

# 3. Ejecutar la aplicación
mvn spring-boot:run

# La API estará disponible en: http://localhost:8080
```

---

## Endpoint disponible

| Método | URL | Descripción |
|---|---|---|
| `POST` | `/api/citas` | Registrar una solicitud de cita médica |
| `GET` | `/api/health` | Verificar estado de la API |

---

## Ejemplo de request válido

```bash
curl -X POST http://localhost:8080/api/citas \
  -H "Content-Type: application/json" \
  -d '{
    "idCita": "CITA-1001",
    "paciente": "Ana Torres",
    "correo": "ana.torres@email.com",
    "especialidad": "Cardiología",
    "fechaCita": "2026-06-15",
    "sede": "Centro Norte",
    "valor": 45.50
  }'
```

**Respuesta esperada (HTTP 201):**
```json
{
  "estado": "CONFIRMADA",
  "timestamp": "2026-06-04 10:30:00",
  "idCita": "CITA-1001",
  "paciente": "Ana Torres",
  "mensaje": "Cita registrada y distribuida exitosamente a todos los sistemas.",
  "sistemas": [
    "billing.queue (facturación - Point-to-Point)",
    "appointments.events → notifications.queue (notificaciones - Pub/Sub)",
    "appointments.events → analytics.queue (analítica - Pub/Sub)",
    "data/outbox/auditoria-citas.csv (sistema legado - File Transfer)"
  ]
}
```

---

## Ejemplo de request inválido

```bash
curl -X POST http://localhost:8080/api/citas \
  -H "Content-Type: application/json" \
  -d '{
    "idCita": "",
    "paciente": "Ana Torres",
    "correo": "",
    "especialidad": "Cardiología",
    "fechaCita": "2026-06-15",
    "sede": "Centro Norte",
    "valor": -10
  }'
```

**Respuesta esperada (HTTP 400):**
```json
{
  "estado": "RECHAZADA",
  "timestamp": "2026-06-04 10:31:00",
  "idCita": "",
  "errores": [
    "El campo 'idCita' es obligatorio",
    "El campo 'correo' es obligatorio",
    "El campo 'valor' debe ser mayor a 0"
  ],
  "mensaje": "La solicitud de cita fue rechazada por datos inválidos."
}
```

---

## Explicación de patrones de integración aplicados

### Point-to-Point (RF2)

**Dónde se aplica:** Envío del comando de facturación a la cola `billing.queue`.

**Por qué:** El sistema de facturación debe recibir y procesar **exactamente una vez** cada solicitud de cita. Si múltiples instancias del sistema de facturación están activas, solo una procesará cada mensaje. Esto garantiza que no se emitan facturas duplicadas.

### Publish/Subscribe (RF3)

**Dónde se aplica:** Publicación del evento `CITA_CONFIRMADA` al exchange fanout `appointments.events`, que distribuye el evento a `notifications.queue` y `analytics.queue`.

**Por qué:** Múltiples sistemas (notificaciones y analítica) necesitan reaccionar al mismo evento de forma independiente. Con Pub/Sub, cada sistema recibe su propia copia del evento sin acoplarse entre sí ni depender del sistema de agenda.

### Transferencia de archivos (RF4)

**Dónde se aplica:** Generación del archivo `data/outbox/auditoria-citas.csv`.

**Por qué:** El sistema legado de auditoría no tiene API ni conexión a mensajería. La única forma de integración posible es mediante un archivo CSV depositado en una carpeta compartida que el sistema legado lee periódicamente.

### Manejo de errores (RF5)

**Dónde se aplica:**
- Validación en `CitaValidationService`: rechaza y registra citas con datos inválidos.
- `onException` global en Apache Camel: captura errores de procesamiento.
- Archivo `data/errors/citas-rechazadas.log`: registra todos los rechazos con timestamp, idCita, motivo y payload.

---

## Estructura del proyecto

```
progreso2-integracion/
├── README.md
├── docker-compose.yml
├── pom.xml
├── src/main/java/edu/udla/integracion/progreso2/
│   ├── Progreso2Application.java
│   ├── controller/CitaController.java
│   ├── model/CitaRequest.java
│   ├── routes/CitaIntegrationRoute.java
│   └── service/
│       ├── CitaValidationService.java
│       └── RabbitMQConfig.java
├── src/main/resources/application.properties
├── data/
│   ├── outbox/auditoria-citas.csv
│   └── errors/citas-rechazadas.log
└── docs/capturas/
```

---

## Evidencia esperada para verificar el funcionamiento

1. La aplicación inicia sin errores en consola.
2. RabbitMQ Management UI (`http://localhost:15672`) muestra las colas y el exchange creados.
3. Postman/curl con request válido devuelve HTTP 201 y estado "CONFIRMADA".
4. En RabbitMQ Management UI, `billing.queue` muestra 1 mensaje encolado.
5. `notifications.queue` y `analytics.queue` muestran 1 mensaje cada una.
6. El archivo `data/outbox/auditoria-citas.csv` contiene una nueva línea con los datos de la cita.
7. Un request inválido devuelve HTTP 400 y genera entrada en `data/errors/citas-rechazadas.log`.
