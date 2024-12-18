package io.github.gmazzo.importclasses.demo;

import io.github.gmazzo.importclasses.demo.imported.FastIgnoreRule;

public class Foo {

    public final FastIgnoreRule rule = new FastIgnoreRule("**");

    public boolean isMatch(String path) {
        return rule.isMatch(path, false);
    }

}
