package org.ice1000.kala

import com.intellij.codeInspection.*
import com.intellij.openapi.project.Project
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiMethodCallExpression

class ViewToMapInspection : KalaInspection() {
  private companion object FIX : LocalQuickFix {
    override fun getFamilyName() = CommonQuickFixBundle.message("fix.use", "from")
    override fun applyFix(p: Project, pd: ProblemDescriptor) {
      val methodCall = pd.psiElement as? PsiMethodCallExpression ?: return
      val qualifier = methodCall.methodExpression.qualifier ?: return
      methodCall.replace(JavaPsiFacade.getElementFactory(p).createExpressionFromText(
        "ImmutableMap.from(${qualifier.text})", methodCall))
    }
  }

  override fun getDisplayName() = KalaBundle.message("kala.view-to-map.name")
  override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean) = methodCallVisitor { call ->
    val methodExpression = call.methodExpression
    if (!call.argumentList.isEmpty) return@methodCallVisitor
    val resolvedMethod = methodExpression.referenceName ?: return@methodCallVisitor
    if (resolvedMethod != "toImmutableMap") return@methodCallVisitor
    val child = methodExpression.referenceNameElement!!
    holder.registerProblem(holder.manager.createProblemDescriptor(call, child.textRangeInParent,
      CommonQuickFixBundle.message("fix.use", "from"),
      ProblemHighlightType.LIKE_DEPRECATED, isOnTheFly, FIX))
  }
}
