// FIR_IDENTICAL
// ISSUE: KT-51007

// MODULE: lib
fun interface A {
    suspend fun foo()
}

// MODULE: main(lib)
suspend fun bar() {}

fun takeA(a: A?) {}

fun test() {
    takeA {
        bar()
    }
}
