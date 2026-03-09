package io.gsp26se16.moni;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;

@SpringBootApplication
@EnableFeignClients
public class IeltsSystemBackendApplication {

    public static void main(String[] args) {
        loadEnvFile();
        SpringApplication.run(IeltsSystemBackendApplication.class, args);
    }

    private static void loadEnvFile() {
        Path envPath = Path.of(".env");
        if (!Files.exists(envPath)) return;
        try {
            Files.readAllLines(envPath).stream()
                    .map(String::trim)
                    .filter(line -> !line.isEmpty() && !line.startsWith("#") && line.contains("="))
                    .forEach(line -> {
                        int idx = line.indexOf('=');
                        String key = line.substring(0, idx).trim();
                        String value = line.substring(idx + 1).trim();
                        if (System.getProperty(key) == null && System.getenv(key) == null) {
                            System.setProperty(key, value);
                        }
                    });
        } catch (IOException e) {
            // .env loading is optional
        }
    }
}
