package org.ice1000.kala

import com.intellij.codeInspection.*
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiMethodCallExpression
import com.intellij.psi.impl.JavaPsiFacadeEx
import com.siyeh.ig.psiutils.CommentTracker

class PreferEmptyInspection : KalaInspection() {
  private val classes = listOf(
    "$MU_PKG.Buffer" to "create",
    "$MU_PKG.MutableMap" to "create",
    IMMUTABLE_SEQ to "empty",
    "$INM_PKG.ImmutableArray" to "empty",
    "$INM_PKG.ImmutableVector" to "empty",
    "$INM_PKG.ImmutableMap" to "empty",
  )

  override fun getDisplayName() = KalaBundle.message("kala.prefer-empty.name")
  override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean) = methodCallVisitor {
    if (!it.argumentList.isEmpty) return@methodCallVisitor
    val m = it.methodExpression
    if (m.referenceName != "of") return@methodCallVisitor
    val type = m.qualifier?.reference?.canonicalText ?: return@methodCallVisitor
    val (_, method) = classes.firstOrNull { (clz, _) -> clz == type } ?: return@methodCallVisitor
    val methodName = it.methodExpression.referenceNameElement!!
    val range = methodName.textRangeInParent
    val message = CommonQuickFixBundle.message("fix.replace.x.with.y", "of", method)
    holder.registerProblem(holder.manager.createProblemDescriptor(it, range, message,
      ProblemHighlightType.LIKE_DEPRECATED, isOnTheFly,
      object : LocalQuickFix {
        override fun getFamilyName() = message
        override fun applyFix(project: Project, pd: ProblemDescriptor) {
          val element = pd.psiElement as? PsiMethodCallExpression ?: return
          val newId = JavaPsiFacadeEx.getElementFactory(project).createIdentifier(method)
          CommentTracker().replaceAndRestoreComments(element.methodExpression.referenceNameElement!!, newId)
        }
      })
    )
  }
}