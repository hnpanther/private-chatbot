package com.hnp.privatechatbot;

import com.hnp.privatechatbot.service.LlmService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * Smoke test — verifies the Spring application context loads correctly.
 * LlmService is mocked to avoid requiring pgVector and an active LLM endpoint.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class PrivateChatbotApplicationTests {

    @MockitoBean
    LlmService llmService;

    @Test
    void contextLoads() {
        // If the context starts without exceptions, the test passes.
    }
}
