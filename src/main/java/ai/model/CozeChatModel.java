package ai.model;


import ai.model.CozeApiModels.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.model.Generation;
import org.springframework.http.MediaType;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.client.RestClient;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.scheduler.Schedulers;
import reactor.netty.http.client.HttpClient;

import java.util.List;
import java.util.Objects;

public class CozeChatModel implements ChatModel {

    private final RestClient restClient;
    private final WebClient webClient;
    private final String botId;
    private final String apiKey;

    public CozeChatModel(String apiKey, String botId) {
        this.botId = botId;
        this.apiKey = apiKey;
        this.restClient = RestClient.builder()
                .baseUrl("https://api.coze.cn/v3")
                .defaultHeader("Authorization", "Bearer " + apiKey)
                .build();
        // 配置底层 HttpClient 禁用缓存并设置长连接
        HttpClient httpClient = HttpClient.create()
                .compress(true) // 开启压缩提高传输效率
                .keepAlive(true);
        this.webClient = WebClient.builder()
                .baseUrl("https://api.coze.cn/v3")
                .defaultHeader("Authorization", "Bearer " + apiKey)
                .defaultHeader("Content-Type", "application/json")
                // 使用 Reactor 驱动确保非阻塞
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .build();
    }

    @Override
    public ChatResponse call(Prompt prompt) {
        // 1. 发送对话请求
        var req = new ChatRequest(botId, "user_123",
                List.of(new Message("user", prompt.getContents(), "text")), false);

        // 修改点 2：显式使用 CozeResponseDTO
        CozeResponseDTO resDTO = restClient.post()
                .uri("/chat")
                .body(req)
                .retrieve()
                .body(CozeResponseDTO.class);

        if (resDTO == null || resDTO.data() == null) {
            throw new RuntimeException("调用 Coze API 失败: " + (resDTO != null ? resDTO.msg() : "未知错误"));
        }

        // 2. 轮询获取结果 (此处假设 pollAndFetch 返回 String)
        String finalContent = pollAndFetch(resDTO.data().id(), resDTO.data().conversationId());

        // 修改点 3：使用 AssistantMessage 构造 Generation
        // Spring AI 1.0.0-M5 规范：Generation(AssistantMessage message)
        AssistantMessage assistantMessage = new AssistantMessage(finalContent);
        Generation generation = new Generation(assistantMessage);

        return new ChatResponse(List.of(generation));
    }

    private String pollAndFetch(String chatId, String convId) {
        boolean completed = false;
        int maxRetries = 30; // 防止无限死循环

        while (!completed && maxRetries-- > 0) {
            // 1. 查看聊天状态
            CozeResponseDTO statusRes = restClient.get()
                    .uri(uri -> uri.path("/chat/retrieve")
                            .queryParam("chat_id", chatId)
                            .queryParam("conversation_id", convId).build())
                    .retrieve()
                    .body(CozeResponseDTO.class);

            if (statusRes != null && "completed".equals(statusRes.data().status())) {
                completed = true;
            } else {
                try { Thread.sleep(1000); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            }
        }

        // 2. 状态完成后，获取消息列表中的最终回答
        var messages = restClient.get()
                .uri(uri -> uri.path("/chat/message/list")
                        .queryParam("chat_id", chatId)
                        .queryParam("conversation_id", convId).build())
                .retrieve()
                .body(JsonNode.class);

        // 解析 JSON：找到 role 为 assistant 且 type 为 answer 的内容
        if (messages != null && messages.has("data")) {
            for (JsonNode node : messages.get("data")) {
                if ("assistant".equals(node.get("role").asText()) && "answer".equals(node.get("type").asText())) {
                    return node.get("content").asText();
                }
            }
        }
        return "未能解析到 Bot 的回复内容";
    }
    @Override
    public Flux<ChatResponse> stream(Prompt prompt) {
        // 1. 构造请求体，注意必须设置 "stream": true
        var req = new ChatRequest(
                botId,
                "user_123",
                List.of(new Message("user", prompt.getContents(), "text")),
                true // 必须开启流式模式
        );

        // 2. 使用 WebClient 处理流式响应 (RestClient 暂不支持长连接流)
        WebClient webClient = WebClient.builder()
                .baseUrl("https://api.coze.cn/v3")
                .defaultHeader("Authorization", "Bearer " + apiKey)
                .build();

        return webClient.post()
                .uri("/chat")
                .bodyValue(req)
                .accept(MediaType.TEXT_EVENT_STREAM)
                .retrieve()
                .bodyToFlux(String.class)
                // 关键：强制即时下发，不等待后续数据
                .publishOn(Schedulers.boundedElastic())
                .flatMap(rawLine -> {
                    // 1. 预检查：如果是空行或结束符，直接返回空流（相当于丢弃）
                    if (rawLine == null || rawLine.isBlank() || rawLine.contains("[DONE]")) {
                        return Flux.empty();
                    }

                    // 2. 解析内容
                    String content = extractContent(rawLine);

                    // 3. 只有真正有内容时才向下游发送数据
                    if (content != null && !content.isEmpty()) {
                        ChatResponse resp = new ChatResponse(List.of(new Generation(new AssistantMessage(content))));
                        return Flux.just(resp);
                    }

                    return Flux.empty(); // 关键：返回空流而不是 null
                });
    }

    /**
     * 解析 Coze 的流式事件
     * Coze 流式返回格式通常为: event: conversation.chat.created ...
     */
    private ChatResponse parseCozeEvent(String rawLine) {
        // 简易逻辑：Coze 流式中增量内容通常在 conversation.chat.delta 事件中
        // 实际生产建议使用正则表达式或 JSON 解析抓取 "content" 字段
        if (rawLine.contains("\"type\":\"answer\"") && rawLine.contains("\"content\":\"")) {
            // 提取 content 字段并封装回 Spring AI 的 ChatResponse
            String content = extractContent(rawLine);
            return new ChatResponse(List.of(new Generation(new AssistantMessage(content))));
        }
        return null;
    }
    private String extractContent(String rawLine) {
        if (rawLine == null || rawLine.isBlank()) return "";

        // 1. 去掉 SSE 前缀
        String content = rawLine.trim();
        if (content.startsWith("data:")) {
            content = content.substring(5).trim();
        }

        // 2. 过滤掉结束标记
        if (content.equals("[DONE]")) return "";

        // 3. 尝试作为 JSON 解析 (处理 {"content":"xxx"} 格式)
        if (content.startsWith("{")) {
            try {
                ObjectMapper mapper = new ObjectMapper();
                JsonNode node = mapper.readTree(content);

                // 情况 A: 消息增量包
                if (node.has("content")) {
                    return node.get("content").asText();
                }
                // 情况 B: 各种状态包或结束包 (直接忽略)
                return "";
            } catch (Exception e) {
                // 解析失败说明可能不是合法的完整 JSON，降级处理
                return "";
            }
        }

        // 4. 处理纯文本行 (你 Postman 里看到的那种 data:### 总结)
        // 如果走到这一步，content 已经是去掉 data: 后的原始文字了
        // 过滤掉可能是建议问题的行 (通常建议问题会由专门的事件触发，这里可以根据业务决定是否保留)
        return content;
    }

}
