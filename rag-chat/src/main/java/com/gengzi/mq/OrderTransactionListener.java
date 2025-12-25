package com.gengzi.mq;

import org.apache.rocketmq.spring.annotation.RocketMQTransactionListener;
import org.apache.rocketmq.spring.core.RocketMQLocalTransactionListener;
import org.apache.rocketmq.spring.core.RocketMQLocalTransactionState;
import org.springframework.messaging.Message;
import org.springframework.stereotype.Component;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.ConcurrentHashMap;

/**
 * 事务消息监听器
 * * @RocketMQTransactionListener 注解说明：
 * rocketMQTemplateBeanName: 指定使用哪个 Template Bean（若有多个）
 */
@Slf4j
@Component
@RocketMQTransactionListener
public class OrderTransactionListener implements RocketMQLocalTransactionListener {

    // 模拟数据库事务状态存储 (实际场景请查数据库)
    private final ConcurrentHashMap<String, Boolean> localTransResult = new ConcurrentHashMap<>();

    /**
     * 步骤 A: 执行本地事务
     * 当发送半消息成功后，此方法会被调用。
     * * @param msg 发送的消息体
     * @param arg 发送时传递的额外业务参数 (如 orderId)
     * @return 事务状态 (COMMIT, ROLLBACK, UNKNOWN)
     */
    @Override
    public RocketMQLocalTransactionState executeLocalTransaction(Message msg, Object arg) {
        String transId = (String) msg.getHeaders().get("rocketmq_TRANSACTION_ID");
        String orderId = (String) arg;
        
        log.info("开始执行本地事务。TransID: {}, OrderID: {}", transId, orderId);

        try {
            // --- 模拟：执行具体的数据库业务逻辑 ---
            // boolean result = orderService.saveOrder(order);
            // ------------------------------------
            
            boolean isSuccess = true; // 假设数据库操作成功
            
            // 模拟偶尔发生状态未知的场景（用于测试回查）
            if ("invalid_order".equals(orderId)) {
                isSuccess = false;
                throw new RuntimeException("模拟本地事务失败");
            }

            // 记录事务结果，供回查使用 (实际业务通常是查数据库是否存在该订单)
            localTransResult.put(transId, isSuccess);

            if (isSuccess) {
                log.info("本地事务成功，提交消息");
                return RocketMQLocalTransactionState.COMMIT;
            } else {
                log.info("本地事务失败，回滚消息");
                return RocketMQLocalTransactionState.ROLLBACK;
            }

        } catch (Exception e) {
            log.error("本地事务执行异常", e);
            // 如果发生异常，通常回滚
            return RocketMQLocalTransactionState.ROLLBACK;
            // 注意：如果这里返回 UNKNOWN，Broker 会稍后进行回查
        }
    }

    /**
     * 步骤 B: 检查本地事务状态 (回查)
     * 当步骤 A 返回 UNKNOWN，或者未返回结果(网络断开/宕机)时，Broker 会调用此方法。
     * * @param msg 之前发送的半消息
     * @return 最终事务状态
     */
    @Override
    public RocketMQLocalTransactionState checkLocalTransaction(Message msg) {
        String transId = (String) msg.getHeaders().get("rocketmq_TRANSACTION_ID");
        log.info("执行事务回查。TransID: {}", transId);

        // --- 实际业务逻辑：查询数据库表，确认该 ID 的业务是否成功 ---
        // Order order = orderMapper.selectById(msg.getPayload().getOrderId());
        // -----------------------------------------------------

        Boolean isSuccess = localTransResult.get(transId);
        
        if (isSuccess != null && isSuccess) {
            log.info("回查成功，提交消息");
            return RocketMQLocalTransactionState.COMMIT;
        } else {
            log.warn("回查失败或找不到记录，回滚消息");
            return RocketMQLocalTransactionState.ROLLBACK;
        }
    }
}