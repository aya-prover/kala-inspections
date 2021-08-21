package org.ice1000.kala

import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.openapi.project.Project
import com.intellij.psi.JavaElementVisitor
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiMethodCallExpression
import com.intellij.refactoring.suggested.endOffset

class FuseImmSeqInspection : KalaInspection() {
  private val methods = listOf(
    "take", "drop", "takeLast", "dropLast", "updated",
    "prepended", "prependedAll", "appended", "appendedAll",
    "mapIndexed", "map",
    // "mapChecked", "mapIndexedChecked",
    // ^ https://github.com/Glavo/kala-common/issues/40
  )

  override fun getDisplayName() = KalaBundle.message("kala.fuse-immseq.name")
  override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean) = object : JavaElementVisitor() {
    override fun visitMethodCallExpression(expression: PsiMethodCallExpression) {
      if (expression.type?.canonicalText?.startsWith("kala.collection.immutable.ImmutableSeq") != true)
        return super.visitMethodCallExpression(expression)
      var expr = expression.methodExpression
      val outerMost = expr.referenceNameElement
        ?: return super.visitMethodCallExpression(expression)
      var count = 0
      while (true) {
        val methodExpr = expr
        if (methodExpr.referenceName !in methods) break
        count++
        val qualifier = methodExpr.qualifier as? PsiMethodCallExpression ?: break
        expr = qualifier.methodExpression
      }
      if (count < 2) return super.visitMethodCallExpression(expression)
      val innerMost = expr.parent
      val message = KalaBundle.message("kala.fuse-immseq.name")
      holder.registerProblem(holder.manager.createProblemDescriptor(
        expression, outerMost.textRangeInParent, message,
        ProblemHighlightType.WARNING, isOnTheFly, object : LocalQuickFix {
        override fun getFamilyName() = message
        override fun applyFix(project: Project, pd: ProblemDescriptor) {
          if (pd.psiElement != expression || !expression.isValid) throw IllegalArgumentException()
          val manager = PsiDocumentManager.getInstance(project)
          val dom = manager.getDocument(expression.containingFile) ?: return
          manager.commitDocument(dom)
          dom.insertString(expression.endOffset, ".toImmutableSeq()")
          dom.insertString(innerMost.endOffset, ".view()")
          manager.commitDocument(dom)
        }
      }))
    }
  }
}
