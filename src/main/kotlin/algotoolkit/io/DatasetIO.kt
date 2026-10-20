package algotoolkit.io

import algotoolkit.model.Coord
import algotoolkit.model.Graph
import algotoolkit.model.formatNumber
import com.fasterxml.jackson.core.JsonProcessingException
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import java.io.IOException
import java.nio.charset.CharacterCodingException
import java.nio.file.Files
import java.nio.file.Path

/** All problems found in a dataset file. Invalid records are reported, never silently dropped. */
class DatasetException(val problems: List<String>) : Exception(problems.joinToString("\n"))

/**
 * Graph CSV : header `source,target,weight`, one undirected edge per line.
 * Graph JSON: {"directed": false, "nodes": [{"id": "A", "x": 0, "y": 0}], "edges": [{"source": "A", "target": "B", "weight": 4}]}
 * Array CSV : optional header `value`, then integers (one per line or comma separated).
 * Array JSON: [1, 2, 3] or {"values": [1, 2, 3]}
 */
object DatasetIO {
    private const val MAX_REPORTED = 50
    private val mapper = ObjectMapper()

    fun loadGraph(path: Path): Graph {
        val text = read(path)
        return when (extension(path)) {
            "csv" -> graphFromCsv(text)
            "json" -> graphFromJson(text)
            else -> unsupported(path)
        }
    }

    fun loadArray(path: Path): IntArray {
        val text = read(path)
        return when (extension(path)) {
            "csv" -> arrayFromCsv(text)
            "json" -> arrayFromJson(text)
            else -> unsupported(path)
        }
    }

    fun graphFromCsv(text: String): Graph {
        val rows = csvRows(text)
        if (rows.isEmpty()) fail("File is empty")
        val header = rows[0]
        if (header.fields.map { it.lowercase() } != listOf("source", "target", "weight")) {
            fail("Line ${header.line}: expected header 'source,target,weight' but found '${header.fields.joinToString(",")}'")
        }
        if (rows.size == 1) fail("File contains a header but no edges")
        val graph = Graph()
        val errors = ArrayList<String>()
        for (row in rows.drop(1)) {
            val f = row.fields
            if (f.size != 3) {
                errors.add("Line ${row.line}: expected 3 fields (source,target,weight) but found ${f.size}")
                continue
            }
            val (source, target, rawWeight) = f
            if (source.isEmpty() || target.isEmpty()) {
                errors.add("Line ${row.line}: source and target must not be empty")
                continue
            }
            val weight = rawWeight.toDoubleOrNull()
            if (weight == null || !weight.isFinite()) {
                errors.add("Line ${row.line}: weight '$rawWeight' is not a finite number")
                continue
            }
            if (weight <= 0.0) {
                errors.add("Line ${row.line}: weight must be positive (got $rawWeight)")
                continue
            }
            try {
                graph.addEdge(graph.getOrAddNode(source).id, graph.getOrAddNode(target).id, weight)
            } catch (e: IllegalArgumentException) {
                errors.add("Line ${row.line}: ${e.message}")
            }
        }
        failIfAny(errors)
        return graph
    }

