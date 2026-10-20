package dev.portafolio.biblioteca;

import org.springframework.boot.SpringApplication;

public class TestBibliotecaJavaApplication {

	public static void main(String[] args) {
		SpringApplication.from(BibliotecaJavaApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}
