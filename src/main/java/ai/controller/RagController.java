package ai.controller;

import ai.config.CozeConfig;
import ai.service.Assistant;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * @Author ouyangxingjie
 * @Description
 * @Date 16:44 2026/1/22
 */
@RestController
@RequestMapping("/api/rag")
public class RagController {

    private final Assistant assistant;

    public RagController(Assistant assistant) {
        this.assistant = assistant;
    }

    @GetMapping("/ask")
    public String ask(@RequestParam String prompt) {
        // 此时调用，LangChain4j 会偷偷执行以下步骤：
        // 1. 去本地向量库查相关资料
        // 2. 把资料拼进 prompt 变成："参考资料：... 请回答：prompt"
        // 3. 把增强后的 prompt 发给你的 CozeChatModel
        return assistant.chat(prompt);
    }
}
