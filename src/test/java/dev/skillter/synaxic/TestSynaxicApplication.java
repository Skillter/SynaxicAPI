package dev.skillter.synaxic;

import org.springframework.boot.SpringApplication;
import org.testcontainers.utility.TestcontainersConfiguration;

public class TestSynaxicApplication {

	public static void main(String[] args) {
		SpringApplication.from(SynaxicApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}
