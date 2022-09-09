package org.ice1000.kala

import com.intellij.codeInspection.*
import com.intellij.openapi.project.Project
import com.intellij.psi.*
import com.intellij.psi.util.InheritanceUtil

class MapPutUnetaInspection : KalaInspection() {
  private companion object FIX : LocalQuickFix {
    override fun getFamilyName() = CommonQuickFixBundle.message("fix.simplify")
    override fun applyFix(project: Project, desc: ProblemDescriptor) {
      val args = desc.psiElement as? PsiExpressionList ?: return
      val (args1, args2) = args.expressions
      if (args1 !is PsiReferenceExpression || args2 !is PsiReferenceExpression) return
      args1.replace(args1.qualifier ?: return)
      args2.delete()
      args.children.firstOrNull { it is PsiJavaToken && it.tokenType == JavaTokenType.COMMA }?.delete()
    }

    private val clz = "$MU_PKG.MutableMapLike"
  }
  override fun getDisplayName() = KalaBundle.message("kala.map-put-uneta.name")
  override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean) = methodCallVisitor {
    val args = it.argumentList
    if (args.expressionCount != 2) return@methodCallVisitor
    val (args1, args2) = args.expressions
    if (args1 !is PsiReferenceExpression || args2 !is PsiReferenceExpression) return@methodCallVisitor

    val methodExpression = it.methodExpression
    val resolvedMethod = methodExpression.referenceName ?: return@methodCallVisitor
    if (resolvedMethod != "put") return@methodCallVisitor
    val type = methodExpression.qualifierExpression?.type ?: return@methodCallVisitor
    if (!InheritanceUtil.isInheritor(type, clz)) return@methodCallVisitor

    if (args1.referenceName != "_1" || args2.referenceName != "_2") return@methodCallVisitor
    val qua1 = args1.qualifierExpression ?: return@methodCallVisitor
    val qua2 = args2.qualifierExpression ?: return@methodCallVisitor
    // TODO: improve
    if (!qua1.textMatches(qua2)) return@methodCallVisitor
    val range = args1.textRangeInParent.union(args2.textRangeInParent)
    holder.registerProblem(holder.manager.createProblemDescriptor(args, range,
      CommonQuickFixBundle.message("fix.simplify"),
      ProblemHighlightType.LIKE_UNUSED_SYMBOL, isOnTheFly, FIX))
  }
}