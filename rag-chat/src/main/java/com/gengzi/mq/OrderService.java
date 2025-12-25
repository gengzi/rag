package com.gengzi.mq;

import org.apache.rocketmq.client.producer.SendResult;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Service;
import lombok.extern.slf4j.Slf4j;

import java.util.UUID;

@Slf4j
@Service
public class OrderService {

    @Autowired
    private RocketMQTemplate rocketmqTemplate;

    /**
     * 发送订单创建的事务消息
     * @param orderInfo 订单信息
     */
    public SendResult createOrder(String orderInfo) {
        // 1. 构建消息
        String transactionId = UUID.randomUUID().toString();
        
        Message<String> message = MessageBuilder
                .withPayload(orderInfo)
                .setHeader("rocketmq_TRANSACTION_ID", transactionId) // 设置事务ID，方便回查
                .setHeader("KEYS", transactionId) // 设置 Key，方便在控制台查询
                .build();

        // 2. 这里的参数 orderInfo 对应 Listener 中 executeLocalTransaction 的 arg 参数
        //    topic:tag 格式
        String destination = "order-topic:create";
        
        log.info("准备发送事务消息...");

        try {
            // 3. 发送事务消息
            // 参数说明：destination, message, arg (传递给本地事务方法的参数)
            SendResult result = rocketmqTemplate.sendMessageInTransaction(
                    destination, 
                    message, 
                    orderInfo 
            );

            log.info("半消息发送状态: {}", result.getSendStatus());
            return result;
            
        } catch (Exception e) {
            log.error("发送事务消息失败", e);
            // 这里可以做降级处理，例如记录本地日志或告警
        }
        return null;
    }
}