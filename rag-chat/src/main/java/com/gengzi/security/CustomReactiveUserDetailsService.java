package com.gengzi.security;

import com.gengzi.dao.User;
import com.gengzi.dao.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.userdetails.ReactiveUserDetailsService;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Component
public class CustomReactiveUserDetailsService implements ReactiveUserDetailsService {

    @Autowired
    private UserRepository userRepository;

    @Override
    public Mono<UserDetails> findByUsername(String username) {
        User userByUsername = userRepository.findUserByUsername(username);
        if (userByUsername == null) {
            return Mono.empty();
        }
        return Mono.justOrEmpty(UserPrincipal.create(userByUsername));
    }
}