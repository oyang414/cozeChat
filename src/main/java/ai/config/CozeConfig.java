package ai.config;

import ai.model.CozeChatModel;
import ai.model.LangChain4jCozeAdapter;
import ai.service.Assistant;
import ai.service.MyTools;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.embedding.AllMiniLmL6V2EmbeddingModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import dev.langchain4j.rag.content.retriever.EmbeddingStoreContentRetriever;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.store.embedding.inmemory.InMemoryEmbeddingStore;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * @Author ouyangxingjie
 * @Description
 * @Date 16:31 2026/1/21
 */
@Configuration
public class CozeConfig {
    @Bean
    public CozeChatModel cozeChatModel(
            @Value("${coze.api.key}") String apiKey,
            @Value("${coze.api.bot-id}") String botId) {
        return new CozeChatModel(apiKey, botId);
    }
    @Bean
    public Assistant assistant(CozeChatModel cozeModel) {
        // 1. 将你的 Coze 包装进 LangChain4j
        ChatLanguageModel cozeAdapter = new LangChain4jCozeAdapter(cozeModel);

        // 2. 准备本地知识库 (假设你有一些 PDF 或文本)
        // 这里演示内存库，实际可用 Milvus 或 Elasticsearch
        InMemoryEmbeddingStore<TextSegment> embeddingStore = new InMemoryEmbeddingStore<>();
        EmbeddingModel embeddingModel = new AllMiniLmL6V2EmbeddingModel(); // 本地向量模型

        // 3. 填充知识库（例如把你的公司规章制度放进去）
        //TextSegment segment = TextSegment.from("本项目的高级顾问是 Gemini，采用 LangChain4j 架构。");
        TextSegment segment = TextSegment.from("小徐是安恪的前端工程师");
        embeddingStore.add(embeddingModel.embed(segment).content(), segment);
        TextSegment segment1 = TextSegment.from("王琦是武汉的一位宝妈，她打麻将特别厉害");
        embeddingStore.add(embeddingModel.embed(segment1).content(), segment1);
        TextSegment segment2 = TextSegment.from("卢钿特别喜欢飞天小女警的毛毛还有chicawa中的乌萨奇，只要是这些动漫的周边她都喜欢收集");
        embeddingStore.add(embeddingModel.embed(segment2).content(), segment2);
        TextSegment segment3 = TextSegment.from("刘胜是一名ios开发工程师，武汉老杆子，家以前住民众乐园，现在住常青城");
        embeddingStore.add(embeddingModel.embed(segment3).content(), segment3);
        TextSegment segment4 = TextSegment.from("李琦琦是多点生活的一名前端工程师，曾拉出近一米长的大便");
        embeddingStore.add(embeddingModel.embed(segment4).content(), segment4);
        // 4. 构建检索器
        ContentRetriever retriever = EmbeddingStoreContentRetriever.builder()
                .embeddingStore(embeddingStore)
                .embeddingModel(embeddingModel)
                .maxResults(3) // 增加到 3，容错率更高
                //.minScore(0.5) // 适当调低门槛
                .build();

        // 5. 最终组合：创建一个拥有“本地知识+Coze大脑”的助手
        return AiServices.builder(Assistant.class)
                .chatLanguageModel(cozeAdapter) // 使用 Coze 作为大脑
                .contentRetriever(retriever)    // 使用本地检索增强
                .chatMemory(MessageWindowChatMemory.withMaxMessages(10)) // 让 Coze 也有记忆
                .build();
    }

}
