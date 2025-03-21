![GitHub](https://img.shields.io/github/license/gmazzo/gradle-import-classes-plugin)
[![Maven Central](https://img.shields.io/maven-central/v/io.github.gmazzo.importclasses/io.github.gmazzo.importclasses.gradle.plugin)](https://central.sonatype.com/artifact/io.github.gmazzo.importclasses/io.github.gmazzo.importclasses.gradle.plugin)
[![Gradle Plugin Portal](https://img.shields.io/gradle-plugin-portal/v/io.github.gmazzo.importclasses)](https://plugins.gradle.org/plugin/io.github.gmazzo.importclasses)
[![Build Status](https://github.com/gmazzo/gradle-import-classes-plugin/actions/workflows/ci-cd.yaml/badge.svg)](https://github.com/gmazzo/gradle-import-classes-plugin/actions/workflows/ci-cd.yaml)
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

importClasses {
    dependencies("org.apache.commons:commons-lang3:3.14.0")
    repackageTo = "org.test.imported"
    keep("org.apache.commons.lang3.StringUtils")
}
```

You can also use `importClasses` and `importClassesLibrary` configurations in the project's `dependencies` block:.
```kotlin
dependencies {
    importClasses("org.apache.commons:commons-lang3:3.14.0")
}
```

The companion `importClassesLibrary` will be mapped to [Proguard's
`-libraryjars` option](https://www.guardsquare.com/manual/configuration/usage#libraryjars).

Then the SourceSet will have the class `org.apache.commons.lang3.StringUtils` from (
`org.apache.commons:commons-lang3:3.14.0`)
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


## Binding to SourceSets and Android's Variants

By default, the plugin will bind with the `main` SourceSet, and works with `java` and `android` plugins.
Further specific imports can be done by creating new specs matching a Java `SourceSet` or an Android `Variant` name

You can configure multiple (and isolated) `importClasses` through the DSL:

```kotlin
importClasses {
    specs {
        create("debug") {  // will be imported onto Android's `debug` source set only
            dependencies("org.foo:foo:1.0.0")
            repackageTo = "org.foo.another.imported"
            keep("org.foo.AnotherClass")
        }
    }
}
```

