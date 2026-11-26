package com.hopa.family.engine

/** The stored engine was written by a newer version of the app: keep it, do not read it. */
class NewerEngineVersion(
    val version: Int,
) : IllegalStateException("engine state v$version is newer than v${EngineStore.CURRENT}")
