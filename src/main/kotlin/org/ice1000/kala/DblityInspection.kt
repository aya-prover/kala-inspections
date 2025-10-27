package org.ice1000.kala

import com.intellij.codeInspection.AbstractBaseJavaLocalInspectionTool
import com.intellij.codeInspection.LocalInspectionToolSession
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.*
import org.jetbrains.annotations.Nls

class DblityInspection : AbstractBaseJavaLocalInspectionTool() {
  override fun isEnabledByDefault() = true
  override fun getGroupDisplayName() = KalaBundle.message("kala.aya.group.name")

  enum class Kind {
    Inherit, Bound, Closed;

    fun toAnnotationName(): String {
      return "@$this"
    }
  }

  // Make it nullable even we don't really return null,
  // in case we add [Inherit] annotation and want to annotate [Term]s explicitly
  fun getKind(ty: PsiType): Kind? {
    // https://github.com/JetBrains/intellij-community/blob/d18a3edba879d572a2e1581bc39ce8faaa0c565c/java/openapi/src/com/intellij/codeInsight/NullableNotNullDialog.java
    val isClosed =
      ty.annotations.any { it.qualifiedName?.endsWith("Closed") == true } // FIXME: don't hard code, make a setting panel, see above
    val isBound =
      ty.annotations.any { it.qualifiedName?.endsWith("Bound") == true } // FIXME: make a inspection that prevents [Closed] and [Bound] annotates the same type

    return when {
      isClosed -> Kind.Closed
      isBound -> Kind.Bound
      else -> Kind.Inherit
    }
  }

  /**
   * @return null if necessary information is missing, the inspection should be stopped.
   */
  fun getKind(expr: PsiExpression): Kind? {
    val ty = expr.type ?: return null
    val basicKind = getKind(ty)
    // if [expr] is already annotated or cannot be used for inferring
    if (basicKind == null || basicKind != Kind.Inherit) return basicKind

    // otherwise, try to infer the real kind
    // this includes:
    // * getter of record
    // * method of some class, like Closure
    if (expr is PsiParenthesizedExpression) {
      val innerExpr = expr.expression
      if (innerExpr != null) {
        return getKind(innerExpr)
      }
    }
    if (expr is PsiMethodCallExpression) {
      val methodExpr = expr.methodExpression
      val receiver = methodExpr.qualifierExpression

      if (receiver != null) {
        val receiverKind = getKind(receiver)
        if (receiverKind != null) {
          // basicKind (the return type of [expr]) is Inherit, and we know the kind of [receiver]
          // thus the real kind of [expr] is the kind of [receiver]

          return receiverKind
        }
      }
    }

    return basicKind
  }

  fun doInspect(
    expected: PsiType,
    actual: PsiExpression,
    holder: ProblemsHolder,
    session: LocalInspectionToolSession
  ) {
    // we may assume [param] is explicitly annotated, otherwise no inspection will be performed
    // this case mostly happens on constructor
    val expectedKind = getKind(expected)
    val actualKind = getKind(actual)

    if (expectedKind == null
      || expectedKind == Kind.Inherit
      // don't skip if `actualKind` is `Inherit`, we treat rhs `Inherit` as Bound, but still skip if no kind
      || actualKind == null
    ) return

    val cmp = expectedKind.compareTo(actualKind)
    if (cmp > 0) {
      // not assignable
      holder.registerProblem(
        actual,
        KalaBundle.message(
          "kala.aya.dblity.not.assignable",
          "'${actualKind.toAnnotationName()}'",
          "'${expectedKind.toAnnotationName()}'"
        ),
        ProblemHighlightType.WARNING
      )
    } else if (cmp < 0) {
      // assignable with implicit cast
      // TODO: I want to make some highlight, but how?
      // sorry holder
      // FIXME: this seems invisible for some reason, this path is reachable
      holder.registerProblem(
        actual,
        KalaBundle.message("kala.aya.dblity.smart.cast", expectedKind.toAnnotationName()),
        ProblemHighlightType.INFORMATION
      )
    }
  }

  override fun getDisplayName(): @Nls(capitalization = Nls.Capitalization.Sentence) String {
    return KalaBundle.message("kala.aya.dblity")
  }

  override fun buildVisitor(
    holder: ProblemsHolder,
    isOnTheFly: Boolean,
    session: LocalInspectionToolSession
  ): PsiElementVisitor {
    return object : JavaElementVisitor() {
      override fun visitCallExpression(callExpression: PsiCallExpression) {
        super.visitCallExpression(callExpression)
        val resolved = callExpression.resolveMethod() ?: return
        val args = callExpression.argumentList ?: return

        val zipped = resolved.parameterList.parameters.zip(args.expressions)
        for ((param, arg) in zipped) {
          doInspect(param.type, arg, holder, session)
        }
      }

      override fun visitAssignmentExpression(expression: PsiAssignmentExpression) {
        super.visitAssignmentExpression(expression)

        if (expression.operationTokenType != JavaTokenType.EQ) return

        // just get kind, left expression is normally not complicate
        val lKind = expression.lExpression.type ?: return
        doInspect(lKind, expression.rExpression ?: return, holder, session)
      }
    }
  }
}