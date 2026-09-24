package com.kei.pulse.data

fun interface PrivilegedSysfsReader {
    fun readText(path: String): String?

    fun readTexts(paths: List<String>): Map<String, String?> {
        return paths.associateWith(::readText)
    }
}
