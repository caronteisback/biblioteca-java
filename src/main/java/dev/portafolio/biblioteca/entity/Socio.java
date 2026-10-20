package dev.portafolio.biblioteca.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@Entity
@Table(name = "socio")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@ToString(onlyExplicitlyIncluded = true)
public class Socio extends EntidadBase {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Setter(AccessLevel.NONE)
	@ToString.Include
	private Long id;

	@Column(nullable = false, length = 120)
	private String nombre;

	@Column(nullable = false, length = 150)
	private String email;

	@Column(nullable = false)
	@Setter(AccessLevel.NONE)
	private boolean activo = true;

	@Column(name = "fecha_registro", nullable = false)
	@Setter(AccessLevel.NONE)
	private LocalDate fechaRegistro;

	@Builder
	public Socio(String nombre, String email, LocalDate fechaRegistro) {
		this.nombre = nombre;
		this.email = email;
		this.fechaRegistro = fechaRegistro;
	}

	public void desactivar() {
		this.activo = false;
	}

	public void reactivar() {
		this.activo = true;
	}

}
