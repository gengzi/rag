package com.gengzi.config;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

public class CustomThreadPool {

    // 推荐：使用静态工厂方法创建线程池（避免直接使用 Executors）
    public static ThreadPoolExecutor createRecommendedThreadPool(
            String poolName,
            int corePoolSize,
            int maximumPoolSize,
            long keepAliveSeconds,
            int queueCapacity) {

        // 自定义线程工厂：带名称前缀，便于日志和监控
        ThreadFactory namedThreadFactory = new ThreadFactory() {
            private final AtomicInteger threadNumber = new AtomicInteger(1);

            @Override
            public Thread newThread(Runnable r) {
                Thread t = new Thread(r, poolName + "-thread-" + threadNumber.getAndIncrement());
                t.setDaemon(false); // 非守护线程，确保 JVM 退出前任务完成（根据需求调整）
                t.setUncaughtExceptionHandler((t1, e) -> {
                    System.err.println("线程 [" + t1.getName() + "] 发生未捕获异常: " + e.getMessage());
                    e.printStackTrace();
                });
                return t;
            }
        };

        // 有界队列：防止内存溢出（比无界 LinkedBlockingQueue 更安全）
        BlockingQueue<Runnable> workQueue = new ArrayBlockingQueue<>(queueCapacity);

        // 自定义拒绝策略：记录日志 + 可选 fallback
        RejectedExecutionHandler rejectionHandler = new RejectedExecutionHandler() {
            @Override
            public void rejectedExecution(Runnable r, ThreadPoolExecutor executor) {
                // 方案1：记录警告（推荐）
                System.err.println("[" + poolName + "] 任务被拒绝！当前活跃线程数: " + executor.getActiveCount() +
                        ", 队列大小: " + executor.getQueue().size() +
                        ", 任务: " + r.toString());

                // 方案2（可选）：由调用者线程执行（慎用，可能阻塞主线程）
                // r.run();

                // 方案3（可选）：抛出自定义异常
                // throw new RejectedTaskException("线程池已满，任务被拒绝");
            }
        };

        return new ThreadPoolExecutor(
                corePoolSize,
                maximumPoolSize,
                keepAliveSeconds,
                TimeUnit.SECONDS,
                workQueue,
                namedThreadFactory,
                rejectionHandler
        );
    }

    // 快速创建一个适用于 I/O 密集型任务的线程池（示例）
    public static ThreadPoolExecutor createIoIntensivePool(String poolName) {
        int cpuCores = Runtime.getRuntime().availableProcessors();
        // I/O 密集型：线程数 ≈ CPU 核数 * (1 + 平均等待时间/平均工作时间)
        // 保守估计：设为 2 * CPU 核数
        int corePoolSize = Math.max(4, cpuCores * 2);
        int maxPoolSize = corePoolSize * 2;
        return createRecommendedThreadPool(poolName, corePoolSize, maxPoolSize, 60L, 1024);
    }

    // 快速创建一个适用于 CPU 密集型任务的线程池（示例）
    public static ThreadPoolExecutor createCpuIntensivePool(String poolName) {
        int cpuCores = Runtime.getRuntime().availableProcessors();
        // CPU 密集型：线程数 ≈ CPU 核数 + 1（避免上下文切换开销）
        int corePoolSize = cpuCores;
        int maxPoolSize = cpuCores + 1;
        return createRecommendedThreadPool(poolName, corePoolSize, maxPoolSize, 30L, 256);
    }

    // 使用示例
    public static void main(String[] args) throws InterruptedException {
        ThreadPoolExecutor ioPool = createIoIntensivePool("MyIoPool");

        for (int i = 0; i < 10; i++) {
            final int taskId = i;
            ioPool.submit(() -> {
                System.out.println(Thread.currentThread().getName() + " 正在处理任务 " + taskId);
                try {
                    Thread.sleep(1000); // 模拟 I/O 操作
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });
        }

        // 优雅关闭
        ioPool.shutdown();
        if (!ioPool.awaitTermination(60, TimeUnit.SECONDS)) {
            ioPool.shutdownNow();
        }
    }
}