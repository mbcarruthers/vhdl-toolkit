package dev.vhdltoolkit.completion

import com.intellij.codeInsight.completion.CompletionContributor
import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.codeInsight.completion.CompletionType
import com.intellij.codeInsight.completion.InsertHandler
import com.intellij.codeInsight.completion.InsertionContext
import com.intellij.codeInsight.completion.PrioritizedLookupElement
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.openapi.util.TextRange
import com.intellij.patterns.PlatformPatterns
import dev.vhdltoolkit.VhdlLanguage
import dev.vhdltoolkit.editing.VhdlLineReindenter

/**
 * Completes VHDL keywords, standard types and block skeletons.
 *
 * The language server contributes names it has seen; this contributes the
 * language. Both appear in the same popup, which is what you want: `clk` and
 * `case` are equally likely next words.
 *
 * Suggestions are filtered by region, so a declarative part offers `signal`
 * and `type` while a process body offers `if` and `wait`. Offering all one
 * hundred reserved words everywhere would be worse than offering none.
 */
class VhdlCompletionContributor : CompletionContributor() {

    init {
        extend(
            CompletionType.BASIC,
            PlatformPatterns.psiElement().withLanguage(VhdlLanguage),
            VhdlKeywordProvider(),
        )
    }
}

private class VhdlKeywordProvider : com.intellij.codeInsight.completion.CompletionProvider<CompletionParameters>() {

    override fun addCompletions(
        parameters: CompletionParameters,
        context: com.intellij.util.ProcessingContext,
        result: CompletionResultSet,
    ) {
        val text = parameters.originalFile.text
        val offset = parameters.offset.coerceIn(0, text.length)
        val prefix = result.prefixMatcher.prefix.lowercase()

        for (suggestion in VhdlKeywordCompletion.suggestionsFor(text, offset)) {
            result.addElement(prioritise(toLookupElement(suggestion), suggestion, prefix))
        }
    }

    /**
     * Ranks a suggestion against whatever the language server is also
     * offering.
     *
     * The server completes names from across the project and matches them
     * loosely, so typing `is` can surface `rising_edge` on the strength of the
     * letters buried in the middle of it. A reserved word the author has begun
     * spelling out is almost always what they meant, so it is lifted above
     * such matches rather than filtered out of them — the server's list is not
     * ours to censor, and sometimes it is right.
     *
     * Skeletons sit just below the bare keyword they share a name with, so
     * `process` followed by Enter still types the word.
     */
    private fun prioritise(
        element: LookupElement,
        suggestion: VhdlKeywordCompletion.Suggestion,
        prefix: String,
    ): LookupElement {
        val word = suggestion.text.lowercase()
        val base = when {
            prefix.isEmpty() -> 0.0
            word == prefix -> 100.0
            word.startsWith(prefix) -> 50.0
            else -> 0.0
        }
        val priority = if (suggestion.template != null) base - 1.0 else base
        if (priority == 0.0) return element
        return PrioritizedLookupElement.withPriority(element, priority)
    }

    private fun toLookupElement(suggestion: VhdlKeywordCompletion.Suggestion): LookupElement {
        var element = LookupElementBuilder.create(suggestion.text)
            .withCaseSensitivity(false)
            .withBoldness(suggestion.template == null)

        suggestion.description?.let { element = element.withTypeText(it, true) }

        val template = suggestion.template
        if (template != null) {
            element = element
                .withPresentableText(suggestion.text)
                .withTailText("  …", true)
                .withInsertHandler(TemplateInsertHandler(template))
        } else if (suggestion.text.lowercase() in VhdlLineReindenter.CLOSING_WORDS) {
            element = element.withInsertHandler(ReindentInsertHandler)
        }
        return element
    }
}

/**
 * Pulls a closing keyword back to its construct's column once it is inserted.
 *
 * Typing `else` is handled as the characters arrive, but accepting it from
 * the popup inserts the word whole and the typed handler never sees it. The
 * line then stayed where it was, which read as the indentation being broken
 * rather than as one path through it being unhandled — and the keyword
 * completion that introduced the second path is the thing that made it
 * reachable.
 */
private object ReindentInsertHandler : InsertHandler<LookupElement> {

    override fun handleInsert(context: InsertionContext, item: LookupElement) {
        context.commitDocument()
        val document = context.document
        val line = document.getLineNumber(context.tailOffset.coerceIn(0, document.textLength))
        VhdlLineReindenter.reindentIfClosing(context.project, document, line)
    }
}

/**
 * Expands a skeleton, matching the surrounding indentation and leaving the
 * caret where work continues.
 *
 * Placeholders are written `$NAME$`; `$CURSOR$` marks where the caret lands.
 * They are stripped rather than turned into editable template fields — a live
 * template would be better, but this works without a parser and without
 * fighting the platform's template engine.
 */
private class TemplateInsertHandler(private val template: String) : InsertHandler<LookupElement> {

    override fun handleInsert(context: InsertionContext, item: LookupElement) {
        val document = context.document
        val startOffset = context.startOffset
        val lineStart = document.getLineStartOffset(document.getLineNumber(startOffset))
        val existingIndent = document.getText(TextRange(lineStart, startOffset)).takeWhile { it == ' ' || it == '\t' }

        val cursorMarker = "\$CURSOR$"
        val cleaned = template.replace(Regex("""\$[A-Z_]+\$""") ) { match ->
            if (match.value == cursorMarker) cursorMarker else match.value.trim('$').lowercase()
        }

        val indented = cleaned.lineSequence()
            .mapIndexed { index, line -> if (index == 0 || line.isEmpty()) line else existingIndent + line }
            .joinToString("\n")

        val caretInTemplate = indented.indexOf(cursorMarker)
        val finalText = indented.replace(cursorMarker, "")

        document.replaceString(context.startOffset, context.tailOffset, finalText)
        context.commitDocument()

        val caret = if (caretInTemplate >= 0) {
            context.startOffset + caretInTemplate
        } else {
            context.startOffset + finalText.length
        }
        context.editor.caretModel.moveToOffset(caret.coerceIn(0, document.textLength))
    }
}
