package com.hopa.family.board

/** Thrown inside the reducer to refuse an op; [BoardReducer] turns it into [BoardStep.Rejected]. */
internal class BoardRejection(val code: RejectCode) : RuntimeException(code.name) {
    override fun fillInStackTrace(): Throwable = this

    companion object {
        fun reject(code: RejectCode): Nothing = throw BoardRejection(code)
    }
}
