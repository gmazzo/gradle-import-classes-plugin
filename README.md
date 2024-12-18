![GitHub](https://img.shields.io/github/license/gmazzo/gradle-import-classes-plugin)
[![Gradle Plugin Portal](https://img.shields.io/gradle-plugin-portal/v/io.github.gmazzo.importclasses)](https://plugins.gradle.org/plugin/io.github.gmazzo.importclasses)
[![Build Status](https://github.com/gmazzo/gradle-import-classes-plugin/actions/workflows/build.yaml/badge.svg)](https://github.com/gmazzo/gradle-import-classes-plugin/actions/workflows/build.yaml)
[![Coverage](https://codecov.io/gh/gmazzo/gradle-import-classes-plugin/branch/main/graph/badge.svg?token=D5cDiPWvcS)](https://codecov.io/gh/gmazzo/gradle-import-classes-plugin)
[![Users](https://img.shields.io/badge/users_by-Sourcegraph-purple)](https://sourcegraph.com/search?q=content:io.github.gmazzo.importclasses+-repo:github.com/gmazzo/gradle-import-classes-plugin)

# gradle-import-classes-plugin
A Gradle plugin to import and repackage dependencies using `jarjar` and `proguard` tools.

# Usage
Apply the plugin:
```kotlin
plugins {
    id("io.github.gmazzo.importclasses") version "<latest>" 
}
```
