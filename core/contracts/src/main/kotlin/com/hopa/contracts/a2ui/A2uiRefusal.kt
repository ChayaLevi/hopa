package com.hopa.contracts.a2ui

/**
 * How decoding and checking stop: [refuse] throws, [refusable] turns the
 * throw back into an [A2uiResult]. Internal; callers only see results.
 */
internal class A2uiRefusal(val problem: A2uiProblem) : Exception(problem.message) {
    companion object {
        fun refuse(code: String, path: String, message: String): Nothing =
            throw A2uiRefusal(A2uiProblem(code, path, message))

        inline fun <T> refusable(block: () -> T): A2uiResult<T> = try {
            A2uiResult.Ok(block())
        } catch (refusal: A2uiRefusal) {
            A2uiResult.Refused(refusal.problem)
        }
    }
}
