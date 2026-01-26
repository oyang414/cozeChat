package ai.controller;

import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

import java.time.Duration;

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
    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> streamAsk(@RequestParam String prompt) {
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

                // 2. 核心优化：平滑处理
                // 如果 AI 瞬间吐出了一大段话，我们不直接发给前端
                // 而是把内容拆成单个字符，或者设定最小发送间隔
                .flatMap(content -> {
                    // 将一段话拆分成单个字符的 Flux
                    String[] chars = content.split("");
                    return Flux.fromArray(chars);
                })

                // 3. 控制频率：每 50 毫秒发射一个字符（真正的打字机节奏）
                .delayElements(Duration.ofMillis(50))

                // 4. 背压策略：如果 AI 产生太快，前端还没接收完
                // 我们把多出来的字符暂时存在内存缓冲区（最多存 1000 个）
                .onBackpressureBuffer(1000,
                        dropped -> System.err.println("警告：缓冲区溢出，丢弃数据：" + dropped))

                // 5. 调试日志
                .doOnNext(c -> System.out.print(c))
                .log("SmoothStream");
    }
}