package dev.junta.firmamobile.ui

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Source-only guards; Compose behavior remains covered by CompatibilityFeedbackPaneTest. */
class LegalInformationDialogStructureTest {
    private val source = listOf(
        File("src/main/java/dev/junta/firmamobile/ui/LegalInformationDialog.kt"),
        File("app/src/main/java/dev/junta/firmamobile/ui/LegalInformationDialog.kt"),
    ).first { it.isFile }.readText()
    private val dialog = body("internal fun LegalInformationDialog(onDismiss: () -> Unit)")

    @Test
    fun extractionKeepsEveryStateOwnerInTheDialogAndItsOriginalBranch() {
        for ((call, expected) in listOf(
            "\\bremember\\s*(?:\\(|\\{)" to 4,
            "\\bproduceState\\s*<" to 2,
            "\\bmutableStateOf\\s*(?:<|\\()" to 2,
            "\\brememberScrollState\\s*\\(" to 1,
        )) {
            assertEquals(call, expected, Regex(call).findAll(dialog).count())
            assertEquals(call, expected, Regex(call).findAll(source).count())
        }
        assertInOrder(dialog,
            "var section by remember { mutableStateOf(\"menu\") }",
            "var document by remember { mutableStateOf<LegalDocument?>(null) }",
            "val title = legalDialogTitle(document, section)",
            "Dialog(onDismissRequest = { back() }",
            "selected != null -> {",
            "produceState<String?>(null, selected.asset, loadError)",
            "remember(content) { content?.let(::legalTextChunks).orEmpty() }",
            "section == \"feedback\" -> {",
            "val report = remember { currentCompatibilityFeedbackReport() }",
            "section == \"licenses\" -> {",
            "produceState<List<LegalDocument>?>(null)",
            "Column(Modifier.weight(1f).verticalScroll(rememberScrollState()))",
            "legalMenuItems(",
        )
        val helpers = listOf("legalDialogTitle", "legalDialogHeader", "legalDocumentContent", "legalLicenseList", "legalMenuItems")
        for (name in helpers) {
            assertTrue(name, Regex("private fun (?:ColumnScope\\.)?$name\\(").containsMatchIn(source))
            val helper = body("fun " + if (name in listOf("legalDocumentContent", "legalLicenseList")) {
                "ColumnScope.$name("
            } else {
                "$name("
            })
            assertFalse(name, Regex("\\b(?:remember|rememberScrollState|produceState|mutableStateOf)\\b")
                .containsMatchIn(helper))
        }
    }

    @Test
    fun headerAndMenuKeepNavigationCallbacksResourcesAndOrder() {
        assertInOrder(body("fun back()"),
            "document != null -> document = null",
            "section != \"menu\" -> section = \"menu\"",
            "else -> onDismiss()",
        )
        assertTrue(dialog.contains("BackHandler { back() }"))
        assertTrue(dialog.contains("DialogProperties(usePlatformDefaultWidth = false)"))
        assertTrue(dialog.contains("legalDialogHeader(title, onBack = { back() }, onDismiss = onDismiss)"))
        assertInOrder(body("fun legalDialogTitle("),
            "\"licenses\" -> stringResource(R.string.legal_licenses)",
            "\"feedback\" -> stringResource(R.string.compatibility_feedback_title)",
            "else -> stringResource(R.string.legal_information)",
        )
        assertInOrder(body("fun legalDialogHeader("),
            "TextButton(onClick = onBack", "R.string.legal_back",
            "TextButton(onClick = onDismiss", "R.string.close",
            "Text(title, style = MaterialTheme.typography.titleLarge, maxLines = 2, overflow = TextOverflow.Ellipsis",
            ".semantics { heading() }", "HorizontalDivider()",
        )
        assertInOrder(dialog,
            "onPrivacy = { document = LegalDocument(privacyTitle, \"legal/privacy.txt\") }",
            "onLicenses = { section = \"licenses\" }",
            "onAbout = { document = LegalDocument(aboutTitle, \"legal/about.txt\") }",
            "onFeedback = { section = \"feedback\" }",
        )
        assertInOrder(body("fun legalMenuItems("),
            "TextButton(onClick = onPrivacy", "R.string.legal_privacy",
            "TextButton(onClick = onLicenses", "R.string.legal_licenses",
            "TextButton(onClick = onAbout", "R.string.legal_about",
            "onClick = onFeedback", ".testTag(\"legal-compatibility-feedback\")",
            "R.string.compatibility_feedback_title",
        )
    }

