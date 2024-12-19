![GitHub](https://img.shields.io/github/license/gmazzo/gradle-import-classes-plugin)
[![Gradle Plugin Portal](https://img.shields.io/gradle-plugin-portal/v/io.github.gmazzo.importclasses)](https://plugins.gradle.org/plugin/io.github.gmazzo.importclasses)
[![Build Status](https://github.com/gmazzo/gradle-import-classes-plugin/actions/workflows/build.yaml/badge.svg)](https://github.com/gmazzo/gradle-import-classes-plugin/actions/workflows/build.yaml)
[![Coverage](https://codecov.io/gh/gmazzo/gradle-import-classes-plugin/branch/main/graph/badge.svg?token=D5cDiPWvcS)](https://codecov.io/gh/gmazzo/gradle-import-classes-plugin)
[![Users](https://img.shields.io/badge/users_by-Sourcegraph-purple)](https://sourcegraph.com/search?q=content:io.github.gmazzo.importclasses+-repo:github.com/gmazzo/gradle-import-classes-plugin)

# gradle-import-classes-plugin
A Gradle plugin to import and repackage dependencies [`Proguard`](https://www.guardsquare.com/manual/home) tool.

# Usage
Apply the plugin:
```kotlin
plugins {
    java
    id("io.github.gmazzo.importclasses") version "<latest>" 
}

```
And then use the new `importClasses` DSL on the target `SourceSet` you want the classes to be imported:
```kotlin
sourceSets.main {
    importClasses("org.apache.commons:commons-lang3:3.14.0") {
        repackageTo = "org.test.imported"
        keep("org.apache.commons.lang3.StringUtils")
    }
}
```

Then the `main` SourceSet will have the class `org.apache.commons.lang3.StringUtils` from (`org.apache.commons:commons-lang3:3.14.0`) 
imported and repackaged as `org.test.imported.StringUtils`.
```java
package org.test;

import org.test.imported.StringUtils;

public class Foo {

    public String swapCase(String string) {
        return StringUtils.swapCase(string);
    }

}
```

> [!NOTE]
> This plugin uses Gradle's [Artifact Transform](https://docs.gradle.org/current/userguide/artifact_transforms.html) 
> by running [`Proguard`](https://www.guardsquare.com/manual/home) on the target dependency.
> You can pass any Proguard option to it inside `importClasses`'s configuration block by calling `option(<rule>)`
