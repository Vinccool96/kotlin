/*
 * Copyright 2010-2021 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.analysis.api.fir

import com.intellij.mock.MockApplication
import com.intellij.mock.MockProject
import com.intellij.openapi.Disposable
import org.jetbrains.kotlin.analysis.api.fir.utils.configureApplicationEnvironment
import org.jetbrains.kotlin.analysis.api.impl.barebone.test.FrontendApiTestConfiguratorService
import org.jetbrains.kotlin.analysis.low.level.api.fir.test.base.FirLowLevelFrontendApiTestConfiguratorService
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.test.builders.TestConfigurationBuilder

object FirFrontendApiTestConfiguratorService : FrontendApiTestConfiguratorService {
    override fun TestConfigurationBuilder.configureTest(disposable: Disposable) {
        with(FirLowLevelFrontendApiTestConfiguratorService) { configureTest(disposable) }
    }

    override fun processTestFiles(files: List<KtFile>): List<KtFile> {
        return FirLowLevelFrontendApiTestConfiguratorService.processTestFiles(files)
    }

    override fun getOriginalFile(file: KtFile): KtFile {
        return FirLowLevelFrontendApiTestConfiguratorService.getOriginalFile(file)
    }

    override fun registerProjectServices(project: MockProject) {
        FirLowLevelFrontendApiTestConfiguratorService.registerProjectServices(project)
    }

    override fun registerApplicationServices(application: MockApplication) {
        FirLowLevelFrontendApiTestConfiguratorService.registerApplicationServices(application)
        configureApplicationEnvironment(application)
    }

    override fun doOutOfBlockModification(file: KtFile) {
        FirLowLevelFrontendApiTestConfiguratorService.doOutOfBlockModification(file)
    }
}
