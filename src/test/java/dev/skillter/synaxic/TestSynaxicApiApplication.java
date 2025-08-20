package dev.skillter.synaxic;

import org.springframework.boot.SpringApplication;

public class TestSynaxicApiApplication {

	public static void main(String[] args) {
		SpringApplication.from(SynaxicApiApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}
