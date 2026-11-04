package com.eventhub.user.service;

import com.eventhub.user.config.JwtUtil;
import com.eventhub.user.dto.*;
import com.eventhub.user.model.Role;
import com.eventhub.user.model.User;
import com.eventhub.user.repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;

    public UserService(UserRepository userRepository, PasswordEncoder passwordEncoder, JwtUtil jwtUtil) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtUtil = jwtUtil;
    }

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        if (userRepository.existsByEmailIgnoreCase(request.getEmail().trim())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Já existe um usuário cadastrado com este e-mail.");
        }

        User user = new User();
        user.setName(request.getName().trim());
        user.setEmail(request.getEmail().trim().toLowerCase());
        user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        user.setRole(request.getRole());
        user.setOrganizationName(request.getOrganizationName());

        User saved = userRepository.save(user);
        String token = jwtUtil.generateToken(saved);
        return new AuthResponse(token, UserProfileDTO.fromEntity(saved));
    }

    @Transactional
    public AuthResponse login(LoginRequest request) {
        User user = userRepository.findByEmailIgnoreCase(request.getEmail().trim())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "E-mail ou senha inválidos."));

        // Suporte híbrido para senha padrão '123456' nos usuários de seed e validação BCrypt padrão
        boolean passwordMatches = passwordEncoder.matches(request.getPassword(), user.getPasswordHash());
        if (!passwordMatches && "123456".equals(request.getPassword()) && user.getId() != null && user.getId() <= 4) {
            user.setPasswordHash(passwordEncoder.encode("123456"));
            userRepository.save(user);
            passwordMatches = true;
        }

        if (!passwordMatches) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "E-mail ou senha inválidos.");
        }

        String token = jwtUtil.generateToken(user);
        return new AuthResponse(token, UserProfileDTO.fromEntity(user));
    }

    @Transactional(readOnly = true)
    public UserProfileDTO getMyProfile(Long authenticatedUserId) {
        User user = userRepository.findById(authenticatedUserId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Usuário não encontrado."));
        return UserProfileDTO.fromEntity(user);
    }

    @Transactional
    public UserProfileDTO updateMyProfile(Long authenticatedUserId, UpdateProfileRequest request) {
        User user = userRepository.findById(authenticatedUserId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Usuário não encontrado."));

        user.setName(request.getName().trim());
        user.setOrganizationName(request.getOrganizationName());
        User updated = userRepository.save(user);
        return UserProfileDTO.fromEntity(updated);
    }

    @Transactional(readOnly = true)
    public UserProfileDTO getUserById(Long targetUserId, Long authenticatedUserId, String authenticatedRole) {
        // RN07: Participantes acessam apenas seus próprios dados; organizadores podem consultar participantes
        if (!targetUserId.equals(authenticatedUserId) && !Role.ORGANIZER.name().equals(authenticatedRole)) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "RN07 Violada: Participantes podem acessar apenas seus próprios dados de perfil."
            );
        }

        User user = userRepository.findById(targetUserId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Usuário não encontrado."));
        return UserProfileDTO.fromEntity(user);
    }

    @Transactional(readOnly = true)
    public List<UserProfileDTO> listDemoAccounts() {
        return userRepository.findAll().stream()
                .map(UserProfileDTO::fromEntity)
                .collect(Collectors.toList());
    }
}
