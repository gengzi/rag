package com.gengzi.ui.service.impl;

import cn.hutool.core.util.StrUtil;
import com.gengzi.dao.Knowledgebase;
import com.gengzi.dao.User;
import com.gengzi.dao.repository.KnowledgebaseRepository;
import com.gengzi.dao.repository.UserRepository;
import com.gengzi.request.UserAddReq;
import com.gengzi.request.UserDelReq;
import com.gengzi.request.UserEditReq;
import com.gengzi.request.UserLoginReq;
import com.gengzi.response.JwtResponse;
import com.gengzi.response.ResultCode;
import com.gengzi.security.BusinessException;
import com.gengzi.security.JwtTokenProvider;
import com.gengzi.security.UserPrincipal;
import com.gengzi.ui.service.UserService;
import com.gengzi.utils.IdUtils;
import com.gengzi.utils.PasswordEncoderUtil;
import jakarta.persistence.criteria.Predicate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class UserServiceImpl implements UserService {
    @Autowired
    private AuthenticationManager authenticationManager;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private KnowledgebaseRepository knowledgebaseRepository;


    @Override
    public JwtResponse login(UserLoginReq loginRequest) {
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                        loginRequest.getUsername(),
                        loginRequest.getPassword()
                )
        );
        SecurityContextHolder.getContext().setAuthentication(authentication);
        String jwt = jwtTokenProvider.generateToken(authentication);
        UserPrincipal userPrincipal = (UserPrincipal) authentication.getPrincipal();
        Collection<? extends GrantedAuthority> authorities = userPrincipal.getAuthorities();
        List<String> roles = authorities.stream()
                .map(GrantedAuthority::getAuthority) // 获取权限字符串（如 "ROLE_USER"）
                .filter(authority -> authority.startsWith("ROLE_")) // 筛选角色（可选，根据实际命名规范）
                .collect(Collectors.toList());
        return new JwtResponse(jwt,
                userPrincipal.getId(),
                userPrincipal.getUsername(), roles);
    }

    /**
     * @param userAddReq
     */
    @Override
    public void addUser(UserAddReq userAddReq) {
        User userByUsername = userRepository.findUserByUsername(userAddReq.getUsername());
        if (userByUsername != null) {
            throw new BusinessException(ResultCode.USER_EXIST);
        }
        User user = new User();
        user.setId(IdUtils.userId());
        user.setCreateTime(System.currentTimeMillis());
        user.setUsername(userAddReq.getUsername());
        user.setNickname(userAddReq.getNickname());
        user.setPassword(PasswordEncoderUtil.encodePassword(userAddReq.getPassword()));
        user.setIsSuperuser(userAddReq.getIsSuperuser());
        user.setKnowledgeIds(userAddReq.getKnowledgeIds().stream().collect(Collectors.joining(",")));
        userRepository.save(user);
    }

    /**
     * @param username
     * @param pageable
     * @return
     */
    @Override
    public Page<User> list(String username, Pageable pageable) {
        Specification<User> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            // 条件1：名称模糊匹配（如果name不为空）
            if (username != null && !username.isEmpty()) {
                predicates.add(cb.like(root.get("username"), username + "%"));
            }
            // 组合所有条件
            return cb.and(predicates.toArray(new Predicate[0]));
        };

        Page<User> all = userRepository.findAll(spec, pageable);
        for (User user : all) {
            List<String> kbIds = Arrays.stream(user.getKnowledgeIds().split(",")).toList();
            List<Knowledgebase> knowledgebaseByIdIn = knowledgebaseRepository.findKnowledgebaseByIdIn(kbIds);
            String kbNames = knowledgebaseByIdIn.stream().map(Knowledgebase::getName).collect(Collectors.joining("\n"));
            user.setKnowledgeIds(kbNames);
        }
        return all;
    }

    /**
     * @param userEditReq
     */
    @Override
    public void editUser(UserEditReq userEditReq) {
        Optional<User> byId = userRepository.findById(userEditReq.getId());
        if (!byId.isPresent()) {
            throw new BusinessException(ResultCode.USER_NOT_EXIST);
        }
        User user = byId.get();
        user.setNickname(userEditReq.getNickname());
        // 判断如果密码为空写入，就不修改密码
        if(StrUtil.isNotBlank(userEditReq.getPassword())){
            user.setPassword(PasswordEncoderUtil.encodePassword(userEditReq.getPassword()));
        }
        user.setIsSuperuser(userEditReq.getIsSuperuser());
        user.setKnowledgeIds(userEditReq.getKnowledgeIds().stream().collect(Collectors.joining(",")));
        userRepository.save(user);
    }

    /**
     * @param userDelReq
     */
    @Override
    public void delUser(UserDelReq userDelReq) {
        // 如果是管理员，不允许删除
        Optional<User> byId = userRepository.findById(userDelReq.getId());
        if (!byId.isPresent()) {
            throw new BusinessException(ResultCode.USER_NOT_EXIST);
        }
        User user = byId.get();
        if (user.getIsSuperuser()) {
            throw new BusinessException(ResultCode.USER_DEL_NOT_ALLOW);
        }
        userRepository.deleteById(userDelReq.getId());

    }
}
