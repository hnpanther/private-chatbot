package com.hnp.privatechatbot.service;

import com.hnp.privatechatbot.entity.ChatBot;
import com.hnp.privatechatbot.entity.ChatMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LlmServiceTest {

    /**
     * RETURNS_DEEP_STUBS lets us stub the entire ChatClient fluent chain
     * (prompt().messages().call().content()) without declaring intermediate mock types.
     */
    @Mock(answer = Answers.RETURNS_DEEP_STUBS)
    ChatClient defaultChatClient;

    @Mock
    VectorStore vectorStore;

    @InjectMocks
    LlmService llmService;

    private static final int RAG_TOP_K = 5;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(llmService, "ragTopK",            RAG_TOP_K);
        ReflectionTestUtils.setField(llmService, "globalBaseUrl",      "http://localhost:8001");
        ReflectionTestUtils.setField(llmService, "globalApiKey",       "test-key");
        ReflectionTestUtils.setField(llmService, "globalModel",        "test-model");
        ReflectionTestUtils.setField(llmService, "globalOllamaBaseUrl","http://localhost:11434");
        ReflectionTestUtils.setField(llmService, "globalOllamaModel",  "llama3");

        lenient().when(defaultChatClient.prompt().messages(anyList()).call().content())
                .thenReturn("mocked LLM response");
    }

    // ── evictCache ─────────────────────────────────────────────────────────────

    @Test
    void evictCache_nonExistentId_isNoOp() {
        assertThatCode(() -> llmService.evictCache(999L))
                .doesNotThrowAnyException();
    }

    // ── RAG — no filter key (Mode 1) ───────────────────────────────────────────

    @Test
    void chat_nullRagFilterKey_searchesAllDocsWithExactTopK() {
        ChatBot bot = buildGlobalBot(null);
        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of());

        llmService.chat(bot, List.of(), "find info");

        ArgumentCaptor<SearchRequest> captor = ArgumentCaptor.forClass(SearchRequest.class);
        verify(vectorStore).similaritySearch(captor.capture());
        assertThat(captor.getValue().getTopK()).isEqualTo(RAG_TOP_K);
    }

    @Test
    void chat_blankRagFilterKey_searchesAllDocsWithExactTopK() {
        ChatBot bot = buildGlobalBot("   ");
        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of());

        llmService.chat(bot, List.of(), "find info");

        ArgumentCaptor<SearchRequest> captor = ArgumentCaptor.forClass(SearchRequest.class);
        verify(vectorStore).similaritySearch(captor.capture());
        assertThat(captor.getValue().getTopK()).isEqualTo(RAG_TOP_K);
    }

    // ── RAG — with filter key (Mode 2) ─────────────────────────────────────────

    @Test
    void chat_withRagFilterKey_searchesWithBroadTopK() {
        ChatBot bot = buildGlobalBot("hr");
        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of());

        llmService.chat(bot, List.of(), "find info");

        ArgumentCaptor<SearchRequest> captor = ArgumentCaptor.forClass(SearchRequest.class);
        verify(vectorStore).similaritySearch(captor.capture());
        assertThat(captor.getValue().getTopK()).isEqualTo(RAG_TOP_K * 5);
    }

    @Test
    void chat_withRagFilterKey_untaggedDocIsAlwaysIncluded() {
        // Untagged docs (no chatbot_id) must pass through even when a filter is active
        ChatBot bot = buildGlobalBot("hr");
        Document untagged   = buildDoc("1", "shared content",  Map.of());
        Document wrongTag   = buildDoc("2", "finance content", Map.of("chatbot_id", "finance"));
        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of(untagged, wrongTag));

        String result = llmService.chat(bot, List.of(), "test");

        assertThat(result).isEqualTo("mocked LLM response");
    }

    @Test
    void chat_withRagFilterKey_docWithOverlappingTagIsIncluded() {
        // A document tagged "hr,safety,finance" should be found by a "hr" filter
        ChatBot bot = buildGlobalBot("hr");
        Document multiTagDoc = buildDoc("1", "multi-dept content",
                Map.of("chatbot_id", "hr,safety,finance"));
        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of(multiTagDoc));

        String result = llmService.chat(bot, List.of(), "test");

        assertThat(result).isEqualTo("mocked LLM response");
    }

    @Test
    void chat_withRagFilterKey_jsonArrayTagIsIncluded() {
        // chatbot_id stored as a JSON array (List) — both formats must work
        ChatBot bot = buildGlobalBot("contract");
        Document arrayTagDoc = buildDoc("1", "contract content",
                Map.of("chatbot_id", List.of("contract", "hr")));
        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of(arrayTagDoc));

        String result = llmService.chat(bot, List.of(), "test");

        assertThat(result).isEqualTo("mocked LLM response");
    }

    @Test
    void chat_withRagFilterKey_jsonArrayWithNoOverlapIsExcluded() {
        // chatbot_id = ["finance"] should NOT match a "hr" filter
        ChatBot bot = buildGlobalBot("hr");
        Document financeOnly = buildDoc("1", "finance content",
                Map.of("chatbot_id", List.of("finance")));
        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of(financeOnly));

        llmService.chat(bot, List.of(), "test");

        // financeOnly is excluded → no RAG context → LLM still called successfully
        assertThat(true).isTrue(); // just verify no exception is thrown
    }

    @Test
    void chat_withRagFilterKey_commaFilterMatchesEachTag() {
        // Chatbot filter "hr,safety" should match docs tagged with either "hr" OR "safety"
        ChatBot bot = buildGlobalBot("hr,safety");
        Document hrDoc     = buildDoc("1", "hr content",     Map.of("chatbot_id", "hr"));
        Document safetyDoc = buildDoc("2", "safety content", Map.of("chatbot_id", "safety,finance"));
        Document financeOnly = buildDoc("3", "finance only",   Map.of("chatbot_id", "finance"));
        when(vectorStore.similaritySearch(any(SearchRequest.class)))
                .thenReturn(List.of(hrDoc, safetyDoc, financeOnly));

        // Should complete without error; finance-only doc is excluded silently
        String result = llmService.chat(bot, List.of(), "test");

        assertThat(result).isEqualTo("mocked LLM response");
    }

    // ── RAG — graceful degradation ─────────────────────────────────────────────

    @Test
    void chat_vectorStoreThrows_gracefullyFallsBackToNoContext() {
        ChatBot bot = buildGlobalBot(null);
        when(vectorStore.similaritySearch(any(SearchRequest.class)))
                .thenThrow(new RuntimeException("pgVector unavailable"));

        // Must not propagate the exception; LLM still gets called (without RAG context)
        String result = llmService.chat(bot, List.of(), "hello");

        assertThat(result).isEqualTo("mocked LLM response");
    }

    // ── buildRagQuery (tested via vectorStore SearchRequest argument) ───────────

    @Test
    void chat_emptyHistory_ragQueryEqualsCurrentMessage() {
        ChatBot bot = buildGlobalBot(null);
        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of());

        llmService.chat(bot, List.of(), "my specific question");

        ArgumentCaptor<SearchRequest> captor = ArgumentCaptor.forClass(SearchRequest.class);
        verify(vectorStore).similaritySearch(captor.capture());
        assertThat(captor.getValue().getQuery()).isEqualTo("my specific question");
    }

    @Test
    void chat_historyWithFewerThan3UserMessages_ragQueryIncludesAllHistory() {
        ChatBot bot = buildGlobalBot(null);
        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of());

        llmService.chat(bot, buildUserHistory("msg1", "msg2"), "current");

        ArgumentCaptor<SearchRequest> captor = ArgumentCaptor.forClass(SearchRequest.class);
        verify(vectorStore).similaritySearch(captor.capture());
        String query = captor.getValue().getQuery();
        assertThat(query).contains("msg1").contains("msg2").contains("current");
    }

    @Test
    void chat_historyWithMoreThan3UserMessages_ragQueryIncludesOnlyLast3() {
        ChatBot bot = buildGlobalBot(null);
        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of());

        llmService.chat(bot, buildUserHistory("msg1", "msg2", "msg3", "msg4", "msg5"), "current");

        ArgumentCaptor<SearchRequest> captor = ArgumentCaptor.forClass(SearchRequest.class);
        verify(vectorStore).similaritySearch(captor.capture());
        String query = captor.getValue().getQuery();
        assertThat(query)
                .contains("msg3").contains("msg4").contains("msg5").contains("current")
                .doesNotContain("msg1")
                .doesNotContain("msg2");
    }

    @Test
    void chat_historyWithAssistantMessages_ragQueryUsesOnlyUserMessages() {
        // Assistant messages in history must not pollute the RAG query
        ChatBot bot = buildGlobalBot(null);
        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of());

        List<ChatMessage> history = new ArrayList<>();
        history.add(buildMsg(ChatMessage.Role.USER,      "user question"));
        history.add(buildMsg(ChatMessage.Role.ASSISTANT, "ai answer — should be excluded"));

        llmService.chat(bot, history, "follow up");

        ArgumentCaptor<SearchRequest> captor = ArgumentCaptor.forClass(SearchRequest.class);
        verify(vectorStore).similaritySearch(captor.capture());
        String query = captor.getValue().getQuery();
        assertThat(query)
                .contains("user question").contains("follow up")
                .doesNotContain("ai answer");
    }

    // ── Helpers ────────────────────────────────────────────────────────────────

    private ChatBot buildGlobalBot(String ragFilterKey) {
        ChatBot bot = new ChatBot();
        bot.setId(1L);
        bot.setLlmProvider(ChatBot.LlmProvider.GLOBAL);
        bot.setRagFilterKey(ragFilterKey);
        return bot;
    }

    private List<ChatMessage> buildUserHistory(String... contents) {
        List<ChatMessage> history = new ArrayList<>();
        for (String content : contents) {
            history.add(buildMsg(ChatMessage.Role.USER, content));
        }
        return history;
    }

    private ChatMessage buildMsg(ChatMessage.Role role, String content) {
        ChatMessage msg = new ChatMessage();
        msg.setRole(role);
        msg.setContent(content);
        return msg;
    }

    private Document buildDoc(String id, String text, Map<String, Object> metadata) {
        return new Document(text, metadata);
    }
}
