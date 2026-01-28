package com.gengzi.tool;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;

import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;

/**
 * 计算器工具 - 执行数学计算和表达式求值
 */
@Component
public class CalculatorTool {

    private final ScriptEngine engine;

    public CalculatorTool() {
        this.engine = new ScriptEngineManager().getEngineByName("JavaScript");
    }

    @Tool(description = "执行数学计算。支持基本运算（+、-、*、/）、括号、幂运算（Math.pow）等。示例：'2 + 3 * 4'、'Math.pow(2, 8)'、'(10 + 5) / 3'")
    public String calculate(String expression) {
        try {
            Object result = engine.eval(expression);
            return String.valueOf(result);
        } catch (Exception e) {
            return "计算错误: " + e.getMessage() + "。请确保表达式格式正确。";
        }
    }

    @Tool(description = "计算百分比。例如：计算25是200的百分之几")
    public String calculatePercentage(double part, double total) {
        if (total == 0) {
            return "错误：总数不能为零";
        }
        double percentage = (part / total) * 100;
        return String.format("%.2f%%", percentage);
    }

    @Tool(description = "计算平均值")
    public String calculateAverage(double[] numbers) {
        if (numbers == null || numbers.length == 0) {
            return "错误：数组不能为空";
        }
        double sum = 0;
        for (double num : numbers) {
            sum += num;
        }
        double average = sum / numbers.length;
        return String.format("平均值: %.2f", average);
    }
}
