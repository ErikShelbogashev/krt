package krt.i9ns.pbrt

/** A parameter of a pbrt-v4 directive: `"type name" value` or `"type name" [values]`. */
data class PbrtParam(val type: String, val name: String, val values: List<Any>) {
    fun doubles(): List<Double> = values.map { (it as? Double) ?: error("$name: number expected, got $it") }
    fun strings(): List<String> = values.map { it as? String ?: error("$name: string expected, got $it") }
}

/**
 * One directive of a pbrt-v4 scene file: its [name], positional [args] (strings, numbers, or a bracketed list of
 * numbers as one list) and named [params]. A directive from a `#krt` comment line is [krt] (ADR 0016).
 */
data class PbrtDirective(
    val name: String,
    val args: List<Any>,
    val params: Map<String, PbrtParam>,
    val line: Int,
    val krt: Boolean = false,
) {
    fun string(index: Int): String = args.getOrNull(index) as? String ?: fail("string argument ${index + 1} expected")

    fun numbers(): List<Double> = args.flatMap { arg ->
        when (arg) {
            is Double -> listOf(arg)
            is List<*> -> arg.map { it as? Double ?: fail("numbers expected") }
            else -> fail("numbers expected, got $arg")
        }
    }

    fun double(name: String, default: Double): Double = params[name]?.doubles()?.firstOrNull() ?: default

    fun int(name: String, default: Int): Int = params[name]?.doubles()?.firstOrNull()?.toInt() ?: default

    fun text(name: String, default: String): String = params[name]?.strings()?.firstOrNull() ?: default

    fun bool(name: String, default: Boolean): Boolean = params[name]?.values?.firstOrNull()?.let {
        it == true || it == "true"
    } ?: default

    fun fail(message: String): Nothing = throw PbrtError(line, "$name: $message")
}

/** A scene file that cannot be read, with the [line] at fault. */
class PbrtError(val line: Int, message: String) : IllegalArgumentException("line $line: $message")

/**
 * Tokens of a pbrt-v4 scene file into directives (ADR 0016): a pure transformation. Comments are skipped, except
 * lines that start with `#krt`, whose rest is read as a directive of krt's own, marked [PbrtDirective.krt].
 */
object PbrtSyntax {

    private val TYPES = setOf(
        "integer", "float", "point2", "vector2", "point3", "vector3", "normal3", "normal", "point", "vector",
        "spectrum", "rgb", "blackbody", "bool", "string", "texture", "color",
    )

    private sealed interface Token {
        val line: Int
    }

    private data class Word(val text: String, override val line: Int) : Token
    private data class Text(val text: String, override val line: Int) : Token
    private data class Number(val value: Double, override val line: Int) : Token
    private data class Open(override val line: Int) : Token
    private data class Close(override val line: Int) : Token
    private data class KrtLine(val tokens: List<Token>, override val line: Int) : Token

    /** The directives of [text], in order, those of `#krt` lines included. */
    fun parse(text: String): List<PbrtDirective> = directives(tokens(text), krt = false)

    private fun directives(tokens: List<Token>, krt: Boolean): List<PbrtDirective> {
        val result = mutableListOf<PbrtDirective>()
        var i = 0
        while (i < tokens.size) {
            val token = tokens[i]
            if (token is KrtLine) {
                result += directives(token.tokens, krt = true)
                i++
                continue
            }
            val word = token as? Word ?: throw PbrtError(token.line, "directive expected, got $token")
            i++
            val args = mutableListOf<Any>()
            val params = linkedMapOf<String, PbrtParam>()
            // ActiveTransform takes a bare word: StartTime, EndTime or All.
            if (word.text == "ActiveTransform") (tokens.getOrNull(i) as? Word)?.let { args += it.text; i++ }
            while (i < tokens.size && tokens[i] !is Word && tokens[i] !is KrtLine) {
                val next = tokens[i]
                val declaration = (next as? Text)?.text?.trim()?.split(Regex("\\s+"))
                if (declaration != null && declaration.size == 2 && declaration[0] in TYPES) {
                    val (values, after) = values(tokens, i + 1)
                    params[declaration[1]] = PbrtParam(declaration[0], declaration[1], values)
                    i = after
                } else {
                    val (value, after) = argument(tokens, i)
                    args += value
                    i = after
                }
            }
            result += PbrtDirective(word.text, args, params, word.line, krt)
        }
        return result
    }

    /** A value or a bracketed list of values, and the index after it. */
    private fun values(tokens: List<Token>, at: Int): Pair<List<Any>, Int> {
        val first = tokens.getOrNull(at) ?: throw PbrtError(tokens[at - 1].line, "value expected")
        if (first !is Open) return listOf(scalar(first)) to at + 1
        val values = mutableListOf<Any>()
        var i = at + 1
        while (tokens.getOrNull(i) !is Close) {
            values += scalar(tokens.getOrNull(i) ?: throw PbrtError(first.line, "] expected"))
            i++
        }
        return values to i + 1
    }

    private fun argument(tokens: List<Token>, at: Int): Pair<Any, Int> {
        val token = tokens[at]
        if (token !is Open) return scalar(token) to at + 1
        val (values, after) = values(tokens, at)
        return values to after
    }

    private fun scalar(token: Token): Any = when (token) {
        is Number -> token.value
        is Text -> token.text
        is Word -> when (token.text) {
            "true" -> true
            "false" -> false
            else -> throw PbrtError(token.line, "value expected, got ${token.text}")
        }
        else -> throw PbrtError(token.line, "value expected")
    }

    /** Tokens of [text] line by line: strings do not span lines in a pbrt file. */
    private fun tokens(text: String): List<Token> = text.lines().flatMapIndexed { index, line ->
        val number = index + 1
        val trimmed = line.trimStart()
        if (isKrt(trimmed)) {
            listOf(KrtLine(lineTokens(trimmed.substring(KRT.length), number), number))
        } else {
            lineTokens(line, number)
        }
    }

    private fun isKrt(line: String): Boolean =
        line.startsWith(KRT) && (line.length == KRT.length || line[KRT.length].isWhitespace())

    /** Tokens of one line, up to a comment. */
    private fun lineTokens(line: String, number: Int): List<Token> {
        val tokens = mutableListOf<Token>()
        var i = 0
        while (i < line.length && line[i] != '#') {
            val c = line[i]
            i = when {
                c.isWhitespace() -> i + 1
                c == '"' -> {
                    val end = line.indexOf('"', i + 1)
                    if (end < 0) throw PbrtError(number, "unclosed string")
                    tokens += Text(line.substring(i + 1, end), number)
                    end + 1
                }
                c == '[' || c == ']' -> {
                    tokens += if (c == '[') Open(number) else Close(number)
                    i + 1
                }
                else -> word(line, i, number, tokens)
            }
        }
        return tokens
    }

    /** A number or a bare word from [start]; the index after it. */
    private fun word(line: String, start: Int, number: Int, tokens: MutableList<Token>): Int {
        var end = start
        while (end < line.length && !line[end].isWhitespace() && line[end] !in "[]\"#") end++
        val word = line.substring(start, end)
        tokens += word.toDoubleOrNull()?.let { Number(it, number) } ?: Word(word, number)
        return end
    }

    private const val KRT = "#krt"
}
