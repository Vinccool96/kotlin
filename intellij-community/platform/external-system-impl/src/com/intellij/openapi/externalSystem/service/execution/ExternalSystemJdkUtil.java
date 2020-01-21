// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.externalSystem.service.execution;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.*;
import com.intellij.openapi.projectRoots.impl.DependentSdkType;
import com.intellij.openapi.projectRoots.impl.SdkConfigurationUtil;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.roots.ui.configuration.projectRoot.SdkDownloadTracker;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.EnvironmentUtil;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.model.java.JdkVersionDetector;

import java.io.File;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.stream.Stream;

import static com.intellij.openapi.util.Pair.pair;

public class ExternalSystemJdkUtil {
  public static final String USE_INTERNAL_JAVA = "#JAVA_INTERNAL";
  public static final String USE_PROJECT_JDK = "#USE_PROJECT_JDK";
  public static final String USE_JAVA_HOME = "#JAVA_HOME";

  @Nullable
  @Contract("_, null -> null")
  public static Sdk getJdk(@Nullable Project project, @Nullable String jdkName) throws ExternalSystemJdkException {
    return resolveJdkName(getProjectJdk(project), jdkName);
  }

  @Nullable
  @Contract("_, null -> null")
  public static Sdk resolveJdkName(@Nullable Sdk projectSdk, @Nullable String jdkName) throws ExternalSystemJdkException {
    if (jdkName == null) return null;
    switch (jdkName) {
      case USE_INTERNAL_JAVA:
        return getInternalJdk();
      case USE_PROJECT_JDK:
        if (projectSdk == null) {
          throw new ProjectJdkNotFoundException();
        }
        return resolveDependentJdk(projectSdk);
      case USE_JAVA_HOME:
        return getJavaHomeJdk();
      default:
        return getJdk(jdkName);
    }
  }

  @NotNull
  private static Sdk getProjectJdk(@Nullable Project project) {
    if (project != null) {
      Sdk res = ProjectRootManager.getInstance(project).getProjectSdk();
      if (res != null) return res;

      Module[] modules = ModuleManager.getInstance(project).getModules();
      for (Module module : modules) {
        Sdk sdk = ModuleRootManager.getInstance(module).getSdk();
        if (sdk != null && sdk.getSdkType() instanceof JavaSdkType) return sdk;
      }
    }

    // Workaround for projects without project Jdk
    SdkType jdkType = getJavaSdk();
    return ProjectJdkTable.getInstance()
      .getSdksOfType(jdkType).stream()
      .filter(it -> isValidJdk(it))
      .max(jdkType.versionComparator())
      .orElseGet(ExternalSystemJdkUtil::getInternalJdk);
  }

  @NotNull
  private static Sdk getJavaHomeJdk() {
    String javaHome = EnvironmentUtil.getEnvironmentMap().get("JAVA_HOME");
    if (StringUtil.isEmptyOrSpaces(javaHome)) throw new UndefinedJavaHomeException();
    if (!isValidJdk(javaHome)) throw new InvalidJavaHomeException(javaHome);
    return ExternalSystemJdkProvider.getInstance().createJdk(null, javaHome);
  }

  @Nullable
  private static Sdk getJdk(@NotNull String jdkName) {
    Sdk jdk = ProjectJdkTable.getInstance().findJdk(jdkName);
    if (jdk == null) return null;
    String homePath = jdk.getHomePath();
    if (!isValidJdk(jdk)) throw new InvalidSdkException(homePath);
    return jdk;
  }

  @NotNull
  public static Pair<String, Sdk> getAvailableJdk(@Nullable Project project) throws ExternalSystemJdkException {
    SdkType javaSdkType = getJavaSdkType();

    if (project != null) {
      Sdk projectJdk = findProjectJDK(project, javaSdkType);
      if (projectJdk != null) {
        return pair(USE_PROJECT_JDK, projectJdk);
      }

      Sdk referencedJdk = findReferencedJdk(project);
      if (referencedJdk != null) {
        return pair(USE_PROJECT_JDK, referencedJdk);
      }
    }

    List<Sdk> allJdks = ProjectJdkTable.getInstance().getSdksOfType(javaSdkType);
    Sdk mostRecentSdk = allJdks.stream().filter(sdk -> isValidJdk(sdk)).max(javaSdkType.versionComparator()).orElse(null);
    if (mostRecentSdk != null) {
      return pair(mostRecentSdk.getName(), mostRecentSdk);
    }

    if (!ApplicationManager.getApplication().isUnitTestMode()) {
      String javaHome = EnvironmentUtil.getEnvironmentMap().get("JAVA_HOME");
      if (isValidJdk(javaHome)) {
        SimpleJavaSdkType simpleJavaSdkType = SimpleJavaSdkType.getInstance();
        String sdkName = simpleJavaSdkType.suggestSdkName(null, javaHome);
        return pair(USE_JAVA_HOME, simpleJavaSdkType.createJdk(sdkName, javaHome));
      }
    }

    return pair(USE_INTERNAL_JAVA, getInternalJdk());
  }

