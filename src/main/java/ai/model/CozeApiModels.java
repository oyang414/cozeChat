package ai.model;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**
 * @Author ouyangxingjie
 * @Description
 * @Date 16:25 2026/1/21
 */

/**
 * Coze V3 协议 DTO 聚合类
 */
public class CozeApiModels {

    public record ChatRequest(
            @JsonProperty("bot_id") String botId,
            @JsonProperty("user_id") String userId,
            @JsonProperty("additional_messages") List<Message> messages,
            boolean stream
    ) {}

    public record Message(String role, String content, @JsonProperty("content_type") String contentType) {}

    // 修改点 1：重命名为 CozeResponseDTO，避免与 Spring AI 的 ChatResponse 冲突
    public record CozeResponseDTO(int code, String msg, ChatData data) {}

    public record ChatData(
            String id,
            @JsonProperty("conversation_id") String conversationId,
            String status
    ) {}
}
