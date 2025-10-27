package com.gengzi.ui.controller;


import com.gengzi.request.UserAddReq;
import com.gengzi.request.UserDelReq;
import com.gengzi.request.UserEditReq;
import com.gengzi.request.UserLoginReq;
import com.gengzi.response.JwtResponse;
import com.gengzi.response.Result;
import com.gengzi.ui.service.UserService;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;

@RestController
@Tag(name = "用户管理", description = "用户管理")
public class UserController {


    @Autowired
    private UserService userService;

    @PostMapping("/user/login")
    @ResponseBody
    public Result<?> authenticateUser(@Valid @RequestBody UserLoginReq loginRequest) {
        JwtResponse login = userService.login(loginRequest);
        return Result.success(login);
    }


    /**
     * 用户管理-添加用户
     */
    @PostMapping("/user/add")
    @ResponseBody
    public Result<?> addUser(@RequestBody UserAddReq userAddReq) {
        userService.addUser(userAddReq);
        return Result.success(true);
    }

    @GetMapping("/list")
    @ResponseBody
    public Result<?> list(@RequestParam(required = false) String username,
                          @PageableDefault(page = 0, size = 10) Pageable pageable) {
        return Result.success(userService.list(username, pageable));
    }


    /**
     * 用户管理-添加用户
     */
    @PostMapping("/user/edit")
    @ResponseBody
    public Result<?> editUser(@RequestBody UserEditReq userEditReq) {
        userService.editUser(userEditReq);
        return Result.success(true);
    }

    /**
     * 用户管理-添加用户
     */
    @PostMapping("/user/del")
    @ResponseBody
    public Result<?> delUser(@RequestBody UserDelReq userDelReq) {
        userService.delUser(userDelReq);
        return Result.success(true);
    }


}