    fun graphFromJson(text: String): Graph {
        val root = parseJson(text)
        if (!root.isObject) fail("The top-level JSON value must be an object with an 'edges' array (and optional 'nodes')")
        val directed = root.get("directed")
        if (directed != null && !(directed.isBoolean && !directed.asBoolean())) {
            fail("Only undirected graphs are supported: 'directed' must be false or omitted")
        }
        val edgesNode = root.get("edges") ?: fail("Missing required field 'edges'")
        if (!edgesNode.isArray) fail("'edges' must be an array")
        val graph = Graph()
        val errors = ArrayList<String>()
        val nodesNode = root.get("nodes")
        val explicitNodes = nodesNode != null
        if (nodesNode != null) {
            if (!nodesNode.isArray) fail("'nodes' must be an array")
            for ((i, n) in nodesNode.withIndex()) {
                val where = "nodes[$i]"
                val id = idOf(if (n.isObject) n.get("id") else n)
                if (id == null) {
                    errors.add("$where: missing or invalid 'id' (use a non-empty string or an integer)")
                    continue
                }
                var coord: Coord? = null
                if (n.isObject && (n.has("x") || n.has("y"))) {
                    val x = n.get("x")
                    val y = n.get("y")
                    if (x == null || y == null || !x.isNumber || !y.isNumber) {
                        errors.add("$where: 'x' and 'y' must both be numbers")
                        continue
                    }
                    coord = Coord(x.asDouble(), y.asDouble())
                }
                if (graph.nodeByLabel(id) != null) errors.add("$where: duplicate node id '$id'") else graph.addNode(id, coord)
            }
        }
        for ((i, e) in edgesNode.withIndex()) {
            val where = "edges[$i]"
            if (!e.isObject) {
                errors.add("$where: must be an object with source, target and weight")
                continue
            }
            val source = idOf(e.get("source"))
            val target = idOf(e.get("target"))
            val w = e.get("weight")
            if (source == null || target == null) {
                errors.add("$where: 'source' and 'target' are required (string or integer)")
                continue
            }
            if (w == null || !w.isNumber) {
                errors.add("$where: 'weight' is required and must be a number")
                continue
            }
            val weight = w.asDouble()
            if (!weight.isFinite() || weight <= 0.0) {
                errors.add("$where: weight must be a positive finite number (got ${w.asText()})")
                continue
            }
            val a = if (explicitNodes) graph.nodeByLabel(source) else graph.getOrAddNode(source)
            val b = if (explicitNodes) graph.nodeByLabel(target) else graph.getOrAddNode(target)
            if (a == null || b == null) {
                errors.add("$where: references unknown node '${if (a == null) source else target}'")
                continue
            }
            try {
                graph.addEdge(a.id, b.id, weight)
            } catch (ex: IllegalArgumentException) {
                errors.add("$where: ${ex.message}")
            }
        }
        failIfAny(errors)
        if (graph.nodeCount == 0) fail("The graph contains no nodes or edges")
        return graph
    }

    fun arrayFromCsv(text: String): IntArray {
        val rows = csvRows(text)
        if (rows.isEmpty()) fail("File is empty")
        val first = rows[0].fields
        val dataRows = if (first.size == 1 && first[0].equals("value", ignoreCase = true)) rows.drop(1) else rows
        val values = ArrayList<Int>()
        val errors = ArrayList<String>()
        for (row in dataRows) {
            for ((col, token) in row.fields.withIndex()) {
                if (token.isEmpty()) {
                    errors.add("Line ${row.line}, field ${col + 1}: value is missing")
                    continue
                }
                val v = token.toIntOrNull()
                if (v == null) errors.add("Line ${row.line}: '$token' is not a valid integer") else values.add(v)
            }
        }
        failIfAny(errors)
        if (values.isEmpty()) fail("File contains a header but no values")
        return values.toIntArray()
    }

    fun arrayFromJson(text: String): IntArray {
        val root = parseJson(text)
        val values: JsonNode = when {
            root.isArray -> root
            root.isObject && root.get("values")?.isArray == true -> root.get("values")
            else -> fail("Expected a JSON array of integers or an object like {\"values\": [1, 2, 3]}")
        }
        if (values.size() == 0) fail("The array is empty")
        val out = IntArray(values.size())
        val errors = ArrayList<String>()
        for ((i, v) in values.withIndex()) {
            if (v.isIntegralNumber && v.canConvertToInt()) out[i] = v.asInt()
            else errors.add("values[$i]: '${v.asText()}' is not an integer in the 32-bit range")
        }
        failIfAny(errors)
        return out
    }

    // ---- helpers ----

    private class CsvRow(val line: Int, val fields: List<String>)

