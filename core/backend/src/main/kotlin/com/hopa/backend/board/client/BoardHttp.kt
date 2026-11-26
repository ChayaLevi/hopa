package com.hopa.backend.board.client

/**
 * The board's HTTP calls, behind a port so the link runs against OkHttp in the apps and against an
 * in-process server in tests. Paths are absolute (`/v1/board/ops?...`); the bearer is added here.
 * A network failure throws; a refused call is an answer with its status.
 */
interface BoardHttp {
    suspend fun get(path: String): HttpAnswer

    suspend fun post(path: String, json: String): HttpAnswer

    suspend fun put(path: String, json: String): HttpAnswer
}
