package demo;

import io.github.gmazzo.importclasses.demo.imported.debug.JsonSlurper;

public class FooDebug {

    public Object fromJson(String string) {
        return new JsonSlurper().parseText(string);
    }

}
