package com.gengzi.service.impl;

import com.gengzi.request.UserLoginReq;
import com.gengzi.response.JwtResponse;
import com.gengzi.security.CustomReactiveUserDetailsService;
import com.gengzi.security.JwtTokenProvider;
import com.gengzi.security.UserPrincipal;
import com.gengzi.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class UserServiceImpl implements UserService {

    @Autowired
    private CustomReactiveUserDetailsService userDetailsService;
    @Autowired
    private JwtTokenProvider jwtTokenProvider;
    @Autowired
    private PasswordEncoder passwordEncoder;


    @Override
    public Mono<JwtResponse> login(UserLoginReq loginRequest) {

        return userDetailsService.findByUsername(loginRequest.getUsername())
                .filter(user -> passwordEncoder.matches(loginRequest.getPassword(), user.getPassword()))
                .map(user -> {
                    UserPrincipal principal = (UserPrincipal) user;
                    String token = jwtTokenProvider.generateToken(user);
                    String username = principal.getUsername();
                    String id = principal.getId();
                    List<String> roles = principal.getAuthorities().stream()
                            .map(GrantedAuthority::getAuthority)
                            .collect(Collectors.toList());
                    // 返回 Token 给前端
                    return new JwtResponse(token, id, username, roles);
                });


    }

}
