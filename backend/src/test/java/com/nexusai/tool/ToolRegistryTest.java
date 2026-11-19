package com.nexusai.tool;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import jakarta.validation.Validation;
import jakarta.validation.constraints.NotBlank;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class ToolRegistryTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final UUID WORKSPACE = UUID.randomUUID();

    record EchoArgs(@NotBlank String text) {}

    /** Records the context it ran in; fails on demand. */
    static final class EchoTool implements AgentTool<EchoArgs> {
        final AtomicReference<ToolContext> lastContext = new AtomicReference<>();

        public String name() { return "echo"; }
        public String description() { return "Echoes text."; }
        public String argumentsHint() { return "{\"text\": string}"; }
        public Class<EchoArgs> argumentType() { return EchoArgs.class; }

        public ToolResult execute(ToolContext context, EchoArgs args) {
            lastContext.set(context);
            switch (args.text()) {
                case "fail" -> throw new ToolExecutionException("Dependency down", null);
                case "bad-ref" -> throw new ToolArgumentException("Unknown reference");
                case "boom" -> throw new IllegalStateException("bug");
                default -> { return new ToolResult("echo " + args.text(), List.of()); }
            }
        }
    }

    static final EvidenceLedger NO_LEDGER = new EvidenceLedger() {
        public Optional<String> record(Passage passage) { return Optional.empty(); }
        public Optional<UUID> resolve(String handle) { return Optional.empty(); }
    };

    private final EchoTool echo = new EchoTool();
    private final ToolRegistry registry = new ToolRegistry(new ArrayList<>(List.of(echo)),
            Validation.buildDefaultValidatorFactory().getValidator());
    private final ToolContext context = new ToolContext(WORKSPACE, NO_LEDGER);

    @Test
    void validCallsRunInTheCallersWorkspaceAndAreTimed() {
        ToolInvocation inv = registry.invoke("echo", JSON.readTree("{\"text\": \"hi\"}"), context);
        assertThat(inv.status()).isEqualTo(ToolInvocation.Status.SUCCEEDED);
        assertThat(inv.result().summary()).isEqualTo("echo hi");
        assertThat(inv.argumentsJson()).isEqualTo("{\"text\":\"hi\"}");
        assertThat(inv.durationMs()).isGreaterThanOrEqualTo(0);
        assertThat(echo.lastContext.get().workspaceId()).isEqualTo(WORKSPACE);
    }

    @Test
    void argumentsCannotChangeTheWorkspace() {
        UUID other = UUID.randomUUID();
        ToolInvocation inv = registry.invoke("echo",
                JSON.readTree("{\"text\": \"hi\", \"workspaceId\": \"" + other + "\", \"workspace_id\": \"" + other + "\"}"), context);
        assertThat(inv.succeeded()).isTrue();
        assertThat(echo.lastContext.get().workspaceId()).isEqualTo(WORKSPACE);
    }

    @Test
    void unknownToolsAndInvalidArgumentsAreRejectedWithoutRunning() {
        assertThat(registry.invoke("rm_rf", JSON.readTree("{\"path\": \"/\"}"), context))
                .satisfies(i -> {
                    assertThat(i.status()).isEqualTo(ToolInvocation.Status.REJECTED);
                    assertThat(i.error()).contains("Unknown tool 'rm_rf'").contains("echo");
                });
        assertThat(registry.invoke("echo", JSON.readTree("{\"text\": \"  \"}"), context).error())
                .contains("Invalid arguments").contains("text");
        assertThat(registry.invoke("echo", JSON.readTree("{\"text\": [1,2]}"), context).status())
                .isEqualTo(ToolInvocation.Status.REJECTED);
        assertThat(registry.invoke("echo", JSON.readTree("[\"text\"]"), context).error())
                .contains("JSON object");
        assertThat(echo.lastContext.get()).isNull();
    }

    @Test
    void toolFailuresAreContainedAndClassified() {
        assertThat(registry.invoke("echo", JSON.readTree("{\"text\": \"fail\"}"), context))
                .satisfies(i -> {
                    assertThat(i.status()).isEqualTo(ToolInvocation.Status.FAILED);
                    assertThat(i.error()).isEqualTo("Dependency down");
                });
        assertThat(registry.invoke("echo", JSON.readTree("{\"text\": \"bad-ref\"}"), context).status())
                .isEqualTo(ToolInvocation.Status.REJECTED);
        assertThat(registry.invoke("echo", JSON.readTree("{\"text\": \"boom\"}"), context))
                .satisfies(i -> {
                    assertThat(i.status()).isEqualTo(ToolInvocation.Status.FAILED);
                    assertThat(i.error()).doesNotContain("bug"); // internals stay in the log
                });
    }

    @Test
    void catalogDescribesToolsAndNamesMustBeUnique() {
        assertThat(registry.catalog()).singleElement().satisfies(d -> {
            assertThat(d.name()).isEqualTo("echo");
            assertThat(d.argumentsHint()).contains("text");
        });
        assertThatThrownBy(() -> new ToolRegistry(List.of(new EchoTool(), new EchoTool()),
                Validation.buildDefaultValidatorFactory().getValidator()))
                .isInstanceOf(IllegalStateException.class);
    }
}
