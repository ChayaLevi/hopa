package com.hopa.decision

/**
 * What a decision layer may do next.
 *
 * The set is deliberately small: it is the vocabulary the loop understands, not
 * the vocabulary of any one vendor. [ACT] is the only verb that changes the
 * device; everything else ends the step with an explanation or a pause.
 */
enum class Verb {
    /** Do the action in the context. */
    ACT,

    /** Do nothing for now; the situation may improve by itself. */
    WAIT,

    /** Stop and ask the person. */
    ASK,

    /** Look again before deciding: read the screen or check the outcome. */
    VERIFY,

    /** Hand the decision to a person because a boundary was reached. */
    ESCALATE,
}
