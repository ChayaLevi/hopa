package com.hopa.family.engine

import com.hopa.family.namespace.Namespaces
import com.hopa.family.preferences.PreferencesNamespace
import com.hopa.family.rules.RulesNamespace
import com.hopa.family.shopping.ShoppingNamespace

/**
 * The capabilities this build of the family board knows, beyond the board itself. A device that
 * meets one it does not know keeps its ops aside and carries on (amended D15); adding one here, with
 * its fixtures, is how a capability ships.
 */
object FamilyCapabilities {
    val ALL: Namespaces = Namespaces(listOf(ShoppingNamespace(), PreferencesNamespace(), RulesNamespace()))
}
