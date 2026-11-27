package krt.domain.render.model

/** Which renderer draws the frame. */
enum class RenderMode(val title: String) {
    /** Progressive path tracing on the CPU workers, the main mode (ADR 0008). */
    CPU("CPU path tracing"),

    /** Path tracing in an SkSL shader through Skia (ADR 0009). */
    GPU("GPU (SkSL)"),

    /** Object edges rasterized on the CPU. */
    WIREFRAME("Wireframe"),
}
