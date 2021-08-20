package org.ice1000.kala

import com.intellij.codeInspection.*
import com.intellij.openapi.project.Project
import com.intellij.psi.JavaElementVisitor
import com.intellij.psi.PsiMethodCallExpression
import com.intellij.psi.util.InheritanceUtil
import com.siyeh.ig.psiutils.CommentTracker

class KalaInspectionProvider : InspectionToolProvider {
  override fun getInspectionClasses(): Array<Class<out LocalInspectionTool>> = arrayOf(
    MudaInspection::class.java,
  )
}

class MudaInspection : LocalInspectionTool() {
  override fun isEnabledByDefault() = true
  override fun getDisplayName() = "Kala collections simplification"
  override fun getGroupDisplayName() = "Kala collections"
  override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean) = object : JavaElementVisitor() {
    private val manager = holder.manager
    private val methods = listOf(
      "kala.collection.immutable.ImmutableSeq" to "toImmutableSeq",
      "kala.collection.Seq" to "toSeq",
      "kala.collection.immutable.ImmutableArray" to "toImmutableArray",
    )

    fun removeMethodCall(it: PsiMethodCallExpression): ProblemDescriptor {
      val methodName = it.methodExpression.referenceNameElement!!
      val range = methodName.textRangeInParent
      return manager.createProblemDescriptor(it, range,
        "Method call does nothing", ProblemHighlightType.LIKE_UNUSED_SYMBOL, isOnTheFly,
        object : LocalQuickFix {
          override fun getFamilyName() = CommonQuickFixBundle.message("fix.simplify")
          override fun applyFix(project: Project, pd: ProblemDescriptor) {
            val element = pd.psiElement as? PsiMethodCallExpression ?: return
            CommentTracker().replaceAndRestoreComments(element, element.methodExpression.qualifier!!)
          }
        })
    }

    override fun visitMethodCallExpression(expression: PsiMethodCallExpression?) {
      super.visitMethodCallExpression(expression)
      val resolvedMethod = expression?.resolveMethod() ?: return
      val type = expression.methodExpression.qualifierExpression?.type ?: return
      methods.forEach { (clz, method) ->
        if (resolvedMethod.name == method && InheritanceUtil.isInheritor(type, clz)) {
          holder.registerProblem(removeMethodCall(expression))
        }
      }
    }
  }
}
