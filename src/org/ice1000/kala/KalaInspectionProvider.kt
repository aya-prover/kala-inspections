package org.ice1000.kala

import com.intellij.codeInspection.*
import com.intellij.openapi.project.Project
import com.intellij.psi.JavaElementVisitor
import com.intellij.psi.PsiMethodCallExpression
import com.intellij.psi.impl.JavaPsiFacadeEx
import com.intellij.psi.util.InheritanceUtil
import com.siyeh.ig.psiutils.CommentTracker

class KalaInspectionProvider : InspectionToolProvider {
  override fun getInspectionClasses(): Array<Class<out LocalInspectionTool>> = arrayOf(
    MudaInspection::class.java,
    PreferEmptyInspection::class.java,
  )
}

const val GROUP_DISPLAY = "Kala collections"

abstract class KalaInspection : LocalInspectionTool() {
  override fun isEnabledByDefault() = true
  final override fun getGroupDisplayName() = GROUP_DISPLAY
}

class PreferEmptyInspection : KalaInspection() {
  override fun getDisplayName() = "Prefer meaningful names over ''of()''"
  override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean) = object : JavaElementVisitor() {
    private val classes = listOf(
      "kala.collection.mutable.Buffer" to "create",
      "kala.collection.mutable.MutableMap" to "create",
      "kala.collection.immutable.ImmutableSeq" to "empty",
      "kala.collection.immutable.ImmutableArray" to "empty",
      "kala.collection.immutable.ImmutableVector" to "empty",
      "kala.collection.immutable.ImmutableMap" to "empty",
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

    override fun visitMethodCallExpression(expression: PsiMethodCallExpression?) {
      super.visitMethodCallExpression(expression)
      val methodExpression = expression?.methodExpression ?: return
      val resolvedMethod = methodExpression.referenceName ?: return
      val type = methodExpression.qualifier?.reference?.canonicalText ?: return
      classes.forEach { (clz, method) ->
        if (clz == type && resolvedMethod == "of") {
          holder.registerProblem(replaceMethodCall(expression, method))
        }
      }
    }
  }
}

class MudaInspection : KalaInspection() {
  private object FIX : LocalQuickFix {
    override fun getFamilyName() = CommonQuickFixBundle.message("fix.simplify")
    override fun applyFix(project: Project, pd: ProblemDescriptor) {
      val element = pd.psiElement as? PsiMethodCallExpression ?: return
      CommentTracker().replaceAndRestoreComments(element, element.methodExpression.qualifier!!)
    }
  }

  override fun getDisplayName() = "Unneeded methods simplification"
  override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean) = object : JavaElementVisitor() {
    private val methods = listOf(
      "kala.collection.immutable.ImmutableSeq" to "toImmutableSeq",
      "kala.collection.Seq" to "toSeq",
      "kala.collection.immutable.ImmutableArray" to "toImmutableArray",
      "kala.collection.immutable.ImmutableVector" to "toImmutableVector",
      "kala.collection.immutable.ImmutableLinkedSeq" to "toImmutableLinkedSeq",
      "kala.collection.immutable.ImmutableSizedLinkedSeq" to "toImmutableSizedLinkedSeq",
      "kala.collection.SeqView" to "view",
      "kala.collection.MapView" to "view",
      "kala.collection.SetView" to "view",
      "kala.collection.View" to "view",
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
