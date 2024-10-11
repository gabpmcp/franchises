package com.nequi.franchises;

import io.github.cdimascio.dotenv.Dotenv;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.util.stream.Collectors;

@SpringBootApplication
public class FranchisesApplication {

	public static void main(String[] args) {
		Dotenv dotenv = Dotenv.load();  // Cargar las variables del archivo .env
		var result = dotenv.entries().stream().peek(entry -> System.setProperty(entry.getKey(), entry.getValue())).collect(Collectors.toList());
		System.out.println(result);

		SpringApplication.run(FranchisesApplication.class, args);
	}

}
