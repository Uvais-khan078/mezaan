package com.example.meezan.domain.parser

/**
 * Parsed roadmap models used by Goal Planner feature.
 */
data class ParsedRoadmap(
	val goalTitle: String,
	val entries: List<ParsedRoadmapEntry>
)

data class ParsedRoadmapEntry(
	val dayNumber: Int,
	val taskName: String,
	val durationMinutes: Int
)

class RoadmapParseException(message: String) : Exception(message)

object RoadmapParser {
	private val goalRegex = Regex("^Goal\\s*:\\s*(.+)$", RegexOption.IGNORE_CASE)
	private val dayRegex = Regex("^Day\\s+(\\d+)\\s*$", RegexOption.IGNORE_CASE)
	private val taskRegex = Regex("^Task\\s*:\\s*(.+)$", RegexOption.IGNORE_CASE)
	private val durationRegex = Regex("^Duration\\s*:\\s*(.+)$", RegexOption.IGNORE_CASE)

	fun parse(input: String): ParsedRoadmap {
		val rawLines = input.lines()
		val lines = rawLines
			.mapIndexed { index, text -> index to text.trim() }
			.filter { it.second.isNotEmpty() }

		if (lines.isEmpty()) {
			throw RoadmapParseException("Roadmap is empty")
		}

		val (firstLineIdx, firstLineText) = lines.first()
		val goalMatch = goalRegex.find(firstLineText)
			?: throw RoadmapParseException("Line ${firstLineIdx + 1}: First non-empty line must be in format: Goal: <title>")

		val goalTitle = goalMatch.groupValues[1].trim()
		if (goalTitle.isBlank()) {
			throw RoadmapParseException("Line ${firstLineIdx + 1}: Goal title cannot be blank")
		}

		val entries = mutableListOf<ParsedRoadmapEntry>()
		var i = 1
		while (i < lines.size) {
			val (dayIdx, dayLine) = lines[i]
			val dayMatch = dayRegex.find(dayLine)
				?: throw RoadmapParseException("Line ${dayIdx + 1}: Expected 'Day N'")
			val dayNumber = dayMatch.groupValues[1].toInt()

			val (taskIdx, taskLine) = lines.getOrNull(i + 1)
				?: throw RoadmapParseException("Line ${dayIdx + 1}: Missing task line after Day $dayNumber")
			val taskMatch = taskRegex.find(taskLine)
				?: throw RoadmapParseException("Line ${taskIdx + 1}: Expected 'Task: ...' after Day $dayNumber")
			val taskName = taskMatch.groupValues[1].trim()
			if (taskName.isBlank()) {
				throw RoadmapParseException("Line ${taskIdx + 1}: Task name cannot be blank for Day $dayNumber")
			}

			val (durationIdx, durationLine) = lines.getOrNull(i + 2)
				?: throw RoadmapParseException("Line ${taskIdx + 1}: Missing duration line for Day $dayNumber")
			val durationMatch = durationRegex.find(durationLine)
				?: throw RoadmapParseException("Line ${durationIdx + 1}: Expected 'Duration: ...' for Day $dayNumber")
			val durationValue = durationMatch.groupValues[1].trim()
			
			val durationMinutes = try {
				parseDurationToMinutes(durationValue)
			} catch (e: RoadmapParseException) {
				throw RoadmapParseException("Line ${durationIdx + 1}: ${e.message}")
			}

			if (entries.any { it.dayNumber == dayNumber }) {
				throw RoadmapParseException("Line ${dayIdx + 1}: Duplicate Day $dayNumber entry")
			}

			entries.add(
				ParsedRoadmapEntry(
					dayNumber = dayNumber,
					taskName = taskName,
					durationMinutes = durationMinutes
				)
			)
			i += 3
		}

		if (entries.isEmpty()) {
			throw RoadmapParseException("At least one day block is required")
		}

		return ParsedRoadmap(goalTitle = goalTitle, entries = entries)
	}

	internal fun parseDurationToMinutes(raw: String): Int {
		val value = raw.lowercase()
		val pattern = Regex("^(\\d+(?:\\.\\d+)?)\\s*(hour|hours|hr|hrs|h|minute|minutes|min|m)s?$")
		val match = pattern.find(value)
			?: throw RoadmapParseException("Invalid duration '$raw'. Use e.g. '1.5 Hours', '90 Minutes', '45 min'")

		val amount = match.groupValues[1].toDouble()
		val unit = match.groupValues[2]

		return when (unit) {
			"hour", "hours", "hr", "hrs", "h" -> Math.round(amount * 60).toInt()
			"minute", "minutes", "min", "m" -> Math.round(amount).toInt()
			else -> throw RoadmapParseException("Unsupported duration unit in '$raw'")
		}
	}
}



