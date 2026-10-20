package dev.portafolio.biblioteca.entity;

import org.hibernate.Hibernate;

/**
 * Identity-based equality for JPA entities.
 *
 * <p>Two entities are equal when they have the same class and the same non-null id. Using
 * {@link Hibernate#getClass(Object)} keeps lazy proxies equal to the real entity, and a constant
 * hash code keeps the value stable while the id is assigned on save.
 */
public abstract class EntidadBase {

	public abstract Long getId();

	@Override
	public boolean equals(Object other) {
		if (this == other) {
			return true;
		}
		if (other == null || Hibernate.getClass(this) != Hibernate.getClass(other)) {
			return false;
		}
		Long id = getId();
		return id != null && id.equals(((EntidadBase) other).getId());
	}

	@Override
	public int hashCode() {
		return Hibernate.getClass(this).hashCode();
	}

}
