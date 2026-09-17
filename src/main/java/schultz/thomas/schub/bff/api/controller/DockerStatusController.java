package schultz.thomas.schub.bff.api.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class DockerStatusController {

    @GetMapping(value = "/docker/{containerID}")
    public String getTestData(@PathVariable Integer containerID) {
        if (containerID == 1) {
            return "Hello World";
        }
        return "Wrong id";
    }
}
