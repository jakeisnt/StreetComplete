import com.esotericsoftware.yamlbeans.YamlConfig
import com.esotericsoftware.yamlbeans.YamlWriter
import de.westnordost.countryboundaries.CountryBoundaries
import org.gradle.api.DefaultTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.TaskAction
import java.io.FileInputStream
import java.io.FileWriter
import java.io.StringWriter
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

/** Counts the occurrence of values for a given key for a certain tag combination by country and
 *  writes the result in a YML file.
 *
 *  So, much like for example taginfo's values page but sorted by country code plus only counting
 *  elements sufficing a certain tag combination.
 *  ( https://taginfo.openstreetmap.org/keys/operator#values ) */
open class SophoxCountValueByCountryTask : DefaultTask() {

    @get:Input lateinit var targetFile: String
    @get:Input lateinit var osmTag: String
    @get:Input lateinit var sparqlQueryPart: String
    @get:Input var minCount: Int = 1
    @get:Input var minPercent: Double = 0.0

    private val pointRegex = Regex("Point\\(([-+\\d.]*) ([-+\\d.]*)\\)")
    private val boundaries = CountryBoundaries.load(FileInputStream("${project.projectDir}/app/src/main/assets/boundaries.ser"))

    @TaskAction fun run() {
        val query = """
        SELECT ?value ?loc
        WHERE { ?osm $sparqlQueryPart osmt:$osmTag ?value; osmm:loc ?loc. }
        """.trimIndent()

        // country code -> ( value -> count )
        val result: MutableMap<String, MutableMap<String, Int>> = mutableMapOf()

        val rows = querySophoxCsv(query).mapNotNull { parseCsvRow(it) }
        for (row in rows) {
            row.countryCode?.let {
                result.getOrPut(it, { mutableMapOf() }).compute(row.value) { _, u ->  (u ?: 0) + 1}
            }
        }

        val config = YamlConfig().apply {
            writeConfig.setWriteClassname(YamlConfig.WriteClassName.NEVER)
            writeConfig.isFlowStyle = true
            writeConfig.setEscapeUnicode(false)
        }

        val fileWriter = FileWriter(targetFile, false)
        fileWriter.write("# Do not edit manually. \n")
        fileWriter.write("# Data generated by counting number of OSM elements in the respecting countries.\n\n")

        for (countryCode in result.keys.sorted()) {
            val valuesForCountry = result[countryCode]!!
            val entries = valuesForCountry.entries.sortedByDescending { it.key }.sortedByDescending { it.value }
            val totalCount = valuesForCountry.values.sum()
            var hasAddedCountry = false
            for ((value, count) in entries) {
                if (count < minCount) continue
                if (100*(count.toDouble() / totalCount) < minPercent) continue

                if (!hasAddedCountry) {
                    fileWriter.write("$countryCode:\n")
                    hasAddedCountry = true
                }
                fileWriter.write("  - ${writeYaml(value, config)} # $count\n")
            }
        }
        fileWriter.close()
    }

    private val Row.countryCode: String? get() = boundaries.getIds(lon, lat).firstOrNull()

    private fun writeYaml(obj: String, config: YamlConfig): String {
        val str = StringWriter()
        val writer = YamlWriter(str, config)
        writer.write(obj)
        writer.close()
        return str.toString().removeSuffix("\n").removeSuffix("\r")
    }

    private fun querySophoxCsv(query: String): List<String> {
        val url = URL("https://sophox.org/sparql?query="+ URLEncoder.encode(query,"UTF-8"))
        val connection = url.openConnection() as HttpURLConnection
        try {
            connection.setRequestProperty("Accept", "text/csv")
            connection.setRequestProperty("User-Agent", "StreetComplete")
            connection.setRequestProperty("charset", StandardCharsets.UTF_8.name())
            connection.doOutput = true
            return connection.inputStream.bufferedReader().readLines()
        } finally {
            connection.disconnect()
        }
    }

    private fun parseCsvRow(row: String): Row? {
        val elements = row.split(',')
        val value = elements[0]
        if (elements.size < 2) return null
        val matchResult = pointRegex.matchEntire(elements[1]) ?: return null
        val lon = matchResult.groupValues[1].toDoubleOrNull() ?: return null
        val lat = matchResult.groupValues[2].toDoubleOrNull() ?: return null
        return Row(value, lon, lat)
    }

}

private data class Row(val value: String, val lon: Double, val lat: Double)