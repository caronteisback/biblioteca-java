package dev.portafolio.biblioteca.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import java.time.ZoneId;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Business parameters of the library. Values live in {@code application.yml}; an invalid value
 * stops the application at startup instead of failing later in the middle of a loan.
 */
@Validated
@ConfigurationProperties(prefix = "biblioteca")
public record BibliotecaProperties(
		@NotNull ZoneId zonaHoraria,
		@Valid @NotNull Prestamo prestamo,
		@Valid @NotNull Multa multa,
		@Valid @NotNull Score score) {

	/**
	 * @param diasPlazo days granted by a loan and by each renewal
	 * @param maxActivos active loans allowed per member
	 * @param maxRenovaciones renewals per loan; at most 3 because of the schema CHECK constraint
	 * @param alertaDiasPrevios days before the due date that raise the DUE_SOON alert
	 */
	public record Prestamo(
			@Positive int diasPlazo,
			@Positive int maxActivos,
			@Min(0) @Max(3) int maxRenovaciones,
			@Min(0) int alertaDiasPrevios) {
	}

	/** @param porDia fine per day of delay, in soles */
	public record Multa(@NotNull @DecimalMin("0.01") BigDecimal porDia) {
	}

	/**
	 * @param inicial starting score
	 * @param penalizacionBase points lost per late loan
	 * @param penalizacionPorDia extra points lost per day of delay
	 * @param umbralBloqueo members below this score cannot start new loans
	 */
	public record Score(
			@Positive int inicial,
			@Min(0) int penalizacionBase,
			@Min(0) int penalizacionPorDia,
			@Min(0) int umbralBloqueo) {
	}

}
