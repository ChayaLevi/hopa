package com.hopa.decision.laix

import com.hopa.decision.Verb
import dev.laix.decision.contracts.Verb as LaixVerb

/**
 * LAIX's verbs to ours.
 *
 * OBSERVE means "look before asking anyone"; HOPA calls that VERIFY. GUIDE means
 * "tell the person what to do"; HOPA cannot guide in the middle of a step, so it
 * becomes ASK. The remaining verbs have the same meaning.
 */
object LaixVerbs {
    fun toHopa(verb: LaixVerb): Verb = when (verb) {
        LaixVerb.WAIT -> Verb.WAIT
        LaixVerb.OBSERVE -> Verb.VERIFY
        LaixVerb.GUIDE -> Verb.ASK
        LaixVerb.ASK -> Verb.ASK
        LaixVerb.ACT -> Verb.ACT
        LaixVerb.VERIFY -> Verb.VERIFY
        LaixVerb.ESCALATE -> Verb.ESCALATE
    }
}
