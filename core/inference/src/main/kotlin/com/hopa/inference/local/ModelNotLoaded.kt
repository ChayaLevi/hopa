package com.hopa.inference.local

/** An on-device answer was asked for before a model file was loaded. */
class ModelNotLoaded(message: String) : IllegalStateException(message)
