package com.example.domain.usecase

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.example.domain.model.DailySheetRow
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CsvExporter @Inject constructor(
    @ApplicationContext private val context: Context
) {
    fun buildCsvContent(rows: List<DailySheetRow>): String {
        val sb = StringBuilder()
        sb.appendLine("date,done,total,percent,note")
        for (row in rows) {
            val escapedNote = "\"" + row.note.replace("\"", "\"\"").replace("\n", " ") + "\""
            sb.appendLine("${row.date},${row.doneCount},${row.totalCount},${row.percent}%,$escapedNote")
        }
        return sb.toString()
    }

    fun createShareIntent(rows: List<DailySheetRow>): Intent {
        val exportDir = File(context.cacheDir, "exports").apply { mkdirs() }
        val csvFile = File(exportDir, "habitworks_progress.csv")
        csvFile.writeText(buildCsvContent(rows))
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            csvFile
        )
        return Intent(Intent.ACTION_SEND).apply {
            type = "text/csv"
            putExtra(Intent.EXTRA_SUBJECT, "HabitWorks Progress")
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }
}
