package dev.portafolio.biblioteca.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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

/** An account that signs in. The password hash never leaves the service layer: no DTO carries it. */
@Entity
@Table(name = "usuario")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@ToString(onlyExplicitlyIncluded = true)
public class Usuario extends EntidadBase {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@ToString.Include
	private Long id;

	@Column(nullable = false, length = 150)
	@ToString.Include
	private String email;

	@Column(name = "password_hash", nullable = false, length = 100)
	private String passwordHash;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 10)
	@ToString.Include
	private Rol rol;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "socio_id")
	private Socio socio;

	@Column(nullable = false)
	@Setter(AccessLevel.NONE)
	private boolean activo = true;

	@Builder
	public Usuario(String email, String passwordHash, Rol rol, Socio socio) {
		this.email = email;
		this.passwordHash = passwordHash;
		this.rol = rol;
		this.socio = socio;
	}

}
