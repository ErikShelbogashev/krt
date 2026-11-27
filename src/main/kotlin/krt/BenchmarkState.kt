package krt

import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import krt.app.render.BenchmarkResultsDialog
import krt.app.render.BenchmarkRun
import krt.app.render.BenchmarkSetupDialog
import krt.app.shell.KrtIcons
import krt.domain.camera.model.Flights
import krt.domain.render.model.BenchmarkConfig
import krt.domain.render.model.BenchmarkResult
import krt.domain.render.model.RenderMode
import krt.domain.render.model.RenderProgress
import krt.domain.render.model.SettingsSnapshot
import krt.infra.AppContext

/** Where the flight benchmark is: closed, being set up, running a plan, or showing results. */
class BenchmarkState {
    var setupOpen by mutableStateOf(false)
    var plan by mutableStateOf<List<BenchmarkConfig>?>(null)
    var results by mutableStateOf<List<BenchmarkResult>?>(null)

    /** The run in progress, whose renderer the window shows while it runs. */
    var running by mutableStateOf<BenchmarkConfig?>(null)
    var settings: SettingsSnapshot? = null

    /** The renderer the window's badge names: that of the run in progress, else the one [chosen] in the panel. */
    fun shownMode(chosen: RenderMode): RenderMode = running?.mode ?: chosen
}

@Composable
fun rememberBenchmarkState(): BenchmarkState = remember { BenchmarkState() }

/** Top bar button that opens the benchmark's setup. */
@Composable
fun BenchmarkButton(state: BenchmarkState, modifier: Modifier = Modifier) {
    IconButton(onClick = { state.setupOpen = true }, modifier = modifier) {
        Icon(KrtIcons.Timer, contentDescription = "Flight benchmark")
    }
}

/** The benchmark's dialogs and its run over the window, wired to the operations of [app]. */
@Composable
fun BenchmarkHost(
    app: AppContext,
    state: BenchmarkState,
    progress: () -> RenderProgress,
    modifier: Modifier = Modifier,
) {
    if (state.setupOpen) {
        BenchmarkSetupDialog(
            onRun = { plan ->
                state.settings = app.captureSettings()
                state.setupOpen = false
                state.plan = plan
            },
            onDismiss = { state.setupOpen = false },
        )
    }
    state.plan?.let { plan ->
        BenchmarkRun(
            plan = plan,
            flightOf = Flights::of,
            progress = progress,
            onStart = { config ->
                state.running = config
                app.startBenchmarkRun(config)
            },
            onPose = { app.flyCamera(it) },
            onFinish = { results ->
                state.settings?.let { app.restoreSettings(it) }
                state.running = null
                state.plan = null
                state.results = results
            },
            modifier = modifier,
        )
    }
    state.results?.let { results ->
        BenchmarkResultsDialog(
            results,
            onSaveCsv = { app.saveBenchmarkReport(it, results) },
            onDismiss = { state.results = null },
        )
    }
}