  private static Sdk findProjectJDK(@NotNull Project project, SdkType javaSdkType) {
    Sdk projectSdk = ProjectRootManager.getInstance(project).getProjectSdk();
    Stream<Sdk> projectSdks = Stream.concat(Stream.of(projectSdk),
                                            Stream.of(ModuleManager.getInstance(project).getModules()).map(module -> ModuleRootManager
                                              .getInstance(module).getSdk()));
    return projectSdks
      .filter(sdk -> sdk != null && sdk.getSdkType() == javaSdkType && isValidJdk(sdk))
      .findFirst().orElse(null);
  }

  @Nullable
  @Contract("null -> null")
  private static Sdk findReferencedJdk(Sdk projectSdk) {
    if (projectSdk != null
        && projectSdk.getSdkType() instanceof DependentSdkType
        && projectSdk.getSdkType() instanceof JavaSdkType) {
      final JavaSdkType sdkType = (JavaSdkType)projectSdk.getSdkType();
      final String jdkPath = FileUtil.toSystemIndependentName(new File(sdkType.getBinPath(projectSdk)).getParent());
      return Arrays.stream(ProjectJdkTable.getInstance().getAllJdks())
        .filter(sdk -> {
          final String homePath = sdk.getHomePath();
          return homePath != null && FileUtil.toSystemIndependentName(homePath).equals(jdkPath);
        })
        .findFirst().orElse(null);
    } else {
      return null;
    }
  }

  @Nullable
  private static Sdk findReferencedJdk(Project project) {
    Sdk projectSdk = ProjectRootManager.getInstance(project).getProjectSdk();
    return findReferencedJdk(projectSdk);
  }

  @NotNull
  public static Sdk resolveDependentJdk(@NotNull Sdk sdk) {
    Sdk parentSdk = findReferencedJdk(sdk);
    if (parentSdk == null) return sdk;
    return parentSdk;
  }

  @NotNull
  public static Collection<String> suggestJdkHomePaths() {
    return getJavaSdkType().suggestHomePaths();
  }

  @NotNull
  public static SdkType getJavaSdkType() {
    return getJavaSdk();
  }

  @Contract("null -> false")
  public static boolean isValidJdk(@Nullable Sdk jdk) {
    if (jdk == null) return false;
    if (!(jdk.getSdkType() instanceof JavaSdkType)) return false;
    if (SdkDownloadTracker.getInstance().isDownloading(jdk)) return true;
    return isValidJdk(jdk.getHomePath());
  }

  @Contract("null -> false")
  public static boolean isValidJdk(@Nullable String homePath) {
    return !StringUtil.isEmptyOrSpaces(homePath) && JdkUtil.checkForJdk(homePath) && JdkUtil.checkForJre(homePath);
  }

  @NotNull
  public static Sdk addJdk(String homePath) {
    ExternalSystemJdkProvider jdkProvider = ExternalSystemJdkProvider.getInstance();
    List<Sdk> sdks = Arrays.asList(ProjectJdkTable.getInstance().getAllJdks());
    String name = SdkConfigurationUtil.createUniqueSdkName(jdkProvider.getJavaSdkType(), homePath, sdks);
    Sdk jdk = jdkProvider.createJdk(name, homePath);
    SdkConfigurationUtil.addSdk(jdk);
    return jdk;
  }

  @NotNull
  private static SdkType getJavaSdk() {
    return ExternalSystemJdkProvider.getInstance().getJavaSdkType();
  }

  @NotNull
  private static Sdk getInternalJdk() {
    return ExternalSystemJdkProvider.getInstance().getInternalJdk();
  }

  @Contract("null -> false")
  public static boolean isJdk9orLater(@Nullable String javaHome) {
    JdkVersionDetector.JdkVersionInfo jdkVersionInfo =
      javaHome == null ? null : JdkVersionDetector.getInstance().detectJdkVersionInfo(javaHome);
    return jdkVersionInfo != null && jdkVersionInfo.version.isAtLeast(9);
  }
}