    @Test
    fun assetLoadingAndStatelessListsKeepBoundsFailuresKeysAndPresentation() {
        assertInOrder(dialog,
            "produceState<String?>(null, selected.asset, loadError)",
            "value = withContext(Dispatchers.IO)",
            "readLegalDocument(context, selected.asset, loadError)",
            "legalDocumentContent(content, chunks)",
            "produceState<List<LegalDocument>?>(null)",
            "value = withContext(Dispatchers.IO)",
            "readLegalDocuments(context)",
            "legalLicenseList(documents, onSelect = { document = it })",
        )
        assertInOrder(source.substringAfter("private fun readLegalDocument("),
            "runCatching {", "require(validLegalAsset(asset))",
            "context.assets.open(asset).bufferedReader(Charsets.UTF_8).use",
            "require(text.length <= 2_000_000)", ".getOrElse { loadError }",
        )
        assertInOrder(source.substringAfter("private fun readLegalDocuments("),
            "runCatching {", "context.assets.open(\"legal/documents.json\").bufferedReader().use",
            "JSONArray(data)", "List(array.length())", "item.getString(\"title\")",
            "item.getString(\"asset\")", "require(validLegalAsset(it.asset))", ".getOrElse { emptyList() }",
        )
        assertInOrder(body("fun ColumnScope.legalDocumentContent("),
            "if (content == null) CircularProgressIndicator(Modifier.padding(16.dp))",
            "LazyColumn(Modifier.weight(1f).testTag(\"legal-document-text\")",
            "contentPadding = PaddingValues(vertical = 12.dp)",
            "verticalArrangement = Arrangement.spacedBy(12.dp)",
            "items(chunks.size) { index -> Text(chunks[index], style = MaterialTheme.typography.bodyMedium) }",
        )
        assertInOrder(body("fun ColumnScope.legalLicenseList("),
            "if (list == null) CircularProgressIndicator(Modifier.padding(16.dp))",
            "else if (list.isEmpty()) Text(stringResource(R.string.legal_load_error))",
            "else LazyColumn(Modifier.weight(1f))", "items(list, key = { it.asset })",
            "TextButton(onClick = { onSelect(item) }",
            "Modifier.fillMaxWidth().heightIn(min = 48.dp)",
            "Text(item.title, modifier = Modifier.fillMaxWidth(), maxLines = 3, overflow = TextOverflow.Ellipsis)",
        )
    }

    @Test
    fun feedbackSnapshotAndPrivateActionCallbacksStayInsideTheExistingPaneBranch() {
        val feedback = dialog.substringAfter("section == \"feedback\" -> {")
            .substringBefore("section == \"licenses\" -> {").trim().removeSuffix("}").trim()
        val expected = """
            val report = remember { currentCompatibilityFeedbackReport() }
            compatibilityFeedbackPane(
                report = report,
                onCopy = { copyCompatibilityFeedback(context, it) },
                onOpenIssue = { openCompatibilityFeedback(context, it) },
                modifier = Modifier.weight(1f),
            )
        """.trimIndent()
        assertEquals(expected.filterNot(Char::isWhitespace), feedback.filterNot(Char::isWhitespace))
        for (call in listOf("currentCompatibilityFeedbackReport()", "copyCompatibilityFeedback(context, it)",
            "openCompatibilityFeedback(context, it)")) {
            assertEquals(call, 1, source.windowed(call.length).count { it == call })
        }
    }

    private fun body(signature: String): String {
        val declaration = source.indexOf(signature)
        assertTrue("Missing declaration: $signature", declaration >= 0)
        val opening = source.indexOf('{', declaration + signature.length)
        var depth = 1
        var index = opening + 1
        while (index < source.length && depth > 0) {
            when (source[index]) {
                '{' -> depth++
                '}' -> depth--
            }
            index++
        }
        assertEquals("Unbalanced declaration: $signature", 0, depth)
        return source.substring(opening + 1, index - 1)
    }

    private fun assertInOrder(text: String, vararg fragments: String) {
        var after = 0
        for (fragment in fragments) {
            val index = text.indexOf(fragment, after)
            assertTrue("Missing or reordered: $fragment", index >= 0)
            after = index + fragment.length
        }
    }
}
