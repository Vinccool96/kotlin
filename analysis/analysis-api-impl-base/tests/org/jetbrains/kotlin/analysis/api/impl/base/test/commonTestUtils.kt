/*
 * Copyright 2010-2021 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.analysis.api.impl.base.test

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.util.Computable
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiReference
import com.intellij.psi.impl.source.resolve.reference.impl.PsiMultiReference
import org.jetbrains.kotlin.analysis.api.KtAnalysisSession
import org.jetbrains.kotlin.analysis.api.analyse
import org.jetbrains.kotlin.analysis.api.components.KtDeclarationRendererOptions
import org.jetbrains.kotlin.analysis.api.symbols.*
import org.jetbrains.kotlin.analysis.api.symbols.markers.KtNamedSymbol
import org.jetbrains.kotlin.analysis.api.symbols.markers.KtSymbolWithKind
import org.jetbrains.kotlin.diagnostics.PsiDiagnosticUtils.offsetToLineAndColumn
import org.jetbrains.kotlin.idea.references.KtReference
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.*

inline fun <T> runReadAction(crossinline runnable: () -> T): T {
    return ApplicationManager.getApplication().runReadAction(Computable { runnable() })
}

fun <R> executeOnPooledThreadInReadAction(action: () -> R): R =
    ApplicationManager.getApplication().executeOnPooledThread<R> { runReadAction(action) }.get()

inline fun <R> analyseOnPooledThreadInReadAction(context: KtElement, crossinline action: KtAnalysisSession.() -> R): R =
    executeOnPooledThreadInReadAction {
        analyse(context) { action() }
    }

fun PsiElement?.position(): String {
    if (this == null) return "(unknown)"
    return offsetToLineAndColumn(containingFile.viewProvider.document, textRange.startOffset).toString()
}

fun KtSymbol.getNameWithPositionString(): String {
    return when (val psi = this.psi) {
        is KtDeclarationWithBody -> psi.name
        is KtNamedDeclaration -> psi.name
        null -> "null"
        else -> psi::class.simpleName
    } + "@" + psi.position()
}

fun String.indented(indent: Int): String {
    val indentString = " ".repeat(indent)
    return indentString + replace("\n", "\n$indentString")
}

fun KtDeclaration.getNameWithPositionString(): String {
    return (presentation?.presentableText ?: name ?: this::class.simpleName) + "@" + position()
}

fun findReferencesAtCaret(mainKtFile: KtFile, caretPosition: Int): List<KtReference> =
    mainKtFile.findReferenceAt(caretPosition)?.unwrapMultiReferences().orEmpty().filterIsInstance<KtReference>()

fun PsiReference.unwrapMultiReferences(): List<PsiReference> = when (this) {
    is KtReference -> listOf(this)
    is PsiMultiReference -> references.flatMap { it.unwrapMultiReferences() }
    else -> error("Unexpected reference $this")
}

fun KtAnalysisSession.renderResolvedTo(
    symbols: List<KtSymbol>,
    renderingOptions: KtDeclarationRendererOptions = KtDeclarationRendererOptions.DEFAULT
) =
    symbols.map { renderResolveResult(it, renderingOptions) }
        .sorted()
        .withIndex()
        .joinToString(separator = "\n") { "${it.index}: ${it.value}" }

private fun KtAnalysisSession.renderResolveResult(
    symbol: KtSymbol,
    renderingOptions: KtDeclarationRendererOptions
): String {
    return buildString {
        symbolContainerFqName(symbol)?.let { fqName ->
            append("(in $fqName) ")
        }
        when (symbol) {
            is KtDeclarationSymbol -> append(symbol.render(renderingOptions))
            is KtPackageSymbol -> append("package ${symbol.fqName}")
            is KtReceiverParameterSymbol -> {
                append("extension receiver with type ")
                append(symbol.type.render(renderingOptions.typeRendererOptions))
            }
            else -> error("Unexpected symbol ${symbol::class}")
        }
    }
}

@Suppress("unused")// KtAnalysisSession receiver
private fun KtAnalysisSession.symbolContainerFqName(symbol: KtSymbol): String? {
    if (symbol is KtPackageSymbol || symbol is KtValueParameterSymbol) return null
    val nonLocalFqName = when (symbol) {
        is KtConstructorSymbol -> symbol.containingClassIdIfNonLocal?.asSingleFqName()
        is KtCallableSymbol -> symbol.callableIdIfNonLocal?.asSingleFqName()?.parent()
        is KtClassLikeSymbol -> symbol.classIdIfNonLocal?.asSingleFqName()?.parent()
        else -> null
    }
    when (nonLocalFqName) {
        null -> Unit
        FqName.ROOT -> return "ROOT"
        else -> return nonLocalFqName.asString()
    }
    val container = (symbol as? KtSymbolWithKind)?.getContainingSymbol() ?: return null
    val parents = generateSequence(container) { it.getContainingSymbol() }.toList().asReversed()
    return "<local>: " + parents.joinToString(separator = ".") { (it as? KtNamedSymbol)?.name?.asString() ?: "<no name>" }
}

