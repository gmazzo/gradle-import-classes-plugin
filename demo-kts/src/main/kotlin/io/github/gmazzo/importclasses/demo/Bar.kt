package io.github.gmazzo.importclasses.demo

import io.github.gmazzo.importclasses.demo.imported.StringUtils
import io.github.gmazzo.importclasses.demo.imported.json.JsonSlurper

object Bar {

    val hello = StringUtils.capitalize("hello, world!")

    fun fromJson(string: String?) =  JsonSlurper().parseText(string)

}
