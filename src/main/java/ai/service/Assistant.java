package ai.service;

import dev.langchain4j.service.SystemMessage;

/**
 * @Author ouyangxingjie
 * @Description
 * @Date 17:01 2026/1/22
 */
public interface Assistant {
    @SystemMessage("""
            你是一个朋友圈百晓生。我会给你一些关于我朋友的零碎片段。
            如果检索到的信息里有答案，请详细回答；
            如果信息冲突，请以最相关的那条为准；
            如果确实没有提到，请委婉地说你不清楚。
            """)
    String chat(String userMessage);
}
