// FIR_IDENTICAL
@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)
import kotlinx.cinterop.*

class Z(rawPtr: NativePtr): CStructVar(rawPtr)

fun foo(x: CValue<Z>) = x

fun bar() {
    staticCFunction(::foo)
}
