package com.gengzi.mq;


import org.apache.rocketmq.client.producer.SendResult;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/order")
public class OrderController {

    @Autowired
    private OrderService orderService;

    /**
     * 模拟用户下单
     * URL: POST http://localhost:8080/api/order/create?userId=1001&productId=apple_phone
     */
    @PostMapping("/create")
    public String createOrder(@RequestParam String userId) {
        
        // 模拟金额
        int amount = 999;
        
        try {
            SendResult sendResult = orderService.createOrder("测试订单数据。。。");
            return "下单请求已提交 (半消息发送状态: " + sendResult.getSendStatus() + ")";
        } catch (Exception e) {
            return "下单失败: " + e.getMessage();
        }
    }
}