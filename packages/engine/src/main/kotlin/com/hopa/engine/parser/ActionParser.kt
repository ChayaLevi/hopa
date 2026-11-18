package com.hopa.engine.parser

import com.hopa.contracts.Action

class ActionParser(
    private val decisionParser: DecisionParser = DecisionParser(),
) {
    fun parse(text: String): Action? = decisionParser.parse(text).decision?.action
}
