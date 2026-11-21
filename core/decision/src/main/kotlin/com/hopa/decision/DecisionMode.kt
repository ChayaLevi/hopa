package com.hopa.decision

/** How the person wants decisions taken. Stored as one string in settings. */
sealed interface DecisionMode {
    /** The value stored in settings; [Off] is the only honest default. */
    val stored: String

    /** The model decides, exactly as before any layer existed. */
    data object Off : DecisionMode {
        override val stored: String = "off"
    }

    /** The best allowed layer for each action class. */
    data object Automatic : DecisionMode {
        override val stored: String = "auto"
    }

    /** One layer everywhere it is allowed; other classes fall back to automatic. */
    data class Pinned(val layerId: String) : DecisionMode {
        override val stored: String get() = layerId
    }

    companion object {
        /**
         * Read a stored value. Unknown or empty text is [Off], never a silent
         * jump to a layer: a typo must not change what executes on the phone.
         */
        fun parse(raw: String): DecisionMode {
            val value = raw.trim()
            return when {
                value.isEmpty() || value.equals("off", ignoreCase = true) -> Off
                value.equals("auto", ignoreCase = true) ||
                    value.equals("automatic", ignoreCase = true) -> Automatic
                else -> Pinned(value)
            }
        }
    }
}
