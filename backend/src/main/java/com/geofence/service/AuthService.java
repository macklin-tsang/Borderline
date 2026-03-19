package com.geofence.service;

import com.geofence.dto.request.LoginRequest;
import com.geofence.dto.request.RegisterRequest;
import com.geofence.dto.response.AuthResponse;
import com.geofence.exception.EmailAlreadyExistsException;
import com.geofence.model.User;
import com.geofence.repository.UserRepository;
import com.geofence.security.JwtService;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
public class AuthService {

    // Pre-computed BCrypt hash of a throwaway value.
    // Used on the "email not found" branch to equalize timing with the "wrong password" branch,
    // preventing timing-based email enumeration (a user gets ~100-300ms BCrypt delay either way).
    private final String dummyHash;

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AuthService(UserRepository userRepository,
                       PasswordEncoder passwordEncoder,
                       JwtService jwtService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.dummyHash = passwordEncoder.encode("dummy-timing-equalizer");
    }

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        if (userRepository.existsByEmail(request.email())) {
            throw new EmailAlreadyExistsException(request.email());
        }
        User user = new User(request.email(), passwordEncoder.encode(request.password()));
        userRepository.save(user);
        return new AuthResponse(jwtService.generate(user), user.getId().toString(), user.getEmail());
    }

    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest request) {
        Optional<User> userOpt = userRepository.findByEmail(request.email());

        // Always run BCrypt to equalize timing regardless of whether the email exists.
        // Without this, an attacker can distinguish "email not found" (fast) from
        // "wrong password" (slow) via response time alone.
        String hashToCheck = userOpt.map(User::getPasswordHash).orElse(dummyHash);
        boolean passwordMatches = passwordEncoder.matches(request.password(), hashToCheck);

        if (userOpt.isEmpty() || !passwordMatches) {
            throw new BadCredentialsException("Invalid credentials");
        }

        User user = userOpt.get();
        return new AuthResponse(jwtService.generate(user), user.getId().toString(), user.getEmail());
    }
}
