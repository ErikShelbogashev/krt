package krt.domain.render.model

import java.util.Locale

/** Texts of the status bar for the frame on screen. */
object RenderStatus {

    private const val MS_PER_SECOND = 1000.0

    fun progressText(mode: RenderMode, progress: RenderProgress): String = when {
        mode == RenderMode.WIREFRAME -> "Wireframe · ready"
        progress.isDone -> "Done · ${progress.passes}/${progress.passes} passes"
        progress.isDraft -> "Quick look · ${progress.passes} passes to go"
        else -> "Pass ${progress.currentPass} / ${progress.passes}"
    }

    fun elapsedText(progress: RenderProgress): String =
        String.format(Locale.ROOT, "%.1f s", progress.elapsedMs / MS_PER_SECOND)

    fun engineText(mode: RenderMode, threads: Int, depth: Int): String = when (mode) {
        RenderMode.GPU -> "GPU · Skia SkSL · depth $depth"
        RenderMode.CPU -> "CPU · $threads threads · depth $depth"
        RenderMode.WIREFRAME -> "CPU · wireframe raster"
    }
}
