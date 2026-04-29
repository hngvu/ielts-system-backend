package io.gsp26se16.moni;

import java.util.TimeZone;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableFeignClients
@EnableScheduling
@EnableAsync
public class IeltsSystemBackendApplication {

    public static void main(String[] args) {
        // Force JVM-wide UTC so every LocalDateTime.now() and @CreationTimestamp
        // produces a UTC instant regardless of the host machine's timezone.
        // The frontend appends 'Z' before parsing, so values must be UTC for
        // displayed times to match the actual moment of the event.
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
        SpringApplication.run(IeltsSystemBackendApplication.class, args);
    }
}
