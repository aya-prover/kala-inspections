package org.ice1000.kala

import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiMethodCallExpression
import com.intellij.psi.PsiReferenceExpression
import com.intellij.psi.util.endOffset

class FuseImmSeqInspection : KalaInspection() {
  private val methods = listOf(
    "take", "drop", "takeLast", "dropLast", "updated",
    "takeWhile", "dropWhile", "reversed", "slice",
    "prepended", "prependedAll", "appended", "appendedAll",
    "mapIndexed", "map", "mapNotNull", "mapIndexedNotNull",
    "filterIsInstance", "filter", "filterNotNull", "filterNot",
    "flatMap", "distinct", "sorted",
  )

  override fun getDisplayName() = KalaBundle.message("kala.fuse-immseq.name", "consecutive")
  override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean) = methodCallVisitor {
    if (it.type?.canonicalText?.startsWith(IMMUTABLE_SEQ) != true) return@methodCallVisitor
    val parent = it.parent
    if (parent is PsiReferenceExpression && parent.referenceName in methods) return@methodCallVisitor
    var expr = it.methodExpression
    val outerMost = expr.referenceNameElement ?: return@methodCallVisitor
    var count = 0
    var qualifier = parent
    while (true) {
      if (expr.referenceName !in methods) break
      count++
      qualifier = expr.qualifier
      expr = (qualifier as? PsiMethodCallExpression ?: break).methodExpression
    }
    if (count < 2) return@methodCallVisitor
    val message = KalaBundle.message("kala.fuse-immseq.name", count)
    holder.registerProblem(holder.manager.createProblemDescriptor(
      it, outerMost.textRangeInParent, message,
      ProblemHighlightType.WARNING, isOnTheFly, object : LocalQuickFix {
      override fun getFamilyName() = message
      override fun applyFix(project: Project, pd: ProblemDescriptor) {
        if (pd.psiElement != it || !it.isValid) throw IllegalArgumentException()
        val manager = PsiDocumentManager.getInstance(project)
        val dom = manager.getDocument(it.containingFile) ?: return
        manager.commitDocument(dom)
        dom.insertString(it.endOffset, ".toImmutableSeq()")
        dom.insertString(qualifier.endOffset, ".view()")
        manager.commitDocument(dom)
      }
    }))
  }
}
