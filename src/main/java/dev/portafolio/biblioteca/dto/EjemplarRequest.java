package dev.portafolio.biblioteca.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record EjemplarRequest(@NotBlank @Size(max = 30) String codigo) {
}
