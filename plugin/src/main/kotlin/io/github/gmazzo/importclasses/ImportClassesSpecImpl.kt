package io.github.gmazzo.importclasses

internal abstract class ImportClassesSpecImpl : ImportClassesSpec {

    override fun keep(className: String) =
        keep(className, null)

    override fun keep(className: String, renameTo: String?) {
        if (renameTo != null) {
            keepsAndRenames.put(className, renameTo)

        } else {
            keepsAndRenames.put(
                className, repackageTo
                    .map { repackage -> "${repackage}.${className.substring(className.lastIndexOf('.') + 1)}" }
                    .orElse("")
            )
        }
    }

    override fun include(vararg pattern: String) {
        filters.addAll(*pattern)
    }

    override fun exclude(vararg pattern: String) {
        filters.addAll(pattern.map { "!$it" })
    }

    override fun option(vararg option: String) {
        extraOptions.addAll(*option)
    }

}
