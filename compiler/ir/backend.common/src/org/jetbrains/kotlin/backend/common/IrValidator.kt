/*
 * Copyright 2010-2024 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.backend.common

import org.jetbrains.kotlin.cli.common.messages.CompilerMessageSeverity
import org.jetbrains.kotlin.config.IrVerificationMode
import org.jetbrains.kotlin.ir.IrBuiltIns
import org.jetbrains.kotlin.ir.IrElement
import org.jetbrains.kotlin.ir.declarations.IrDeclaration
import org.jetbrains.kotlin.ir.declarations.IrDeclarationParent
import org.jetbrains.kotlin.ir.declarations.IrFile
import org.jetbrains.kotlin.ir.util.DeclarationParentsVisitor
import org.jetbrains.kotlin.ir.util.dump
import org.jetbrains.kotlin.ir.util.render
import org.jetbrains.kotlin.ir.visitors.IrElementVisitorVoid
import org.jetbrains.kotlin.ir.visitors.acceptChildrenVoid
import org.jetbrains.kotlin.ir.visitors.acceptVoid

typealias ReportIrValidationError = (IrFile?, IrElement, String) -> Unit

data class IrValidatorConfig(
    val checkTypes: Boolean = true,
    val checkProperties: Boolean = false,
    val checkScopes: Boolean = false, // TODO: Consider setting to true by default and deleting
)

class IrValidator(
    val irBuiltIns: IrBuiltIns,
    val config: IrValidatorConfig,
    val reportError: ReportIrValidationError
) : IrElementVisitorVoid {

    var currentFile: IrFile? = null

    override fun visitFile(declaration: IrFile) {
        currentFile = declaration
        super.visitFile(declaration)
        if (config.checkScopes) {
            ScopeValidator(this::error).check(declaration)
        }
    }

    private fun error(element: IrElement, message: String) {
        reportError(currentFile, element, message)
    }

    private val elementChecker = CheckIrElementVisitor(irBuiltIns, this::error, config)

    override fun visitElement(element: IrElement) {
        element.acceptVoid(elementChecker)
        element.acceptChildrenVoid(this)
    }
}

private fun IrElement.checkDeclarationParents(reportError: ReportIrValidationError) {
    val checker = CheckDeclarationParentsVisitor()
    accept(checker, null)
    if (checker.errors.isNotEmpty()) {
        val expectedParents = LinkedHashSet<IrDeclarationParent>()
        reportError(
            null,
            this,
            buildString {
                append("Declarations with wrong parent: ")
                append(checker.errors.size)
                append("\n")
                checker.errors.forEach {
                    append("declaration: ")
                    append(it.declaration.render())
                    append("\nexpectedParent: ")
                    append(it.expectedParent.render())
                    append("\nactualParent: ")
                    append(it.actualParent?.render())
                }
                append("\nExpected parents:\n")
                expectedParents.forEach {
                    append(it.dump())
                }
            }
        )
    }
}

private class CheckDeclarationParentsVisitor : DeclarationParentsVisitor() {
    class Error(val declaration: IrDeclaration, val expectedParent: IrDeclarationParent, val actualParent: IrDeclarationParent?)

    val errors = ArrayList<Error>()

    override fun handleParent(declaration: IrDeclaration, actualParent: IrDeclarationParent) {
        try {
            val assignedParent = declaration.parent
            if (assignedParent != actualParent) {
                errors.add(Error(declaration, assignedParent, actualParent))
            }
        } catch (e: Exception) {
            errors.add(Error(declaration, actualParent, null))
        }
    }
}

open class IrValidationError(message: String? = null) : IllegalStateException(message)

class DuplicateIrNodeError(element: IrElement) : IrValidationError(element.render())

/**
 * Verifies common IR invariants that should hold in all the backends.
 */
fun performBasicIrValidation(
    element: IrElement,
    irBuiltIns: IrBuiltIns,
    checkProperties: Boolean = false,
    checkTypes: Boolean = false,
    reportError: ReportIrValidationError,
) {
    val validatorConfig = IrValidatorConfig(
        checkTypes = checkTypes,
        checkProperties = checkProperties,
    )
    val validator = IrValidator(irBuiltIns, validatorConfig, reportError)
    try {
        element.acceptVoid(validator)
    } catch (e: DuplicateIrNodeError) {
        // Performing other checks may cause e.g. infinite recursion.
        return
    }
    element.checkDeclarationParents(reportError)
}

/**
 * Verifies common IR invariants that should hold in all the backends.
 *
 * Reports errors to [CommonBackendContext.messageCollector].
 *
 * **Note:** this method does **not** throw [IrValidationError]. Use [throwValidationErrorIfNeeded] for checking for errors and throwing
 * [IrValidationError]. This gives the caller the opportunity to perform additional (for example, backend-specific) validation before
 * aborting. The caller decides when it's time to abort.
 */
fun performBasicIrValidation(
    context: CommonBackendContext,
    fragment: IrElement,
    mode: IrVerificationMode,
    phaseName: String,
    checkProperties: Boolean = false,
    checkTypes: Boolean = false,
) {
    if (mode == IrVerificationMode.NONE) return
    performBasicIrValidation(
        fragment,
        context.irBuiltIns,
        checkProperties,
        checkTypes,
    ) { file, element, message ->
        context.reportIrValidationError(mode, file, element, message, phaseName)
    }
}

fun ErrorReportingContext.reportIrValidationError(
    mode: IrVerificationMode,
    file: IrFile?,
    element: IrElement,
    message: String,
    phaseName: String,
) {
    val severity = when (mode) {
        IrVerificationMode.WARNING -> CompilerMessageSeverity.WARNING
        IrVerificationMode.ERROR -> CompilerMessageSeverity.ERROR
        IrVerificationMode.NONE -> return
    }
    val phaseMessage = if (phaseName.isNotEmpty()) "$phaseName: " else ""
    // TODO: render all element's parents.
    report(
        severity,
        element,
        file,
        "[IR VALIDATION] ${phaseMessage}${"$message\n" + element.render()}",
    )
}

/**
 * Allows to abort the compilation process if after validating the IR there were errors and [mode] is [IrVerificationMode.ERROR].
 */
fun ErrorReportingContext.throwValidationErrorIfNeeded(mode: IrVerificationMode) {
    if (messageCollector.hasErrors() && mode == IrVerificationMode.ERROR) {
        throw IrValidationError()
    }
}
