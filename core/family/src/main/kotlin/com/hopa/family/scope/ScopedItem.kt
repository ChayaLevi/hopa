package com.hopa.family.scope

import com.hopa.family.model.Item
import com.hopa.family.model.Scope

/** An item together with the scope it lives in, which is its visibility (D11). */
data class ScopedItem(
    val scope: Scope,
    val item: Item,
)
