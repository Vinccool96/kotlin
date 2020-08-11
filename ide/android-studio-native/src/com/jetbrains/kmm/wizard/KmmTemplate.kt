/*
 * Copyright 2010-2020 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package com.jetbrains.kmm.wizard

import com.android.tools.idea.wizard.template.*
import com.jetbrains.kmm.KmmBundle
import com.jetbrains.kmm.versions.MobileMultiplatformPluginVersionsInfo

object KmmTemplate : RecipeHolder(), Template {
    override val category = Category.Application
    override val constraints = listOf(TemplateConstraint.Kotlin)
    override val formFactor = FormFactor.Mobile

    override val minCompileSdk: Int = 0
    override val minSdk: Int = 0

    override val name: String = KmmBundle.message("wizard.project.title")
    override val description: String = KmmBundle.message("wizard.project.description")
    override val documentationUrl = KmmBundle.message(
        "wizard.project.documentationUrl",
        MobileMultiplatformPluginVersionsInfo.getPluginVersionNumber()
    )

    override val recipe: Recipe = { data -> recipeImpl(data) }

    override val revision: Int = 0

    override val uiContexts: Collection<WizardUiContext> = listOf(WizardUiContext.NewProject, WizardUiContext.NewProjectExtraDetail)

    override val widgets: Collection<Widget<*>> = listOf(
        TextFieldWidget(androidAppName),
        TextFieldWidget(iosAppName),
        TextFieldWidget(sharedName),
        CheckBoxWidget(sharedTests)
    )

    override fun thumb() = Thumb {
        this.javaClass.getResource("/META-INF/kmm-project-logo.png")
    }
}