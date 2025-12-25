package com.gengzi.mq;

import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.common.message.MessageExt;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

/**
 * 订单消费者
 * * 1. topic: 监听的主题
 * 2. consumerGroup: 消费者组 (必须全局唯一)
 * 3. selectorExpression: 过滤 Tag，只消费 tag 为 'create' 的消息
 */
@Slf4j
@Component
@RocketMQMessageListener(
    topic = "order-topic", 
    consumerGroup = "consumer-group-order-processing",
    selectorExpression = "create" 
)
public class OrderConsumer implements RocketMQListener<MessageExt> {

    @Override
    public void onMessage(MessageExt message) {
        // 1. 解析消息
        String msgId = message.getMsgId();
        String transId = message.getTransactionId();
        String keys = message.getKeys(); // 我们之前把 keys 设置为了业务上的 transactionId
        String body = new String(message.getBody(), StandardCharsets.UTF_8);

        log.info("收到订单创建消息 | MsgID: {} | Key: {} | Body: {}", msgId, keys, body);

        try {
            // --- 2. 幂等性检查 (核心) ---
            // 工业级建议：在执行业务前，先用 keys (业务ID) 去查“已处理消息表”或“业务状态”
            // if (orderService.isProcessed(keys)) {
            //     log.info("该订单已处理，跳过。Key: {}", keys);
            //     return;
            // }

            // --- 3. 执行下游业务 ---
            // 例如：增加用户积分、创建物流单
            processDownstreamBusiness(body);

            log.info("订单后续业务处理成功。Key: {}", keys);

        } catch (Exception e) {
            log.error("消费失败，将触发重试。Key: {}", keys, e);
            // 抛出异常后，RocketMQ 会自动进行重试（默认 16 次，时间间隔递增）
            throw new RuntimeException("消费处理失败", e);
        }
    }

    private void processDownstreamBusiness(String body) {
        // 模拟业务耗时
        try { Thread.sleep(100); } catch (InterruptedException e) {}
        System.out.println(">>> [积分系统] 已为订单 [" + body + "] 增加积分 +100");
    }
}