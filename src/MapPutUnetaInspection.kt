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
      if (args1 is PsiReferenceExpression && args2 is PsiReferenceExpression) {
        args1.replace(args1.qualifier ?: return)
      } else if (args1 is PsiMethodCallExpression && args2 is PsiMethodCallExpression) {
        args1.replace(args1.methodExpression.qualifierExpression ?: return)
      } else return
      args2.delete()
      args.children.firstOrNull { it is PsiJavaToken && it.tokenType == JavaTokenType.COMMA }?.delete()
    }

    private val clz = "$MU_PKG.MutableMapLike"
    private val tupClz = "$TU_PKG.Tuple2"
    private val method1Names = listOf("component1", "getKey")
    private val method2Names = listOf("component2", "getValue")
  }

  override fun getDisplayName() = KalaBundle.message("kala.map-put-uneta.name")
  override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean) = methodCallVisitor {
    val args = it.argumentList
    if (args.expressionCount != 2) return@methodCallVisitor
    val (args1, args2) = args.expressions

    val methodExpression = it.methodExpression
    val resolvedMethod = methodExpression.referenceName ?: return@methodCallVisitor
    if (resolvedMethod != "put") return@methodCallVisitor
    val type = methodExpression.qualifierExpression?.type ?: return@methodCallVisitor
    if (!InheritanceUtil.isInheritor(type, clz)) return@methodCallVisitor

    if (args1 is PsiReferenceExpression && args2 is PsiReferenceExpression) {
      if (args1.referenceName != "_1" || args2.referenceName != "_2") return@methodCallVisitor
      if (!validateQualifiers(
          args1.qualifierExpression ?: return@methodCallVisitor,
          args2.qualifierExpression ?: return@methodCallVisitor)) return@methodCallVisitor
    } else if (args1 is PsiMethodCallExpression && args2 is PsiMethodCallExpression) {
      val method1 = args1.methodExpression
      val method2 = args2.methodExpression
      if (method1.referenceName !in method1Names || method2.referenceName !in method2Names) return@methodCallVisitor
      if (!validateQualifiers(
          method1.qualifierExpression ?: return@methodCallVisitor,
          method2.qualifierExpression ?: return@methodCallVisitor)) return@methodCallVisitor
    } else return@methodCallVisitor
    val range = args1.textRangeInParent.union(args2.textRangeInParent)
    holder.registerProblem(holder.manager.createProblemDescriptor(args, range,
      CommonQuickFixBundle.message("fix.simplify"),
      ProblemHighlightType.LIKE_UNUSED_SYMBOL, isOnTheFly, FIX))
  }

  private fun validateQualifiers(qua1: PsiExpression, qua2: PsiExpression): Boolean {
    // TODO: improve
    if (!qua1.textMatches(qua2)) return true
    if (qua1.type?.canonicalText != tupClz) return true
    return false
  }
}