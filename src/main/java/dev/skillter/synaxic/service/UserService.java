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
        String googleSub = oAuth2User.getAttribute("sub");
        String email = oAuth2User.getAttribute("email");

        return userRepository.findByGoogleSub(googleSub)
                .orElseGet(() -> createNewUser(googleSub, email));
    }

    private User createNewUser(String googleSub, String email) {
        User newUser = User.builder()
                .googleSub(googleSub)
                .email(email)
                .build();
        log.info("Creating new user for email: {}", email);
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