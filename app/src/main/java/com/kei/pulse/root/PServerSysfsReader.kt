package com.kei.pulse.root

import android.content.Context
import com.kei.pulse.data.PrivilegedSysfsReader

class PServerSysfsReader(
    private val context: Context,
) : PrivilegedSysfsReader {

    override fun readText(path: String): String? {
        return readTexts(listOf(path))[path]
    }

    override fun readTexts(paths: List<String>): Map<String, String?> {
        val uniquePaths = paths.distinct()
        if (uniquePaths.isEmpty()) return emptyMap()

        val command = buildString {
            appendLine("set +e")
            uniquePaths.forEachIndexed { index, path ->
                appendLine("printf '%s\\n' '__PULSE_SYSFS_BEGIN_${index}__'")
                appendLine("cat ${path.shellSingleQuote()} 2>/dev/null")
                appendLine("printf '\\n%s\\n' '__PULSE_SYSFS_END_${index}__'")
            }
        }
        val output = RootSupport.runRootCommand(command).orEmpty()
        val byPath = uniquePaths.mapIndexed { index, path ->
            val begin = "__PULSE_SYSFS_BEGIN_${index}__"
            val end = "__PULSE_SYSFS_END_${index}__"
            val value = output
                .substringAfter(begin, missingDelimiterValue = "")
                .substringBefore(end, missingDelimiterValue = "")
                .trim()
                .takeIf { it.isNotEmpty() }
            path to value
        }.toMap()

        return paths.associateWith { path -> byPath[path] }
    }
}

private fun String.shellSingleQuote(): String {
    return "'${replace("'", "'\"'\"'")}'"
}
