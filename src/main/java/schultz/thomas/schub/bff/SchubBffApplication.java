package schultz.thomas.schub.bff;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;

@SpringBootApplication
@EnableFeignClients
public class SchubBffApplication {

    public static void main(String[] args) {
        SpringApplication.run(SchubBffApplication.class, args);
    }
}
