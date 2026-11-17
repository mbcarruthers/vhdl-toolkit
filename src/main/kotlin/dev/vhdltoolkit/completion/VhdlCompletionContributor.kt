package dev.vhdltoolkit.completion

import com.intellij.codeInsight.completion.CompletionContributor
import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.codeInsight.completion.CompletionType
import com.intellij.codeInsight.completion.InsertHandler
import com.intellij.codeInsight.completion.InsertionContext
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.openapi.util.TextRange
import com.intellij.patterns.PlatformPatterns
import dev.vhdltoolkit.VhdlLanguage

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

        for (suggestion in VhdlKeywordCompletion.suggestionsFor(text, offset)) {
            result.addElement(toLookupElement(suggestion))
        }
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
        }
        return element
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
