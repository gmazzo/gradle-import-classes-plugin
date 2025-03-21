package demo

import io.github.gmazzo.importclasses.demo.imported.debug.JsonSlurper

object FooDebug {

    fun fromJson(string: String?) =  JsonSlurper().parseText(string)

}
