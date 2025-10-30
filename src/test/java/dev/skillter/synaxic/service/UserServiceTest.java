package dev.skillter.synaxic.service;

import dev.skillter.synaxic.model.entity.User;
import dev.skillter.synaxic.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.oauth2.core.user.OAuth2User;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.lenient;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private UserService userService;

    private User testUser;
    private OAuth2User oauth2User;

    @BeforeEach
    void setUp() {
        testUser = User.builder()
                .id(1L)
                .googleSub("google123")
                .email("test@example.com")
                .createdAt(Instant.now())
                .build();

        oauth2User = mock(OAuth2User.class);
        lenient().when(oauth2User.getAttribute("sub")).thenReturn("google123");
        lenient().when(oauth2User.getAttribute("email")).thenReturn("test@example.com");
    }

    @Test
    void processOAuth2User_WhenUserExists_ShouldReturnExistingUser() {
        when(userRepository.findByGoogleSub("google123")).thenReturn(Optional.of(testUser));

        User result = userService.processOAuth2User(oauth2User);

        assertThat(result).isEqualTo(testUser);
        verify(userRepository).findByGoogleSub("google123");
        verify(userRepository, never()).save(any());
    }

    @Test
    void processOAuth2User_WhenUserDoesNotExist_ShouldCreateNewUser() {
        when(userRepository.findByGoogleSub("google123")).thenReturn(Optional.empty());
        when(userRepository.save(any(User.class))).thenReturn(testUser);

        User result = userService.processOAuth2User(oauth2User);

        assertThat(result).isNotNull();
        verify(userRepository).findByGoogleSub("google123");
        verify(userRepository).save(any(User.class));
    }

    @Test
    void findByEmail_WhenUserExists_ShouldReturnUser() {
        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(testUser));

        Optional<User> result = userService.findByEmail("test@example.com");

        assertThat(result).isPresent();
        assertThat(result.get()).isEqualTo(testUser);
        verify(userRepository).findByEmail("test@example.com");
    }

    @Test
    void findByEmail_WhenUserDoesNotExist_ShouldReturnEmpty() {
        when(userRepository.findByEmail(anyString())).thenReturn(Optional.empty());

        Optional<User> result = userService.findByEmail("nonexistent@example.com");

        assertThat(result).isEmpty();
        verify(userRepository).findByEmail("nonexistent@example.com");
    }

    @Test
    void deleteUser_ShouldCallRepository() {
        doNothing().when(userRepository).deleteById(1L);

        userService.deleteUser(1L);

        verify(userRepository).deleteById(1L);
    }
}
