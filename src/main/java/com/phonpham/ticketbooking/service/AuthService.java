package com.phonpham.ticketbooking.service;

import com.phonpham.ticketbooking.dto.AuthResponse;
import com.phonpham.ticketbooking.dto.LoginRequest;
import com.phonpham.ticketbooking.dto.RegisterRequest;
import com.phonpham.ticketbooking.dto.TokenRefreshRequest;
import com.phonpham.ticketbooking.entity.RefreshToken;
import com.phonpham.ticketbooking.entity.Role;
import com.phonpham.ticketbooking.entity.User;
import com.phonpham.ticketbooking.exception.BadRequestException;
import com.phonpham.ticketbooking.repository.UserRepository;
import com.phonpham.ticketbooking.security.JwtService;
import com.phonpham.ticketbooking.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final AuthenticationManager authenticationManager;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final RefreshTokenService refreshTokenService;

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        if (userRepository.existsByUsername(request.getUsername())) {
            throw new BadRequestException("Username is already taken");
        }

        if (userRepository.existsByEmail(request.getEmail())) {
            throw new BadRequestException("Email is already in use");
        }

        User user = User.builder()
                .username(request.getUsername())
                .email(request.getEmail())
                .password(passwordEncoder.encode(request.getPassword()))
                .fullName(request.getFullName())
                .role(Role.ROLE_USER)
                .enabled(true)
                .build();

        User savedUser = userRepository.save(user);
        UserPrincipal userPrincipal = UserPrincipal.create(savedUser);

        String accessToken = jwtService.generateToken(userPrincipal);
        RefreshToken refreshToken = refreshTokenService.createRefreshToken(savedUser.getId());

        return AuthResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken.getToken())
                .tokenType("Bearer")
                .expiresIn(jwtService.getJwtExpirationMs())
                .userId(savedUser.getId())
                .username(savedUser.getUsername())
                .email(savedUser.getEmail())
                .role(savedUser.getRole().name())
                .build();
    }

    public AuthResponse login(LoginRequest request) {
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.getUsernameOrEmail(), request.getPassword())
        );

        SecurityContextHolder.getContext().setAuthentication(authentication);
        UserPrincipal userPrincipal = (UserPrincipal) authentication.getPrincipal();

        String accessToken = jwtService.generateToken(userPrincipal);
        RefreshToken refreshToken = refreshTokenService.createRefreshToken(userPrincipal.getId());

        return AuthResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken.getToken())
                .tokenType("Bearer")
                .expiresIn(jwtService.getJwtExpirationMs())
                .userId(userPrincipal.getId())
                .username(userPrincipal.getUsername())
                .email(userPrincipal.getEmail())
                .role(userPrincipal.getAuthorities().iterator().next().getAuthority())
                .build();
    }

    @Transactional
    public AuthResponse refreshToken(TokenRefreshRequest request) {
        String requestRefreshToken = request.getRefreshToken();

        return refreshTokenService.findByToken(requestRefreshToken)
                .map(refreshTokenService::verifyExpiration)
                .map(RefreshToken::getUser)
                .map(user -> {
                    UserPrincipal userPrincipal = UserPrincipal.create(user);
                    String newAccessToken = jwtService.generateToken(userPrincipal);

                    return AuthResponse.builder()
                            .accessToken(newAccessToken)
                            .refreshToken(requestRefreshToken)
                            .tokenType("Bearer")
                            .expiresIn(jwtService.getJwtExpirationMs())
                            .userId(user.getId())
                            .username(user.getUsername())
                            .email(user.getEmail())
                            .role(user.getRole().name())
                            .build();
                })
                .orElseThrow(() -> new BadRequestException("Invalid refresh token"));
    }
}
