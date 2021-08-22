package org.ice1000.kala

import com.intellij.codeInspection.*
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiMethodCallExpression
import com.intellij.psi.util.InheritanceUtil

class MudaInspection : KalaInspection() {
  private object FIX : LocalQuickFix {
    override fun getFamilyName() = CommonQuickFixBundle.message("fix.simplify")
    override fun applyFix(project: Project, pd: ProblemDescriptor) {
      val element = pd.psiElement as? PsiMethodCallExpression ?: return
      element.methodExpression.qualifier?.let(element::replace)
    }
  }

  private val methods = listOf(
    "$PKG.Seq" to "toSeq",
    "$PKG.SeqView" to "view",
    "$PKG.MapView" to "view",
    "$PKG.SetView" to "view",
    "$PKG.View" to "view",
  ) + INM_CLZ_FACTORIES

  override fun getDisplayName() = KalaBundle.message("kala.muda.name")
  override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean) = methodCallVisitor {
    val methodExpression = it.methodExpression
    val resolvedMethod = methodExpression.referenceName ?: return@methodCallVisitor
    val type = methodExpression.qualifierExpression?.type ?: return@methodCallVisitor
    if (methods.any { (clz, method) -> resolvedMethod == method && InheritanceUtil.isInheritor(type, clz) }) {
      val methodName = methodExpression.referenceNameElement!!
      val range = methodName.textRangeInParent
      holder.registerProblem(holder.manager.createProblemDescriptor(it, range,
        CommonQuickFixBundle.message("fix.remove.redundant", resolvedMethod),
        ProblemHighlightType.LIKE_UNUSED_SYMBOL, isOnTheFly, FIX))
    }
  }
}