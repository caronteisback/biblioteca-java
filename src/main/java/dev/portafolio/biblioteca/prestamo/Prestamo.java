package dev.portafolio.biblioteca.prestamo;

import dev.portafolio.biblioteca.comun.EntidadBase;
import dev.portafolio.biblioteca.ejemplar.Ejemplar;
import dev.portafolio.biblioteca.socio.Socio;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@Entity
@Table(name = "prestamo")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@ToString(onlyExplicitlyIncluded = true)
public class Prestamo extends EntidadBase {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Setter(AccessLevel.NONE)
	@ToString.Include
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "ejemplar_id", nullable = false)
	private Ejemplar ejemplar;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "socio_id", nullable = false)
	private Socio socio;

	@Column(name = "fecha_prestamo", nullable = false)
	private LocalDate fechaPrestamo;

	@Column(name = "fecha_vencimiento", nullable = false)
	@Setter(AccessLevel.NONE)
	private LocalDate fechaVencimiento;

	@Column(name = "fecha_devolucion")
	@Setter(AccessLevel.NONE)
	private LocalDate fechaDevolucion;

	@Column(nullable = false)
	@Setter(AccessLevel.NONE)
	private int renovaciones;

	@Builder
	public Prestamo(Ejemplar ejemplar, Socio socio, LocalDate fechaPrestamo, LocalDate fechaVencimiento) {
		this.ejemplar = ejemplar;
		this.socio = socio;
		this.fechaPrestamo = fechaPrestamo;
		this.fechaVencimiento = fechaVencimiento;
	}

	public boolean isActivo() {
		return fechaDevolucion == null;
	}

	/** An active loan is overdue once today is after its due date; due day itself is still on time. */
	public boolean isVencido(LocalDate hoy) {
		return isActivo() && hoy.isAfter(fechaVencimiento);
	}

	/**
	 * Days of delay: counted up to the return date for a returned loan and up to today for an active one.
	 * Never negative.
	 */
	public int diasRetraso(LocalDate hoy) {
		LocalDate hasta = isActivo() ? hoy : fechaDevolucion;
		return (int) Math.max(0, ChronoUnit.DAYS.between(fechaVencimiento, hasta));
	}

	/** Moves the due date forward from the current due date (not from today) and counts the renewal. */
	public void renovar(int dias) {
		this.fechaVencimiento = fechaVencimiento.plusDays(dias);
		this.renovaciones++;
	}

	public void devolver(LocalDate hoy) {
		this.fechaDevolucion = hoy;
	}

}
