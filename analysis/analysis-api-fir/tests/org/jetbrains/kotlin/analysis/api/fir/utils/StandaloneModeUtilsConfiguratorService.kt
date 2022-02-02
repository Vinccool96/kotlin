/*
 * Copyright 2010-2022 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.analysis.api.fir.utils

import com.intellij.mock.MockApplication
import com.intellij.mock.MockProject
import com.intellij.openapi.Disposable
import org.jetbrains.kotlin.analysis.api.fir.FirFrontendApiTestConfiguratorService
import org.jetbrains.kotlin.analysis.api.impl.barebone.test.FrontendApiTestConfiguratorService
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.test.builders.TestConfigurationBuilder

object StandaloneModeUtilsConfiguratorService : FrontendApiTestConfiguratorService {
    override val allowDependedAnalysisSession: Boolean
        get() = false

    override fun TestConfigurationBuilder.configureTest(disposable: Disposable) {
        with(FirFrontendApiTestConfiguratorService) { configureTest(disposable) }
    }

    // TODO: other variant to register project services by compiler configuration?
    override fun registerProjectServices(project: MockProject) {
        // Will be configured at the test
    }

    override fun registerApplicationServices(application: MockApplication) {
        configureApplicationEnvironment(application)
    }

    override fun doOutOfBlockModification(file: KtFile) {
        FirFrontendApiTestConfiguratorService.doOutOfBlockModification(file)
    }
}
