package dev.portafolio.biblioteca.multa;

import dev.portafolio.biblioteca.comun.EntidadBase;
import dev.portafolio.biblioteca.prestamo.Prestamo;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@Entity
@Table(name = "multa")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@ToString(onlyExplicitlyIncluded = true)
public class Multa extends EntidadBase {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Setter(AccessLevel.NONE)
	@ToString.Include
	private Long id;

	@OneToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "prestamo_id", nullable = false, unique = true)
	private Prestamo prestamo;

	@Column(name = "dias_retraso", nullable = false)
	private int diasRetraso;

	@Column(nullable = false, precision = 10, scale = 2)
	private BigDecimal monto;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 10)
	@Setter(AccessLevel.NONE)
	@ToString.Include
	private EstadoMulta estado = EstadoMulta.PENDING;

	@Column(name = "fecha_generacion", nullable = false)
	private LocalDate fechaGeneracion;

	@Column(name = "fecha_pago")
	@Setter(AccessLevel.NONE)
	private LocalDate fechaPago;

	@Builder
	public Multa(Prestamo prestamo, int diasRetraso, BigDecimal monto, LocalDate fechaGeneracion) {
		this.prestamo = prestamo;
		this.diasRetraso = diasRetraso;
		this.monto = monto;
		this.fechaGeneracion = fechaGeneracion;
	}

	public boolean isPendiente() {
		return estado == EstadoMulta.PENDING;
	}

	/** Marks the whole fine as paid. There is no gateway and no partial payment. */
	public void pagar(LocalDate hoy) {
		this.estado = EstadoMulta.PAID;
		this.fechaPago = hoy;
	}

}
