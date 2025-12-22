package dev.skillter.synaxic.service;

import dev.skillter.synaxic.model.entity.User;
import dev.skillter.synaxic.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserService {

    private final UserRepository userRepository;

    @Transactional
    public User processOAuth2User(OAuth2User oAuth2User) {
        log.info("Processing OAuth2 user with attributes: {}", oAuth2User.getAttributes());
        String googleSub = oAuth2User.getAttribute("sub");
        String email = oAuth2User.getAttribute("email");
        String name = oAuth2User.getAttribute("name");

        log.info("Extracted values - sub: {}, email: {}, name: {}", googleSub, email, name);

        if (googleSub == null) {
            log.error("No 'sub' attribute found in OAuth2User");
            throw new RuntimeException("Missing 'sub' attribute in OAuth2User");
        }

        if (email == null) {
            log.error("No 'email' attribute found in OAuth2User");
            throw new RuntimeException("Missing 'email' attribute in OAuth2User");
        }

        User user = userRepository.findByGoogleSub(googleSub)
                .orElseGet(() -> createNewUser(googleSub, email, name));

        log.info("User processed successfully: {}", user.getId());
        return user;
    }

    private User createNewUser(String googleSub, String email, String name) {
        User newUser = User.builder()
                .googleSub(googleSub)
                .email(email)
                .build();
        log.info("Creating new user for email: {}, name: {}", email, name);
        return userRepository.save(newUser);
    }

    public java.util.Optional<User> findByEmail(String email) {
        return userRepository.findByEmail(email);
    }

    @Transactional
    public void deleteUser(Long userId) {
        userRepository.deleteById(userId);
    }
}