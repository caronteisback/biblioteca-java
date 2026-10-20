package dev.portafolio.biblioteca.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@Entity
@Table(name = "ejemplar")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@ToString(onlyExplicitlyIncluded = true)
public class Ejemplar extends EntidadBase {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Setter(AccessLevel.NONE)
	@ToString.Include
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "libro_id", nullable = false)
	private Libro libro;

	@Column(nullable = false, length = 30)
	@ToString.Include
	private String codigo;

	@Column(nullable = false)
	@Setter(AccessLevel.NONE)
	private boolean activo = true;

	@Builder
	public Ejemplar(Libro libro, String codigo) {
		this.libro = libro;
		this.codigo = codigo;
	}

	public void darDeBaja() {
		this.activo = false;
	}

	public void reactivar() {
		this.activo = true;
	}

}
