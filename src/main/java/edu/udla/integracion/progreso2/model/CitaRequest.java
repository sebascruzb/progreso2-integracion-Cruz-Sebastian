package edu.udla.integracion.progreso2.model;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;

/**
 * Modelo que representa una solicitud de cita médica.
 * Contiene todos los campos requeridos por el sistema Salud360.
 */
public class CitaRequest {

    @JsonProperty("idCita")
    private String idCita;

    @JsonProperty("paciente")
    private String paciente;

    @JsonProperty("correo")
    private String correo;

    @JsonProperty("especialidad")
    private String especialidad;

    @JsonProperty("fechaCita")
    private String fechaCita;

    @JsonProperty("sede")
    private String sede;

    @JsonProperty("valor")
    private BigDecimal valor;

    // Constructors
    public CitaRequest() {}

    public CitaRequest(String idCita, String paciente, String correo,
                       String especialidad, String fechaCita, String sede, BigDecimal valor) {
        this.idCita = idCita;
        this.paciente = paciente;
        this.correo = correo;
        this.especialidad = especialidad;
        this.fechaCita = fechaCita;
        this.sede = sede;
        this.valor = valor;
    }

    // Getters and Setters
    public String getIdCita() { return idCita; }
    public void setIdCita(String idCita) { this.idCita = idCita; }

    public String getPaciente() { return paciente; }
    public void setPaciente(String paciente) { this.paciente = paciente; }

    public String getCorreo() { return correo; }
    public void setCorreo(String correo) { this.correo = correo; }

    public String getEspecialidad() { return especialidad; }
    public void setEspecialidad(String especialidad) { this.especialidad = especialidad; }

    public String getFechaCita() { return fechaCita; }
    public void setFechaCita(String fechaCita) { this.fechaCita = fechaCita; }

    public String getSede() { return sede; }
    public void setSede(String sede) { this.sede = sede; }

    public BigDecimal getValor() { return valor; }
    public void setValor(BigDecimal valor) { this.valor = valor; }

    @Override
    public String toString() {
        return "CitaRequest{" +
                "idCita='" + idCita + '\'' +
                ", paciente='" + paciente + '\'' +
                ", correo='" + correo + '\'' +
                ", especialidad='" + especialidad + '\'' +
                ", fechaCita='" + fechaCita + '\'' +
                ", sede='" + sede + '\'' +
                ", valor=" + valor +
                '}';
    }
}
