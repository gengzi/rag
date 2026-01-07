---
name: java-springboot-coding-standards
description: 为 Java/Spring Boot 项目提供全面编码规范与规范化流程。使用场景：当用户要求“规范化/统一编码风格/代码审查清单/脚手架规范/将本工程规范化/提供 Spring Boot 编码规范/整理工程结构与分层/制定异常与日志规范/测试规范”等时使用。
---

# Java Spring Boot 编码规范

## 快速流程
1. 明确目标：新项目或存量工程，模块范围（API/批处理/任务/集成），技术栈版本（Spring Boot、JDK、持久化框架）。
2. 读取工程结构与关键配置（build.gradle/pom.xml、application.yml、主包结构）。
3. 基于规范输出：规范清单、当前差距、优先级整改建议。
4. 若用户要求改造，按模块最小改动落地，并说明影响范围与回归点。

## 何时加载参考
- 需要具体规则或示例时读取 `references/standards.md`。

## 输出偏好
- 使用清单化输出：规范项、理由、实施要点、检查方式。
- 给出整改建议时，标注优先级（P0/P1/P2）与风险。
