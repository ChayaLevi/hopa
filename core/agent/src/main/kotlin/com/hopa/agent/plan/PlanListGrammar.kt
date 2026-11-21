package com.hopa.agent.plan

/**
 * Forces the plan answer into the shape this planner parses: up to six numbered
 * lines of short text, nothing else. A 0.6B model cannot hold that shape on its
 * own - it writes prose - so the sampler is constrained instead of the prompt
 * being trusted. The grammar lives next to the parser it protects.
 */
object PlanListGrammar {
    val GRAMMAR: String = """
        root  ::= ws step (nl step)* nl? ws
        step  ::= [1-6] ". " text
        text  ::= [^\n]{1,80}
        ws    ::= [ \t]*
        nl    ::= "\n"
    """.trimIndent()
}
