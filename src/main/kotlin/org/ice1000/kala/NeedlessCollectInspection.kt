package org.ice1000.kala

import com.intellij.codeInspection.*
import com.intellij.openapi.project.Project
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiMethodCallExpression
import com.intellij.psi.util.InheritanceUtil

class NeedlessCollectInspection : KalaInspection() {
  private val methods = listOf("collector", "factory")

  override fun getDisplayName() = KalaBundle.message("kala.needless-collect.name")
  override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean) = methodCallVisitor {
    val methodExpression = it.methodExpression
    if (!InheritanceUtil.isInheritor(methodExpression.qualifierExpression?.type, "$PKG.CollectionLike"))
      return@methodCallVisitor
    val methodNamePsi = methodExpression.referenceNameElement ?: return@methodCallVisitor
    if (!methodNamePsi.textMatches("collect")) return@methodCallVisitor
    val argumentList = it.argumentList
    if (argumentList.expressionCount != 1) return@methodCallVisitor
    val arg = argumentList.expressions[0] as? PsiMethodCallExpression ?: return@methodCallVisitor
    val resolveMethod = arg.resolveMethod() ?: return@methodCallVisitor
    if (resolveMethod.name !in methods) return@methodCallVisitor
    val classQName = resolveMethod.containingClass?.qualifiedName
    val (_, method) = INM_CLZ_FACTORIES.firstOrNull { (clz, _) -> classQName == clz } ?: return@methodCallVisitor
    val message = CommonQuickFixBundle.message("fix.replace.with.x", method)
    holder.registerProblem(holder.manager.createProblemDescriptor(
      it, methodNamePsi.textRangeInParent, message, ProblemHighlightType.WARNING,
      isOnTheFly, object : LocalQuickFix {
      override fun getFamilyName() = message
      override fun applyFix(project: Project, pd: ProblemDescriptor) {
        if (pd.psiElement != it || !it.isValid) return
        argumentList.firstChild.nextSibling.delete()
        methodNamePsi.replace(JavaPsiFacade.getElementFactory(project).createIdentifier(method))
      }
    }))
  }
}
