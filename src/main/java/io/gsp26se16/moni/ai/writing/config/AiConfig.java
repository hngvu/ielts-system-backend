package io.gsp26se16.moni.ai.writing.config;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

@Configuration
public class AiConfig {

    /**
     * Mark OpenAI (Nvidia) as primary ChatModel for text generation.
     * Gemini is configured separately for vision tasks only.
     */
    @Bean
    @Primary
    public ChatModel primaryChatModel(@Qualifier("openAiChatModel") ChatModel openAiChatModel) {
        return openAiChatModel;
    }

    @Bean(name = "aiExecutor")
    public Executor aiExecutor() {
        return new ThreadPoolExecutor(
                4, // core pool size
                8, // max pool size
                60,
                TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(50),
                new ThreadFactory() {
                    private final AtomicInteger count = new AtomicInteger(1);

                    @Override
                    public Thread newThread(Runnable r) {
                        return new Thread(r, "ai-exec-" + count.getAndIncrement());
                    }
                },
                new ThreadPoolExecutor.AbortPolicy() // reject khi full queue
                );
    }
}
