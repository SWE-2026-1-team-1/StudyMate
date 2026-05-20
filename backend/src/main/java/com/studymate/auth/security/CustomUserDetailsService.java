package com.studymate.auth.security;

import com.studymate.auth.domain.User;
import com.studymate.auth.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class CustomUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    // - JwtAuthenticationFilter 에서 userId(String) 로 호출
    public UserDetails loadUserByUserId(Long userId) {
        User user = userRepository.findByIdAndIsDeletedFalse(userId)
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + userId));
        return new CustomUserDetails(user);
    }

    // - UserDetailsService 기본 구현 (username = email); Spring Security 폼 로그인 미사용
    @Override
    public UserDetails loadUserByUsername(String username) {
        throw new UnsupportedOperationException("Use loadUserByUserId");
    }
}
