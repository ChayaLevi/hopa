package com.hopa.records

import com.hopa.contracts.wire.Action
import com.hopa.contracts.wire.Observation
import com.hopa.contracts.wire.ObservationNode
import com.hopa.contracts.wire.ScreenSize
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class RunRecorderClearTest {
    @Test
    fun clearing_drops_every_record() = runBlocking {
        val recorder = InMemoryRunRecorder()
        val observation = Observation(
            id = "obs-1",
            timestamp = 1,
            screenSize = ScreenSize(1080, 2400),
            tree = ObservationNode(type = "root"),
        )
        recorder.append(
            RunRecordDraft(
                runId = "run-1",
                step = 0,
                traceId = "trace-1",
                action = Action(kind = "ui.observe"),
                classification = "read",
                approved = null,
                observationHash = "sha256:" + "0".repeat(64),
                verified = true,
                executed = true,
            ),
        )

        recorder.clear()

        assertTrue(recorder.all().isEmpty())
    }
}
