package com.app.eventmanagmentsystem.service.impl;

import com.app.eventmanagmentsystem.payload.auth.LoginRequest;
import com.app.eventmanagmentsystem.payload.auth.LoginResponse;
import com.app.eventmanagmentsystem.security.AppUserDetails;
import com.app.eventmanagmentsystem.security.jwt.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final AuthenticationManager authenticationManager;
    private final JwtTokenProvider jwtTokenProvider;

    public LoginResponse login(LoginRequest request) {
        // Spring Security handles password comparison and account-status checks
        Authentication auth = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                        request.getEmail().toLowerCase().trim(),
                        request.getPassword()
                )
        );

        AppUserDetails user = (AppUserDetails) auth.getPrincipal();
        Set<String> permissions = user.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.toSet());

        String token = jwtTokenProvider.generateAccessToken(
                user.getUserId(),
                user.getEmail(),
                Map.of("permissions", permissions)
        );

        return LoginResponse.builder()
                .accessToken(token)
                .expiresIn(jwtTokenProvider.getAccessTokenExpiryMs())
                .userId(user.getUserId())
                .email(user.getEmail())
                .fullName(user.getFullName())
                .permissions(permissions)
                .build();
    }
}

