package io.gsp26se16.moni;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;

@SpringBootApplication
@EnableFeignClients

public class IeltsSystemBackendApplication {

    public static void main(String[] args) {
        SpringApplication.run(IeltsSystemBackendApplication.class, args);
    }

}
