package br.com.investia.ai.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Trava o interpretador determinístico do copiloto (modo sem LLM).
 *
 * O bug original: "cria uma meta de viagem de 5000" caía no texto de ajuda porque o
 * padrão exigia "criar"/"crie"/"nova" e não cobria a forma "cria".
 */
class ChatDeterministicIntentTest {

    @SuppressWarnings("unchecked")
    private static Map<String, Object> resolve(String question) throws Exception {
        ChatService service = new ChatService(null, null, null, null);
        Method m = ChatService.class.getDeclaredMethod("resolveDeterministic", String.class, List.class);
        m.setAccessible(true);
        return (Map<String, Object>) m.invoke(service, question, List.of());
    }

    private static String toolOf(String question) throws Exception {
        Map<String, Object> intent = resolve(question);
        return intent == null ? null : String.valueOf(intent.get("tool"));
    }

    @Test
    @DisplayName("cria uma meta com valor resolve create_goal")
    void criaMetaComValor() throws Exception {
        assertThat(toolOf("cria uma meta de viagem de 5000")).isEqualTo("create_goal");
        assertThat(toolOf("criar meta de reserva de emergencia 10000")).isEqualTo("create_goal");
        assertThat(toolOf("quero uma nova meta de carro 30000")).isEqualTo("create_goal");
        assertThat(toolOf("crie uma meta de viagem 2500")).isEqualTo("create_goal");
    }

    @Test
    @DisplayName("meta sem valor não inventa número")
    void metaSemValorNaoInventa() throws Exception {
        assertThat(resolve("cria uma meta de viagem")).isNull();
    }

    @Test
    @DisplayName("nome da meta sai limpo, sem o verbo")
    void nomeDaMetaLimpo() throws Exception {
        Map<String, Object> intent = resolve("cria uma meta de viagem de 5000");
        assertThat(intent).isNotNull();
        @SuppressWarnings("unchecked")
        Map<String, Object> args = (Map<String, Object>) intent.get("args");
        assertThat(String.valueOf(args.get("name"))).isEqualTo("viagem");
    }

    @Test
    @DisplayName("formas do verbo gastar resolvem create_expense")
    void formasDeGasto() throws Exception {
        assertThat(toolOf("gastei 45,90 no mercado hoje")).isEqualTo("create_expense");
        assertThat(toolOf("gasto de 120 com internet")).isEqualTo("create_expense");
        assertThat(toolOf("vou gastar 80 no cinema")).isEqualTo("create_expense");
        assertThat(toolOf("paguei 30 de taxi")).isEqualTo("create_expense");
        assertThat(toolOf("compra de 55 na farmacia")).isEqualTo("create_expense");
    }

    @Test
    @DisplayName("entradas continuam funcionando")
    void entradas() throws Exception {
        assertThat(toolOf("recebi 3200 de salario")).isEqualTo("create_income");
        assertThat(toolOf("entrou 500 de freela")).isEqualTo("create_income");
    }

    @Test
    @DisplayName("resumo e listagens")
    void consultas() throws Exception {
        assertThat(toolOf("quanto sobrou para aportar?")).isEqualTo("summary");
        assertThat(toolOf("qual meu saldo")).isEqualTo("summary");
        assertThat(toolOf("quais minhas contas")).isEqualTo("list_accounts");
    }

    @Test
    @DisplayName("aportar em meta")
    void aporte() throws Exception {
        assertThat(toolOf("aportar 300 na reserva de emergencia")).isEqualTo("contribute_goal");
    }
}
