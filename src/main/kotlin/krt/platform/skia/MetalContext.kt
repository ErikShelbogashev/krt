package krt.platform.skia

import java.lang.foreign.Arena
import java.lang.foreign.FunctionDescriptor
import java.lang.foreign.Linker
import java.lang.foreign.MemorySegment
import java.lang.foreign.SymbolLookup
import java.lang.foreign.ValueLayout.ADDRESS
import org.jetbrains.skia.DirectContext

/**
 * A Skia context on the system's Metal device of its own, apart from the window's: the device and a command queue
 * reached through the foreign function API and the Objective-C runtime. Null where there is no Metal: on another
 * system, or where native access is not allowed.
 */
object MetalContext {
    private const val OBJC = "/usr/lib/libobjc.A.dylib"
    private const val METAL = "/System/Library/Frameworks/Metal.framework/Metal"

    @Suppress("TooGenericExceptionCaught") // any failure to reach Metal means there is none to use
    fun create(): DirectContext? = try {
        if (System.getProperty("os.name").startsWith("Mac")) make() else null
    } catch (e: Throwable) {
        System.err.println("krt: no Metal context of its own (${e.message}); the window draws GPU frames")
        null
    }

    private fun make(): DirectContext? {
        val linker = Linker.nativeLinker()
        val objc = SymbolLookup.libraryLookup(OBJC, Arena.global())
        val metal = SymbolLookup.libraryLookup(METAL, Arena.global())
        val selector = linker.downcallHandle(
            objc.find("sel_registerName").orElseThrow(), FunctionDescriptor.of(ADDRESS, ADDRESS),
        )
        val send = linker.downcallHandle(
            objc.find("objc_msgSend").orElseThrow(), FunctionDescriptor.of(ADDRESS, ADDRESS, ADDRESS),
        )
        val makeDevice = linker.downcallHandle(
            metal.find("MTLCreateSystemDefaultDevice").orElseThrow(), FunctionDescriptor.of(ADDRESS),
        )
        val device = (makeDevice.invokeWithArguments() as MemorySegment).address()
        if (device == 0L) return null
        val queue = Arena.ofConfined().use { arena ->
            val name = selector.invokeWithArguments(arena.allocateFrom("newCommandQueue")) as MemorySegment
            (send.invokeWithArguments(MemorySegment.ofAddress(device), name) as MemorySegment).address()
        }
        return DirectContext.makeMetal(device, queue)
    }
}
