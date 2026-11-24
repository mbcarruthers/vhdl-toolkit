package dev.vhdltoolkit.project

import org.w3c.dom.Document
import org.w3c.dom.Element
import org.w3c.dom.Node
import java.io.InputStream
import javax.xml.XMLConstants
import javax.xml.parsers.DocumentBuilderFactory

/**
 * XML loading shared by the project importers.
 *
 * Project files are untrusted input — they arrive from a colleague's checkout
 * or a vendor tool — so external entities and DTDs are switched off to avoid
 * XXE and entity-expansion attacks.
 */
internal object XmlDocuments {

    fun parse(input: InputStream): Document {
        val factory = DocumentBuilderFactory.newInstance().apply {
            setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
            setFeature("http://xml.org/sax/features/external-general-entities", false)
            setFeature("http://xml.org/sax/features/external-parameter-entities", false)
            setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false)
            isXIncludeAware = false
            isExpandEntityReferences = false
            isNamespaceAware = false
            runCatching { setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "") }
            runCatching { setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "") }
        }
        return input.use { factory.newDocumentBuilder().parse(it) }
    }

    /**
     * All elements with the given tag name, ignoring any namespace prefix.
     * Vendor tools are inconsistent about prefixes, so matching on the local
     * name keeps the importers from breaking on a schema revision.
     */
    fun elementsNamed(root: Node, localName: String): List<Element> {
        val out = ArrayList<Element>()
        fun walk(node: Node) {
            if (node is Element && node.localNameOrTag().equals(localName, ignoreCase = true)) {
                out.add(node)
            }
            val children = node.childNodes
            for (i in 0 until children.length) walk(children.item(i))
        }
        walk(root)
        return out
    }

    /** Nearest ancestor with the given tag name, or null. */
    fun ancestorNamed(node: Node, localName: String): Element? {
        var current: Node? = node.parentNode
        while (current != null) {
            if (current is Element && current.localNameOrTag().equals(localName, ignoreCase = true)) {
                return current
            }
            current = current.parentNode
        }
        return null
    }

    fun Element.attr(name: String): String? =
        getAttribute(name).takeIf { it.isNotEmpty() }

    private fun Element.localNameOrTag(): String = localName ?: tagName.substringAfter(':')
}
