package com.nexusai.tool;

/**
 * An explicit, bounded capability exposed to the agent. Tools are fixed Java code: the
 * model only chooses a tool by name and proposes arguments, which the registry binds to
 * {@code A} and validates before {@link #execute} runs. Model text is never executed.
 *
 * <p>Scope comes only from the {@link ToolContext} (the workspace of the run), never from
 * arguments, so the agent cannot widen its own access.
 *
 * @param <A> typed, Bean-Validation-annotated argument record
 */
public interface AgentTool<A> {

    /** Stable identifier the model uses, e.g. {@code search_knowledge}. */
    String name();

    /** What the tool does and when to use it (shown to the model). */
    String description();

    /** The argument shape as shown to the model, e.g. {@code {"query": string, "limit": 1-8}}. */
    String argumentsHint();

    Class<A> argumentType();

    /**
     * @throws ToolArgumentException  arguments are well-formed but refer to something invalid
     * @throws ToolExecutionException the capability failed (e.g. a dependency is down)
     */
    ToolResult execute(ToolContext context, A arguments);
}
