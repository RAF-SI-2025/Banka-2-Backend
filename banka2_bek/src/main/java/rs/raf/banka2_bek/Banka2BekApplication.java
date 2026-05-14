package rs.raf.banka2_bek;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@EnableCaching
public class Banka2BekApplication {

	public static void main(String[] args) {
		SpringApplication.run(Banka2BekApplication.class, args);
	}

}
