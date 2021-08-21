package org.ice1000.kala

import com.intellij.codeInspection.*
import com.intellij.openapi.project.Project
import com.intellij.psi.JavaElementVisitor
import com.intellij.psi.PsiMethodCallExpression
import com.intellij.psi.impl.JavaPsiFacadeEx
import com.siyeh.ig.psiutils.CommentTracker

class PreferEmptyInspection : KalaInspection() {
  override fun getDisplayName() = KalaBundle.message("kala.prefer-empty.name")
  override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean) = object : JavaElementVisitor() {
    private val classes = listOf(
      "$MU_PKG.Buffer" to "create",
      "$MU_PKG.MutableMap" to "create",
      IMMUTABLE_SEQ to "empty",
      "$INM_PKG.ImmutableArray" to "empty",
      "$INM_PKG.ImmutableVector" to "empty",
      "$INM_PKG.ImmutableMap" to "empty",
    )

    fun replaceMethodCall(it: PsiMethodCallExpression, method: String): ProblemDescriptor {
      val methodName = it.methodExpression.referenceNameElement!!
      val range = methodName.textRangeInParent
      val message = CommonQuickFixBundle.message("fix.replace.x.with.y", "of", method)
      return holder.manager.createProblemDescriptor(it, range, message,
        ProblemHighlightType.LIKE_DEPRECATED, isOnTheFly,
        object : LocalQuickFix {
          override fun getFamilyName() = message
          override fun applyFix(project: Project, pd: ProblemDescriptor) {
            val element = pd.psiElement as? PsiMethodCallExpression ?: return
            val newId = JavaPsiFacadeEx.getElementFactory(project).createIdentifier(method)
            CommentTracker().replaceAndRestoreComments(element.methodExpression.referenceNameElement!!, newId)
          }
        })
    }

    override fun visitMethodCallExpression(expression: PsiMethodCallExpression) {
      super.visitMethodCallExpression(expression)
      if (!expression.argumentList.isEmpty) return
      val m = expression.methodExpression
      val resolvedMethod = m.referenceName ?: return
      val type = m.qualifier?.reference?.canonicalText ?: return
      classes.forEach { (clz, method) ->
        if (clz == type && resolvedMethod == "of") {
          holder.registerProblem(replaceMethodCall(expression, method))
        }
      }
    }
  }
}