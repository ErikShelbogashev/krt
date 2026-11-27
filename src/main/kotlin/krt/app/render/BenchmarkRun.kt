package krt.app.render

import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.pointer.PointerEvent
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import krt.app.shell.MonoValue
import krt.domain.camera.model.FlightPath
import krt.domain.camera.model.FlightPose
import krt.domain.render.model.Benchmark
import krt.domain.render.model.BenchmarkConfig
import krt.domain.render.model.BenchmarkResult
import krt.domain.render.model.BenchmarkSample
import krt.domain.render.model.DoneWatch
import krt.domain.render.model.FlightRecording
import krt.domain.render.model.RenderProgress
import krt.domain.scene.model.DemoScene


/** Window frames a run waits before its first frame counts, so the scene is set up and compiled. */
private const val WARM_UP_FRAMES = 10

/** Live figures of the run in progress, for the overlay. */
private data class Live(val run: Int, val config: BenchmarkConfig?, val step: Int, val fps: Double)

/**
 * Runs the [plan] of the flight benchmark over the window: for each run [onStart] sets it up, the frame is let finish,
 * then the camera follows [flightOf] its scene, one pose per window frame through [onPose], while the window's frame
 * times and the [progress] of the frame on screen are recorded; after the flight the time until the frame is done.
 * The overlay blocks input; Esc or Stop ends the benchmark early. [onFinish] gets the results of the runs so far.
 */
@Suppress("LongParameterList")
@Composable
fun BenchmarkRun(
    plan: List<BenchmarkConfig>,
    flightOf: (DemoScene) -> FlightPath,
    progress: () -> RenderProgress,
    onStart: (BenchmarkConfig) -> Unit,
    onPose: (FlightPose) -> Unit,
    onFinish: (List<BenchmarkResult>) -> Unit,
    modifier: Modifier = Modifier,
) {
    val currentFlightOf by rememberUpdatedState(flightOf)
    val currentProgress by rememberUpdatedState(progress)
    val currentOnStart by rememberUpdatedState(onStart)
    val currentOnPose by rememberUpdatedState(onPose)
    val currentOnFinish by rememberUpdatedState(onFinish)
    var live by remember { mutableStateOf(Live(0, null, 0, 0.0)) }
    var stopped by remember { mutableStateOf(false) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(plan) {
        val results = mutableListOf<BenchmarkResult>()
        for ((index, config) in plan.withIndex()) {
            if (stopped) break
            live = Live(index, config, 0, 0.0)
            currentOnStart(config)
            repeat(WARM_UP_FRAMES) { withFrameNanos { } }
            awaitDone(currentProgress) { stopped }
            val sample = fly(currentFlightOf(config.scene), currentProgress, currentOnPose, { stopped }) { recording ->
                live = Live(index, config, recording.poses - 1, recording.fps)
            }
            results += Benchmark.summarize(config, sample)
        }
        currentOnFinish(results)
    }
    LaunchedEffect(Unit) { focus.requestFocus() }
    Box(
        modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.15f))
            .pointerInput(Unit) { awaitPointerEventScope { while (true) consumeAll(awaitPointerEvent()) } }
            .focusRequester(focus)
            .focusable()
            .onKeyEvent { event ->
                if (event.key == Key.Escape) stopped = true
                true
            },
        contentAlignment = Alignment.TopCenter,
    ) {
        RunCard(live, plan.size, onStop = { stopped = true })
    }
}

@Composable
private fun RunCard(live: Live, runs: Int, onStop: () -> Unit) {
    val config = live.config
    Column(
        Modifier
            .padding(top = 72.dp)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh, RoundedCornerShape(12.dp))
            .padding(16.dp)
            .width(420.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Benchmark · run ${live.run + 1} of $runs", style = MaterialTheme.typography.titleSmall)
                Text(
                    config?.let { "${it.scene.title} · ${it.mode.title} · ${it.width}×${it.height}" } ?: "",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            MonoValue("${live.fps.toInt()} fps")
        }
        LinearProgressIndicator(
            progress = { (live.run + live.step.toFloat() / BenchmarkConfig.STEPS) / runs },
            modifier = Modifier.fillMaxWidth(),
            drawStopIndicator = {},
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Pose ${live.step + 1} / ${BenchmarkConfig.STEPS} · Esc stops",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            OutlinedButton(onClick = onStop) { Text("Stop") }
        }
    }
}

/** Pumps the flight of one run: a pose per window frame; [FlightRecording] keeps the figures. */
private suspend fun fly(
    flight: FlightPath,
    progress: () -> RenderProgress,
    onPose: (FlightPose) -> Unit,
    stopped: () -> Boolean,
    onStep: (FlightRecording) -> Unit,
): BenchmarkSample {
    var recording = FlightRecording.startingAt(withFrameNanos { it })
    while (recording.poses < BenchmarkConfig.STEPS && !stopped()) {
        val shown = progress()
        onPose(flight.pose(recording.poses, BenchmarkConfig.STEPS))
        recording = recording.frame(withFrameNanos { it }, shown)
        onStep(recording)
    }
    val settle = if (stopped()) null else awaitDone(progress, stopped)
    return recording.sample(settle, stopped())
}

/** Pumps window frames while [DoneWatch] says to wait; its result is the time to the finished frame. */
private suspend fun awaitDone(progress: () -> RenderProgress, stopped: () -> Boolean): Double? {
    val watch = DoneWatch(withFrameNanos { it })
    var now = watch.startNanos
    while (watch.waiting(now, progress(), stopped())) now = withFrameNanos { it }
    return watch.result(now, progress(), stopped())
}

private fun consumeAll(event: PointerEvent) {
    event.changes.forEach { it.consume() }
}
