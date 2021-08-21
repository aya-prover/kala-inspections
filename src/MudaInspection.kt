package org.ice1000.kala

import com.intellij.codeInspection.*
import com.intellij.openapi.project.Project
import com.intellij.psi.JavaElementVisitor
import com.intellij.psi.PsiMethodCallExpression
import com.intellij.psi.util.InheritanceUtil
import com.siyeh.ig.psiutils.CommentTracker

class MudaInspection : KalaInspection() {
  private object FIX : LocalQuickFix {
    override fun getFamilyName() = CommonQuickFixBundle.message("fix.simplify")
    override fun applyFix(project: Project, pd: ProblemDescriptor) {
      val element = pd.psiElement as? PsiMethodCallExpression ?: return
      CommentTracker().replaceAndRestoreComments(element, element.methodExpression.qualifier!!)
    }
  }

  override fun getDisplayName() = KalaBundle.message("kala.muda.name")
  override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean) = object : JavaElementVisitor() {
    private val methods = listOf(
      IMMUTABLE_SEQ to "toImmutableSeq",
      "$INM_PKG.ImmutableArray" to "toImmutableArray",
      "$INM_PKG.ImmutableVector" to "toImmutableVector",
      "$INM_PKG.ImmutableLinkedSeq" to "toImmutableLinkedSeq",
      "$INM_PKG.ImmutableSizedLinkedSeq" to "toImmutableSizedLinkedSeq",
      "$PKG.Seq" to "toSeq",
      "$PKG.SeqView" to "view",
      "$PKG.MapView" to "view",
      "$PKG.SetView" to "view",
      "$PKG.View" to "view",
    )

    fun removeMethodCall(it: PsiMethodCallExpression, method: String): ProblemDescriptor {
      val methodName = it.methodExpression.referenceNameElement!!
      val range = methodName.textRangeInParent
      return holder.manager.createProblemDescriptor(it, range,
        CommonQuickFixBundle.message("fix.remove.redundant", method),
        ProblemHighlightType.LIKE_UNUSED_SYMBOL, isOnTheFly, FIX)
    }

    override fun visitMethodCallExpression(expression: PsiMethodCallExpression?) {
      super.visitMethodCallExpression(expression)
      val methodExpression = expression?.methodExpression ?: return
      val resolvedMethod = methodExpression.referenceName ?: return
      val type = methodExpression.qualifierExpression?.type ?: return
      methods.forEach { (clz, method) ->
        if (resolvedMethod == method && InheritanceUtil.isInheritor(type, clz)) {
          holder.registerProblem(removeMethodCall(expression, method))
        }
      }
    }
  }
}