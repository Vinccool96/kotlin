/*
 * Copyright 2010-2021 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

/*
 * Copyright 2010-2020 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.cli.fir

import org.jetbrains.kotlin.backend.common.lower.ExpectDeclarationRemover
import org.jetbrains.kotlin.config.CommonConfigurationKeys
import org.jetbrains.kotlin.ir.backend.js.expectActualLinker
import org.jetbrains.kotlin.ir.backend.js.serializeModuleIntoKlib
import org.jetbrains.kotlin.ir.util.IrMessageLogger
import org.jetbrains.kotlin.ir.visitors.acceptVoid
import org.jetbrains.kotlin.library.KotlinAbiVersion

class ClassicJsKLibGeneratorResult

class ClassicJsKLibGeneratorBuilder : CompilationStageBuilder<FrontendToIrConverterResult, ClassicJsKLibGeneratorResult> {

    var outputKlibPath: String? = null

    var nopack: Boolean? = null

    var abiVersion: KotlinAbiVersion = KotlinAbiVersion.CURRENT

    var jsOutputName: String? = null

    override fun build(): CompilationStage<FrontendToIrConverterResult, ClassicJsKLibGeneratorResult> {
        return ClassicJsKLibGenerator(outputKlibPath!!, nopack!!, abiVersion, jsOutputName)
    }

    operator fun invoke(body: ClassicJsKLibGeneratorBuilder.() -> Unit): ClassicJsKLibGeneratorBuilder {
        this.body()
        return this
    }
}

class ClassicJsKLibGenerator internal constructor(
    val outputKlibPath: String,
    val nopack: Boolean,
    var abiVersion: KotlinAbiVersion,
    var jsOutputName: String?
) : CompilationStage<FrontendToIrConverterResult, ClassicJsKLibGeneratorResult> {

    override fun execute(
        input: FrontendToIrConverterResult
    ): ExecutionResult<ClassicJsKLibGeneratorResult> {
        val configuration = input.configuration

        val messageLogger = configuration[IrMessageLogger.IR_MESSAGE_LOGGER] ?: IrMessageLogger.None

        val moduleName = configuration[CommonConfigurationKeys.MODULE_NAME]!!

        val (moduleFragment, _, cleanFiles, symbolTable, _, _, bindingContext, project, files, dependenciesList, expectDescriptorToSymbol, hasErrors) = input

        if (!configuration.expectActualLinker) {
            moduleFragment.acceptVoid(ExpectDeclarationRemover(symbolTable, false))
        }

        serializeModuleIntoKlib(
            moduleName,
            project,
            configuration,
            messageLogger,
            bindingContext ?: error(""),
            files,
            outputKlibPath,
            dependenciesList.map { it.library },
            moduleFragment,
            expectDescriptorToSymbol,
            cleanFiles,
            nopack,
            perFile = false,
            hasErrors,
            abiVersion,
            jsOutputName
        )


        return ExecutionResult.Success(
            ClassicJsKLibGeneratorResult(),
            emptyList()
        )
    }
}

