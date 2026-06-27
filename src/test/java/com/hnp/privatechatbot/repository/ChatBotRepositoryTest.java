package com.hnp.privatechatbot.repository;

import com.hnp.privatechatbot.entity.ChatBot;
import com.hnp.privatechatbot.entity.Department;
import com.hnp.privatechatbot.service.LlmService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Transactional
class ChatBotRepositoryTest {

    @MockitoBean LlmService llmService; // prevents pgVector VectorStore dependency

    @Autowired ChatBotRepository chatBotRepository;
    @PersistenceContext EntityManager em;

    private Department deptA;
    private Department deptB;

    @BeforeEach
    void setUp() {
        deptA = new Department();
        deptA.setName("Engineering");
        em.persist(deptA);

        deptB = new Department();
        deptB.setName("Accounting");
        em.persist(deptB);

        ChatBot activeInA = new ChatBot();
        activeInA.setName("Active Bot A");
        activeInA.setActive(true);
        activeInA.setDepartment(deptA);
        em.persist(activeInA);

        ChatBot inactiveInA = new ChatBot();
        inactiveInA.setName("Inactive Bot A");
        inactiveInA.setActive(false);
        inactiveInA.setDepartment(deptA);
        em.persist(inactiveInA);

        ChatBot activeInB = new ChatBot();
        activeInB.setName("Active Bot B");
        activeInB.setActive(true);
        activeInB.setDepartment(deptB);
        em.persist(activeInB);

        em.flush();
        em.clear();
    }

    @Test
    void findByActiveTrueOrderByDepartmentNameAsc_returnsOnlyActiveBots() {
        List<ChatBot> result = chatBotRepository.findByActiveTrueOrderByDepartmentNameAsc();

        assertThat(result).allMatch(ChatBot::isActive);
        assertThat(result).extracting(ChatBot::getName)
                .doesNotContain("Inactive Bot A");
    }

    @Test
    void findByActiveTrueOrderByDepartmentNameAsc_orderedByDepartmentName() {
        List<ChatBot> result = chatBotRepository.findByActiveTrueOrderByDepartmentNameAsc();

        // "Accounting" < "Engineering" alphabetically
        assertThat(result.get(0).getDepartment().getName()).isEqualTo("Accounting");
        assertThat(result.get(1).getDepartment().getName()).isEqualTo("Engineering");
    }

    @Test
    void findByDepartmentsAndActiveTrue_returnsActiveBotInDepartment() {
        List<ChatBot> result = chatBotRepository.findByDepartmentsAndActiveTrue(Set.of(deptA));

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getName()).isEqualTo("Active Bot A");
    }

    @Test
    void findByDepartmentsAndActiveTrue_excludesInactiveBots() {
        List<ChatBot> result = chatBotRepository.findByDepartmentsAndActiveTrue(Set.of(deptA));

        assertThat(result).noneMatch(b -> b.getName().equals("Inactive Bot A"));
    }

    @Test
    void findByDepartmentsAndActiveTrue_multipleDepartments_returnsAll() {
        List<ChatBot> result = chatBotRepository.findByDepartmentsAndActiveTrue(Set.of(deptA, deptB));

        assertThat(result).hasSize(2);
        assertThat(result).extracting(ChatBot::getName)
                .containsExactlyInAnyOrder("Active Bot A", "Active Bot B");
    }

    @Test
    void findByDepartmentsAndActiveTrue_emptySet_returnsEmpty() {
        List<ChatBot> result = chatBotRepository.findByDepartmentsAndActiveTrue(Set.of());

        assertThat(result).isEmpty();
    }
}
