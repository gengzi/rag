package com.gengzi.service;



import com.gengzi.request.UserLoginReq;
import com.gengzi.response.JwtResponse;
import reactor.core.publisher.Mono;


public interface UserService {


    Mono<JwtResponse> login(UserLoginReq loginRequest);


}
