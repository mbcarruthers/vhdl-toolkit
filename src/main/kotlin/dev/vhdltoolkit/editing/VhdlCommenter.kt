package dev.vhdltoolkit.editing

import com.intellij.lang.Commenter

/** Enables Comment with Line Comment (Ctrl+/) using VHDL's `--`. */
class VhdlCommenter : Commenter {
    override fun getLineCommentPrefix(): String = "-- "
    override fun getBlockCommentPrefix(): String = "/*"
    override fun getBlockCommentSuffix(): String = "*/"
    override fun getCommentedBlockCommentPrefix(): String? = null
    override fun getCommentedBlockCommentSuffix(): String? = null
}
