package schultz.thomas.schub.bff;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;
import org.springframework.cloud.openfeign.EnableFeignClients;

// Sans compte local, Spring Boot en créerait un d'office, mot de passe généré écrit au journal : on l'en empêche.
@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)
@EnableFeignClients
public class SchubBffApplication {

    public static void main(String[] args) {
        SpringApplication.run(SchubBffApplication.class, args);
    }
}
