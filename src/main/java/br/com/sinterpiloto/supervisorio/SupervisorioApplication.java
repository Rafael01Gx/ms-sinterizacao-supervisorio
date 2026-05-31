package br.com.sinterpiloto.supervisorio;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class SupervisorioApplication {

	public static void main(String[] args) {
		SpringApplication.run(SupervisorioApplication.class, args);
	}

}
