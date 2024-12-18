package io.github.gmazzo.importclasses

import org.gradle.api.artifacts.transform.TransformAction
import org.gradle.api.artifacts.transform.TransformOutputs
import org.gradle.api.artifacts.transform.TransformParameters

abstract class EmptyTransform : TransformAction<TransformParameters.None> {

    override fun transform(outputs: TransformOutputs) {
    }

    companion object {
        const val EMPTY_TYPE = "extractClasses:empty"
    }

}
