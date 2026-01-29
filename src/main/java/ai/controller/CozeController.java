package ai.controller;

import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.BufferOverflowStrategy;
import reactor.core.publisher.Flux;

import java.time.Duration;
import java.util.Collections;
import java.util.Map;

/**
 * @Author ouyangxingjie
 * @Description
 * @Date 16:20 2026/1/21
 */
@RestController
@RequestMapping("/api/ai")
public class CozeController {

    private final ChatModel cozeChatModel; // 注入你之前实现的 CozeChatModel

    public CozeController(ChatModel cozeChatModel) {
        this.cozeChatModel = cozeChatModel;
    }

    /*@GetMapping("/ask")
    public String ask(@RequestParam(value = "prompt", defaultValue = "你好") String prompt) {
        // 显式调用，确保返回的是回复字符串
        // 如果 cozeChatModel 实现了 ChatModel，默认的 call(String) 会返回 String
        return cozeChatModel.call(prompt);
    }*/
    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE+ ";charset=UTF-8")
    public Flux<ServerSentEvent<Map<String, String>>> streamAsk(@RequestParam String prompt) {
        /*return cozeChatModel.stream(new Prompt(prompt))
                .map(res -> res.getResult().getOutput().getContent())
                // 针对 SSE 的特殊优化：确保每个元素被即时发送，并增加心跳防止连接断开
                //.onBackpressureBuffer()
                //.log("AI-Streaming"); // 可以在控制台实时看到流向
                // 关键：强制刷新缓冲区
                .doOnNext(content -> {
                    // 每产生一个字都打印，如果在控制台是跳跃的，说明后端逻辑通了
                    System.out.print(content);
                });*/
        return cozeChatModel.stream(new Prompt(prompt))
                // 1. 提取内容字符串
                .map(res -> res.getResult().getOutput().getContent())

                // 2. 过滤掉空内容，防止出现空的 data: 行
                .filter(content -> content != null && !content.isEmpty())

                // 3. 【关键】封装为对象，让 Jackson 自动处理 JSON 转义（包括引号和换行符）
                // 这样你就不用自己写 .replace("\"", "\\\"") 了，Jackson 做得更完美
                .map(content -> Collections.singletonMap("content", content))

                // 4. 【核心】封装为标准 SSE 对象
                // Spring 会自动补全 data: 前缀和 \n\n，绝不会出现 data:data:
                .map(data -> ServerSentEvent.<Map<String, String>>builder()
                        .data(data)
                        .build())

                // 5. 背压策略
                .onBackpressureBuffer(
                        1000,
                        dropped -> System.err.println("背压警告：丢弃数据：" + dropped),
                        BufferOverflowStrategy.DROP_OLDEST
                )

                // 6. 控制步调
                .delayElements(Duration.ofMillis(20))

                .doOnNext(sse -> System.out.println("发送数据块: " + sse.data()))
                .log("CozeStream");
    }
}