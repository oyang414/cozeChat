package ai;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * @Author ouyangxingjie
 * @Description
 * @Date 17:24 2026/1/21
 */

@SpringBootApplication
public class CozeAiApplication {
    public static void main(String[] args) {
        SpringApplication.run(CozeAiApplication.class, args);
        System.out.println("🚀 Coze AI 助手已启动！访问 http://localhost:8080/chat?message=你好 开始测试");
    }
}