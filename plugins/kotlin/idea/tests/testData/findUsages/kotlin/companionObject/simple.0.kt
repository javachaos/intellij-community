
// PSI_ELEMENT: org.jetbrains.kotlin.psi.KtObjectDeclaration
// OPTIONS: usages
class Foo {
    companion <caret>object {
        fun f() {
        }
    }
}


