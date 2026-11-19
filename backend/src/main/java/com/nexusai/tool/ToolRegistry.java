package com.nexusai.tool;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.JsonNodeFactory;

/**
 * The single place AI-callable capabilities are declared and invoked. Invocation is
 * lookup → bind JSON arguments to the tool's typed record → Bean Validation → execute in
 * the caller's workspace → audited {@link ToolInvocation}. Anything the model proposes that
 * doesn't fit is REJECTED without running.
 */
@Component
public class ToolRegistry {

    private static final Logger log = LoggerFactory.getLogger(ToolRegistry.class);
    private static final int MAX_ARGUMENT_CHARS = 2000;

    /** Tool description as shown to the model. */
    public record ToolDescriptor(String name, String description, String argumentsHint) {}

    private final Map<String, AgentTool<?>> tools = new LinkedHashMap<>();
    private final Validator validator;
    private final JsonMapper json = JsonMapper.builder()
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES) // extra keys can't widen scope; they're ignored
            .build();

    public ToolRegistry(List<AgentTool<?>> tools, Validator validator) {
        this.validator = validator;
        tools.stream().sorted(Comparator.comparing(AgentTool::name)).forEach(t -> {
            if (this.tools.putIfAbsent(t.name(), t) != null) {
                throw new IllegalStateException("Duplicate tool name: " + t.name());
            }
        });
    }

    public List<ToolDescriptor> catalog() {
        return tools.values().stream()
                .map(t -> new ToolDescriptor(t.name(), t.description(), t.argumentsHint()))
                .toList();
    }

    public Set<String> names() {
        return Set.copyOf(tools.keySet());
    }

    public Optional<AgentTool<?>> find(String name) {
        return Optional.ofNullable(tools.get(name));
    }

    /** Validates and runs a proposed call. Never throws for model-caused problems. */
    public ToolInvocation invoke(String name, JsonNode arguments, ToolContext context) {
        JsonNode args = arguments == null || arguments.isNull() ? JsonNodeFactory.instance.objectNode() : arguments;
        String argumentsJson = truncate(args.toString());
        AgentTool<?> tool = name == null ? null : tools.get(name);
        if (tool == null) {
            return ToolInvocation.rejected(String.valueOf(name), argumentsJson,
                    "Unknown tool '" + name + "'. Available: " + String.join(", ", tools.keySet()));
        }
        if (!args.isObject()) {
            return ToolInvocation.rejected(name, argumentsJson, "Arguments must be a JSON object");
        }
        return run(tool, args, argumentsJson, context);
    }

    private <A> ToolInvocation run(AgentTool<A> tool, JsonNode args, String argumentsJson, ToolContext context) {
        A bound;
        try {
            bound = json.treeToValue(args, tool.argumentType());
        } catch (RuntimeException e) {
            return ToolInvocation.rejected(tool.name(), argumentsJson, "Invalid arguments: expected " + tool.argumentsHint());
        }
        Set<ConstraintViolation<A>> violations = validator.validate(bound);
        if (!violations.isEmpty()) {
            String problems = violations.stream()
                    .map(v -> v.getPropertyPath() + " " + v.getMessage())
                    .sorted()
                    .collect(Collectors.joining("; "));
            return ToolInvocation.rejected(tool.name(), argumentsJson, "Invalid arguments: " + problems);
        }

        long started = System.nanoTime();
        try {
            ToolResult result = tool.execute(context, bound);
            return new ToolInvocation(tool.name(), argumentsJson, ToolInvocation.Status.SUCCEEDED, result, null, elapsed(started));
        } catch (ToolArgumentException e) {
            return new ToolInvocation(tool.name(), argumentsJson, ToolInvocation.Status.REJECTED, null, e.getMessage(), elapsed(started));
        } catch (ToolExecutionException e) {
            log.warn("Tool {} failed: {}", tool.name(), e.getMessage());
            return new ToolInvocation(tool.name(), argumentsJson, ToolInvocation.Status.FAILED, null, e.getMessage(), elapsed(started));
        } catch (RuntimeException e) {
            log.error("Tool {} failed unexpectedly", tool.name(), e);
            return new ToolInvocation(tool.name(), argumentsJson, ToolInvocation.Status.FAILED, null,
                    "The tool failed unexpectedly.", elapsed(started));
        }
    }

    private static String truncate(String text) {
        return text.length() <= MAX_ARGUMENT_CHARS ? text : text.substring(0, MAX_ARGUMENT_CHARS);
    }

    private static long elapsed(long started) {
        return (System.nanoTime() - started) / 1_000_000;
    }
}
