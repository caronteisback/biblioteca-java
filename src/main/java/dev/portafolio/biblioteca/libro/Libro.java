package dev.portafolio.biblioteca.libro;

import dev.portafolio.biblioteca.comun.EntidadBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@Entity
@Table(name = "libro")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@ToString(onlyExplicitlyIncluded = true)
public class Libro extends EntidadBase {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Setter(AccessLevel.NONE)
	@ToString.Include
	private Long id;

	@Column(nullable = false, length = 13)
	@ToString.Include
	private String isbn;

	@Column(nullable = false, length = 200)
	private String titulo;

	@Column(nullable = false, length = 150)
	private String autor;

	@Column(name = "anio_publicacion")
	private Integer anioPublicacion;

	@Builder
	public Libro(String isbn, String titulo, String autor, Integer anioPublicacion) {
		this.isbn = isbn;
		this.titulo = titulo;
		this.autor = autor;
		this.anioPublicacion = anioPublicacion;
	}

}
