package com.cloudyte.app.data

import com.cloudyte.app.db.ExclusionRuleType
import com.cloudyte.app.db.LocalExclusion
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EvaluateExclusionsTest {

    @Test
    fun testGlobMatching() {
        val rules = listOf(
            LocalExclusion(id = 1, pattern = "*.tmp", isRegex = false, enabled = true),
            LocalExclusion(id = 2, pattern = ".DS_Store", isRegex = false, enabled = true),
            LocalExclusion(id = 3, pattern = "cache/*", isRegex = false, enabled = true)
        )

        assertTrue(EvaluateExclusions.isExcluded("temp_file.tmp", rules))
        assertTrue(EvaluateExclusions.isExcluded("/folder/path/.DS_Store", rules))
        assertTrue(EvaluateExclusions.isExcluded("cache/data.bin", rules))

        assertFalse(EvaluateExclusions.isExcluded("document.pdf", rules))
        assertFalse(EvaluateExclusions.isExcluded("image.png", rules))
    }

    @Test
    fun testRegexMatching() {
        val rules = listOf(
            LocalExclusion(id = 1, pattern = ".*_backup_\\d+\\.zip", isRegex = true, enabled = true)
        )

        assertTrue(EvaluateExclusions.isExcluded("my_backup_2026.zip", rules))
        assertFalse(EvaluateExclusions.isExcluded("my_backup_abc.zip", rules))
    }

    @Test
    fun testDisabledRuleIgnored() {
        val rules = listOf(
            LocalExclusion(id = 1, pattern = "*.tmp", isRegex = false, enabled = false)
        )

        assertFalse(EvaluateExclusions.isExcluded("temp_file.tmp", rules))
    }

    @Test
    fun testAllSevenTypedExclusionRules() {
        val folderRule = LocalExclusion(id = 1, pattern = "WhatsApp/Trash", ruleType = ExclusionRuleType.FOLDER_PATH.name, sourceUri = "content://tree/trash")
        val fileRule = LocalExclusion(id = 2, pattern = "Secrets/keys.kdbx", ruleType = ExclusionRuleType.FILE_PATH.name, sourceUri = "content://tree/doc/99")
        val hiddenRule = LocalExclusion(id = 3, pattern = ".*", ruleType = ExclusionRuleType.HIDDEN.name)
        val extRule = LocalExclusion(id = 4, pattern = "log, bak", ruleType = ExclusionRuleType.EXTENSION.name)
        val mimeRule = LocalExclusion(id = 5, pattern = "video/*", ruleType = ExclusionRuleType.MIME_TYPE.name)
        val sizeRule = LocalExclusion(id = 6, pattern = "5 MB", ruleType = ExclusionRuleType.MAX_SIZE_BYTES.name, sizeLimitBytes = 5L * 1024L * 1024L)
        val rules = listOf(folderRule, fileRule, hiddenRule, extRule, mimeRule, sizeRule)

        assertTrue(
            EvaluateExclusions.evaluate(
                ExclusionCandidate("trash", "WhatsApp/Trash", isDirectory = true),
                rules
            ).excluded
        )
        assertTrue(
            EvaluateExclusions.evaluate(
                ExclusionCandidate("keys.kdbx", "Secrets/keys.kdbx", fileUri = "content://tree/doc/99"),
                rules
            ).excluded
        )
        assertTrue(
            EvaluateExclusions.evaluate(
                ExclusionCandidate(".nomedia", "Camera/.nomedia"),
                rules
            ).excluded
        )
        assertTrue(
            EvaluateExclusions.evaluate(
                ExclusionCandidate("debug.LOG", "logs/debug.LOG"),
                rules
            ).excluded
        )
        assertTrue(
            EvaluateExclusions.evaluate(
                ExclusionCandidate("clip.mp4", "Movies/clip.mp4", mimeType = "video/mp4"),
                rules
            ).excluded
        )
        assertTrue(
            EvaluateExclusions.evaluate(
                ExclusionCandidate("large.bin", "Downloads/large.bin", sizeBytes = 6L * 1024L * 1024L),
                rules
            ).excluded
        )
        assertFalse(
            EvaluateExclusions.evaluate(
                ExclusionCandidate("photo.jpg", "Camera/photo.jpg", mimeType = "image/jpeg", sizeBytes = 2L * 1024L * 1024L),
                rules
            ).excluded
        )
    }

    @Test
    fun testGlobalMaxSizeLimitOverridesBeforeRules() {
        val result = EvaluateExclusions.evaluate(
            candidate = ExclusionCandidate("video.mov", "Camera/video.mov", sizeBytes = 150L * 1024L * 1024L),
            exclusions = emptyList(),
            globalMaxFileSizeBytes = 100L * 1024L * 1024L
        )
        assertTrue(result.excluded)
        assertEquals(ExclusionSkipReason.GLOBAL_MAX_SIZE, result.reason)
    }

    @Test
    fun testValidationRejectsInvalidInputsAndExplainsUnits() {
        val invalidExt = EvaluateExclusions.validateRuleInput(ExclusionRuleType.EXTENSION, "invalid ext!", false)
        assertTrue(invalidExt is ExclusionValidationResult.Invalid)

        val invalidMime = EvaluateExclusions.validateRuleInput(ExclusionRuleType.MIME_TYPE, "video", false)
        assertTrue(invalidMime is ExclusionValidationResult.Invalid)

        val invalidRegex = EvaluateExclusions.validateRuleInput(ExclusionRuleType.PATH_PATTERN, "[unclosed(", true)
        assertTrue(invalidRegex is ExclusionValidationResult.Invalid)

        val invalidSize = EvaluateExclusions.validateRuleInput(ExclusionRuleType.MAX_SIZE_BYTES, "-5", false, sizeValueText = "-5", sizeUnit = ExclusionSizeUnit.MB)
        assertTrue(invalidSize is ExclusionValidationResult.Invalid)
        assertTrue((invalidSize as ExclusionValidationResult.Invalid).message.contains("MB"))

        val validSize = EvaluateExclusions.validateRuleInput(ExclusionRuleType.MAX_SIZE_BYTES, "", false, sizeValueText = "2", sizeUnit = ExclusionSizeUnit.GB)
        assertTrue(validSize is ExclusionValidationResult.Valid)
        assertEquals(2L * 1024L * 1024L * 1024L, (validSize as ExclusionValidationResult.Valid).sizeLimitBytes)
    }
}
