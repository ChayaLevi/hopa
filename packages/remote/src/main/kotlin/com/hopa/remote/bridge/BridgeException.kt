package com.hopa.remote.bridge

import java.io.IOException

/** Raised for a bridge that is unreachable or answered with a refusal. */
class BridgeException(message: String) : IOException(message)
