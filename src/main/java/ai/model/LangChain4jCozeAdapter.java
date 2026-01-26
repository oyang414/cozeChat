package ai.model;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;

import java.util.List;

/**
 * @Author ouyangxingjie
 * @Description
 * @Date 16:47 2026/1/22
 */
public class LangChain4jCozeAdapter implements ChatLanguageModel {

    private final ai.model.CozeChatModel myCozeModel; // 你之前写的那个类

    public LangChain4jCozeAdapter(ai.model.CozeChatModel myCozeModel) {
        this.myCozeModel = myCozeModel;
    }

    @Override
    public Response<AiMessage> generate(List<ChatMessage> messages) {
        // 1. 将 LangChain4j 的 messages 转为你 CozeModel 需要的字符串
        // 简单处理：取最后一条用户消息
        String lastUserMessage = messages.stream()
                .filter(m -> m instanceof dev.langchain4j.data.message.UserMessage)
                .map(m -> ((dev.langchain4j.data.message.UserMessage) m).text())
                .reduce((first, second) -> second)
                .orElse("");

        // 2. 调用你已有的同步/轮询方法
        String cozeResponse = myCozeModel.call(lastUserMessage);


        // 3. 返回 LangChain4j 要求的格式
        return Response.from(AiMessage.from(cozeResponse));
    }
}
