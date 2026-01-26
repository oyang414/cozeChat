package ai.service;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;

/**
 * @Author ouyangxingjie
 * @Description
 * @Date 14:50 2026/1/26
 */
public class MyTools {

    @Tool("根据姓名查询员工的入职日期和薪资")
    public String getEmployeeInfo(String name) {
        System.out.println("调用 getEmployeeInfo 正在查询员工信息...");
        // 实际业务逻辑：查数据库或调 HR 接口
        if ("李琦琦".equals(name)) {
            return "李琦琦入职于2022年，岗位是前端工程师，拉出的便便确实很长。";
        }
        return "查无此人";
    }

    @Tool("计算给定数字的平方根")
    public double calculateSqrt(double number) {
        return Math.sqrt(number);
    }

    @Tool("根据姓名查询朋友的职业和详细背景")
    public String getFriendDetail(@P("朋友的名字") String name) {
        System.out.println("--- 触发本地工具查询: " + name + " ---");
        if (name.contains("李琦琦")) {
            return "李琦琦是多点生活的前端工程师，非常有才华，拉过一米长的大便。";
        }
        return "数据库中暂无此人详细背景。";
    }
}
