package com.gengzi.ui.service;


import com.gengzi.dao.User;
import com.gengzi.request.UserAddReq;
import com.gengzi.request.UserDelReq;
import com.gengzi.request.UserEditReq;
import com.gengzi.request.UserLoginReq;
import com.gengzi.response.JwtResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface UserService {


    JwtResponse login(UserLoginReq loginRequest);

    void addUser(UserAddReq userAddReq);

    Page<User> list(String username, Pageable pageable);

    void editUser(UserEditReq userEditReq);

    void delUser(UserDelReq userDelReq);
}
