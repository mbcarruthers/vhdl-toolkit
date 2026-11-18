package dev.vhdltoolkit.navigation

import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiManager
import com.intellij.psi.search.FileTypeIndex
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import com.intellij.psi.util.PsiModificationTracker
import com.intellij.openapi.vfs.VirtualFile
import dev.vhdltoolkit.VhdlFileType
import dev.vhdltoolkit.project.VhdlDesignUnits

/**
 * Where every design unit in the project lives.
 *
 * An entity and the architecture that implements it are usually in different
 * files, so linking them means knowing about files other than the one being
 * edited. There is no PSI structure to index — the tree is flat — so this
 * scans each VHDL file's text with [VhdlDesignUnits] instead.
 *
 * The result is cached against the platform's modification tracker, so it is
 * rebuilt when something changes and reused otherwise. Scanning every VHDL
 * file is affordable precisely because it is a tokenizer pass and nothing
 * more.
 */
object VhdlDesignUnitIndex {

    data class Entry(val file: VirtualFile, val unit: VhdlDesignUnits.Unit)

    /** Every unit in the project, keyed by lower-cased name. */
    fun all(project: Project): Map<String, List<Entry>> =
        CachedValuesManager.getManager(project).getCachedValue(project) {
            CachedValueProvider.Result.create(
                build(project),
                PsiModificationTracker.MODIFICATION_COUNT,
            )
        }

    fun named(project: Project, name: String): List<Entry> =
        all(project)[name.lowercase()].orEmpty()

    /** The units in one file, cached on that file. */
    fun inFile(file: com.intellij.psi.PsiFile): List<VhdlDesignUnits.Unit> =
        CachedValuesManager.getCachedValue(file) {
            CachedValueProvider.Result.create(
                VhdlDesignUnits.scan(file.text),
                file,
            )
        }

    /** The leaf at [offset] in [file], which is what a gutter icon anchors to. */
    fun leafAt(project: Project, file: VirtualFile, offset: Int): PsiElement? {
        val psi = PsiManager.getInstance(project).findFile(file) ?: return null
        return psi.findElementAt(offset)
    }

    private fun build(project: Project): Map<String, List<Entry>> {
        val out = HashMap<String, MutableList<Entry>>()
        val scope = GlobalSearchScope.projectScope(project)
        val manager = PsiManager.getInstance(project)

        for (virtualFile in FileTypeIndex.getFiles(VhdlFileType, scope)) {
            val psi = manager.findFile(virtualFile) ?: continue
            for (unit in VhdlDesignUnits.scan(psi.text)) {
                out.getOrPut(unit.name) { ArrayList() }.add(Entry(virtualFile, unit))
                // An architecture is also reachable by the entity it names,
                // which is how `entity -> its architectures` is answered.
                unit.target?.let { out.getOrPut(it) { ArrayList() }.add(Entry(virtualFile, unit)) }
            }
        }
        return out
    }
}
