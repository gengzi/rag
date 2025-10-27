package com.gengzi.ui.service;

import com.gengzi.dao.User;
import com.gengzi.dao.repository.UserRepository;
import com.gengzi.response.ResultCode;
import com.gengzi.security.BusinessException;
import com.gengzi.security.UserPrincipal;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

/**
 * rag用户详情服务实现 spring security UserDetailsService接口
 */
@Service
public class RagUserDetailsService implements UserDetailsService {

    @Autowired
    private UserRepository userRepository;


    /**
     * 根据用户名获取用户信息
     *
     * @param username
     * @return
     * @throws UsernameNotFoundException
     */
    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        User userByUsername = userRepository.findUserByUsername(username);
        if(userByUsername == null){
                throw new BusinessException(ResultCode.USER_NOT_EXIST);
        }
        return UserPrincipal.create(userByUsername);
    }
}
