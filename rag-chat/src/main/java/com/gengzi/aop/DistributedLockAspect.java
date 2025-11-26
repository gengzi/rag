package com.gengzi.aop;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.core.annotation.Order;
import org.springframework.expression.EvaluationContext;
import org.springframework.expression.Expression;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.ParserContext;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;

@Aspect
@Component
@Order(1) // 确保在事务等切面之前执行
public class DistributedLockAspect {

    private final RedissonClient redissonClient;
    private final ExpressionParser parser = new SpelExpressionParser();

    public DistributedLockAspect(RedissonClient redissonClient) {
        this.redissonClient = redissonClient;
    }

    @Around("@annotation(distributedLock)")
    public Object around(ProceedingJoinPoint joinPoint, DistributedLock distributedLock) throws Throwable {
        String lockKey = parseLockKey(distributedLock.key(), joinPoint);

        RLock lock = redissonClient.getLock(lockKey);
        boolean lockAcquired = false;

        try {
            // 尝试获取锁
            lockAcquired = lock.tryLock(
                distributedLock.waitTime(),
                distributedLock.leaseTime(),
                distributedLock.timeUnit()
            );

            if (!lockAcquired) {
                throw new RuntimeException(distributedLock.errorMsg());
            }

            // 执行目标方法
            return joinPoint.proceed();

        } finally {
            // 只有成功获取了锁，才释放
            if (lockAcquired && lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    /**
     * 解析 SpEL 表达式，生成最终的 lock key
     */
    private String parseLockKey(String keyExpression, ProceedingJoinPoint joinPoint) {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        Object[] args = joinPoint.getArgs();
        String[] parameterNames = getParameterNames(signature);

        EvaluationContext context = new StandardEvaluationContext();
        for (int i = 0; i < args.length; i++) {
            if (parameterNames != null && i < parameterNames.length) {
                context.setVariable(parameterNames[i], args[i]);
            }
        }

        // ✅ 关键：使用 TemplateParserContext 支持 #{...} 模板
        Expression expression = parser.parseExpression(keyExpression, ParserContext.TEMPLATE_EXPRESSION);
        return expression.getValue(context, String.class);
    }

    /**
     * 获取方法参数名（需编译时保留参数名：-parameters 或使用 @Param）
     */
    private String[] getParameterNames(MethodSignature signature) {
        return signature.getParameterNames(); // Spring 4.3+ 支持，需 JDK8+ 编译参数
    }
}