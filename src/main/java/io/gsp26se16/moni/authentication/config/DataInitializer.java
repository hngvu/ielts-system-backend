package io.gsp26se16.moni.authentication.config;


import io.gsp26se16.moni.authentication.entity.UserCredentials;
import io.gsp26se16.moni.authentication.repository.UserCredentialsRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class DataInitializer implements CommandLineRunner {
    // phung add
    private final UserCredentialsRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    public void run(String... args) {
        initializeAdminUser();
        initializeEventManagerUser();
    }

    private void initializeAdminUser() {
        String adminEmail = "admin@cap.vn";

        if (!userRepository.existsByEmail(adminEmail)) {
            UserCredentials adminUser = UserCredentials.builder()
                    .email(adminEmail)
                    .password(passwordEncoder.encode("admin123"))
                    .role(UserCredentials.Role.ADMIN)
                    .isActive(true)
                    .build();

            userRepository.save(adminUser);
            log.info("Admin user created successfully with email: {}", adminEmail);
        } else {
            log.info("Admin user already exists with email: {}", adminEmail);
        }
    }

    private void initializeEventManagerUser() {
        String expertEmail = "expert@cap.vn";

        if (!userRepository.existsByEmail(expertEmail)) {
            UserCredentials eventManagerUser = UserCredentials.builder()
                    .email(expertEmail)
                    .password(passwordEncoder.encode("12345678"))
                    .role(UserCredentials.Role.EXPERT)
                    .isActive(true)
                    .build();

            userRepository.save(eventManagerUser);
            log.info("Event Manager user created successfully with email: {}", expertEmail);
        } else {
            log.info("Event Manager user already exists with email: {}", expertEmail);
        }
    }
}
