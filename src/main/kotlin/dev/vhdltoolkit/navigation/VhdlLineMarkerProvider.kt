package dev.vhdltoolkit.navigation

import com.intellij.codeInsight.daemon.RelatedItemLineMarkerInfo
import com.intellij.codeInsight.daemon.RelatedItemLineMarkerProvider
import com.intellij.codeInsight.navigation.NavigationGutterIconBuilder
import com.intellij.icons.AllIcons
import com.intellij.psi.PsiElement
import dev.vhdltoolkit.VhdlLanguage
import dev.vhdltoolkit.project.VhdlDesignUnits
import dev.vhdltoolkit.project.VhdlDesignUnits.Kind

/**
 * Gutter icons linking the halves of a design.
 *
 * VHDL separates what a component looks like from what it does, and the two
 * declarations are usually in different files. Without a way to jump between
 * them you navigate by filename and memory. Three links are offered:
 *
 *  - an entity to the architectures that implement it
 *  - an architecture back to its entity
 *  - an instantiation to the entity or component it names
 */
class VhdlLineMarkerProvider : RelatedItemLineMarkerProvider() {

    override fun getName(): String = "VHDL design units"

    override fun collectNavigationMarkers(
        element: PsiElement,
        result: MutableCollection<in RelatedItemLineMarkerInfo<*>>,
    ) {
        if (element.language !is VhdlLanguage) return
        // A marker must sit on a leaf, or the platform complains that two
        // markers cover the same range.
        if (element.firstChild != null) return

        val file = element.containingFile ?: return
        val start = element.textRange.startOffset
        val unit = VhdlDesignUnitIndex.inFile(file).firstOrNull { it.nameOffset == start } ?: return

        val project = element.project
        val targets = when (unit.kind) {
            Kind.ENTITY -> architecturesOf(project, unit.name)
            Kind.ARCHITECTURE -> declarationsOf(project, unit.target, Kind.ENTITY)
            Kind.INSTANTIATION -> declarationsOf(project, unit.target, Kind.ENTITY, Kind.COMPONENT)
            else -> return
        }
        if (targets.isEmpty()) return

        val icon = when (unit.kind) {
            Kind.ENTITY -> AllIcons.Gutter.ImplementedMethod
            else -> AllIcons.Gutter.ImplementingMethod
        }
        val tooltip = when (unit.kind) {
            Kind.ENTITY -> "Go to architecture"
            Kind.ARCHITECTURE -> "Go to entity"
            else -> "Go to declaration"
        }

        result.add(
            NavigationGutterIconBuilder.create(icon)
                .setTargets(targets)
                .setTooltipText(tooltip)
                .createLineMarkerInfo(element),
        )
    }

    private fun architecturesOf(
        project: com.intellij.openapi.project.Project,
        entity: String,
    ): List<PsiElement> =
        VhdlDesignUnitIndex.named(project, entity)
            .filter { it.unit.kind == Kind.ARCHITECTURE && it.unit.target == entity }
            .mapNotNull { VhdlDesignUnitIndex.leafAt(project, it.file, it.unit.nameOffset) }

    private fun declarationsOf(
        project: com.intellij.openapi.project.Project,
        name: String?,
        vararg kinds: VhdlDesignUnits.Kind,
    ): List<PsiElement> {
        if (name == null) return emptyList()
        return VhdlDesignUnitIndex.named(project, name)
            .filter { it.unit.kind in kinds && it.unit.name == name }
            .mapNotNull { VhdlDesignUnitIndex.leafAt(project, it.file, it.unit.nameOffset) }
    }
}
