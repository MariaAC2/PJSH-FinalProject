package com.quizapp.config;

import com.quizapp.entities.User;
import com.quizapp.enums.UserRole;
import com.quizapp.repositories.UserRepository;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
public class AdminSeeder {

    @Bean
    public ApplicationRunner seedAdmin(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder
    ) {
        return args -> {
            String email = "admin@quizapp.local";

            if (userRepository.existsByEmail(email)) {
                return; // admin already exists
            }

            User admin = new User();
            admin.setEmail(email);
            admin.setDisplayName("Admin");
            admin.setPassword(passwordEncoder.encode("Admin123!"));
            admin.setRole(UserRole.ADMIN);

            userRepository.save(admin);
        };
    }
}