    private fun csvRows(text: String): List<CsvRow> {
        val rows = ArrayList<CsvRow>()
        for ((i, raw) in text.lineSequence().withIndex()) {
            if (raw.isNotBlank()) rows.add(CsvRow(i + 1, splitCsvLine(raw, i + 1)))
        }
        return rows
    }

    private fun splitCsvLine(line: String, lineNumber: Int): List<String> {
        val out = ArrayList<String>()
        val sb = StringBuilder()
        var inQuotes = false
        var i = 0
        while (i < line.length) {
            val c = line[i]
            if (inQuotes && c == '"' && i + 1 < line.length && line[i + 1] == '"') {
                sb.append('"')
                i++
            } else if (c == '"') {
                inQuotes = !inQuotes
            } else if (c == ',' && !inQuotes) {
                out.add(sb.toString().trim())
                sb.setLength(0)
            } else {
                sb.append(c)
            }
            i++
        }
        if (inQuotes) fail("Line $lineNumber: unterminated quoted field")
        out.add(sb.toString().trim())
        return out
    }

    private fun parseJson(text: String): JsonNode {
        if (text.isBlank()) fail("File is empty")
        return try {
            mapper.readTree(text)
        } catch (e: JsonProcessingException) {
            fail("Malformed JSON at line ${e.location?.lineNr}: ${e.originalMessage}")
        }
    }

    private fun idOf(n: JsonNode?): String? = when {
        n == null -> null
        n.isTextual -> n.asText().trim().takeIf { it.isNotEmpty() }
        n.isIntegralNumber -> n.asText()
        else -> null
    }

    private fun read(path: Path): String {
        if (!Files.isRegularFile(path)) fail("File not found: $path")
        return try {
            Files.readString(path).removePrefix("\uFEFF")
        } catch (e: CharacterCodingException) {
            fail("File is not valid UTF-8 text: $path")
        } catch (e: IOException) {
            fail("Cannot read file: ${e.message}")
        }
    }

    private fun extension(path: Path): String = path.fileName.toString().substringAfterLast('.', "").lowercase()

    private fun unsupported(path: Path): Nothing =
        fail("Unsupported file type '${path.fileName}': use a .csv or .json file")

    private fun fail(message: String): Nothing = throw DatasetException(listOf(message))

    private fun failIfAny(errors: List<String>) {
        if (errors.isEmpty()) return
        val shown = errors.take(MAX_REPORTED).toMutableList()
        if (errors.size > MAX_REPORTED) shown.add("... and ${errors.size - MAX_REPORTED} more problem(s)")
        throw DatasetException(shown)
    }
}

/** Short human-readable previews shown after a successful import. */
object DatasetPreview {
    fun graph(g: Graph, limit: Int = 50): String = buildString {
        appendLine("Nodes: ${g.nodeCount}   Edges: ${g.edgeCount}   Components: ${g.componentCount()}")
        appendLine(
            "A* heuristic: " + if (g.hasConsistentCoordinates()) "Euclidean (coordinates present and consistent)"
            else "zero heuristic (coordinates absent or inconsistent with weights)"
        )
        appendLine()
        appendLine("First ${minOf(limit, g.edgeCount)} of ${g.edgeCount} edges (undirected):")
        for (e in g.edges.take(limit)) {
            appendLine("  ${g.labelOf(e.a)} - ${g.labelOf(e.b)}   weight ${formatNumber(e.weight)}")
        }
    }

    fun array(a: IntArray, limit: Int = 100): String = buildString {
        appendLine("Elements: ${a.size}   Min: ${a.min()}   Max: ${a.max()}")
        appendLine("Already sorted ascending: " + if (a.indices.all { it == 0 || a[it - 1] <= a[it] }) "yes" else "no")
        appendLine()
        appendLine("First ${minOf(limit, a.size)} of ${a.size} values:")
        appendLine(a.take(limit).joinToString(", "))
    }
}
