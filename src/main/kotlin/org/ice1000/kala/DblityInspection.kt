package org.ice1000.kala

import com.intellij.codeInspection.AbstractBaseJavaLocalInspectionTool
import com.intellij.codeInspection.LocalInspectionToolSession
import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.openapi.project.Project
import com.intellij.psi.*
import com.intellij.psi.impl.light.LightRecordField
import com.intellij.psi.util.parentOfTypes
import com.intellij.psi.impl.light.LightRecordField
import com.intellij.psi.util.parentOfTypes
import org.jetbrains.annotations.Nls

class DblityInspection : AbstractBaseJavaLocalInspectionTool() {
  override fun isEnabledByDefault() = true
  override fun getGroupDisplayName() = KalaBundle.message("kala.aya.group.name")

  enum class Kind {
    Inherit, Bound, Closed;

    fun toAnnotationName(): String {
      return "@$this"
    }

    /**
     * Check if [other] is assignable to [this], or in other words, [this] is assignable from [other]
     * @return negative, if not assignable, positive if assignable with cast
     */
    fun isAssignable(other: Kind): Int {
      val other = if (other == Inherit) Bound else other
      return other.compareTo(this)
    }
  }

  fun getKind(ty: PsiType): Kind? = getKind(ty.annotations)

  /// Make it nullable even we don't really return null,
  /// in case we add [Inherit] annotation and want to annotate [Term]s explicitly
  fun getKind(annotations: Array<out PsiAnnotation>): Kind? {
    // https://github.com/JetBrains/intellij-community/blob/d18a3edba879d572a2e1581bc39ce8faaa0c565c/java/openapi/src/com/intellij/codeInsight/NullableNotNullDialog.java
    val isClosed =
      annotations.any { it.qualifiedName?.endsWith("Closed") == true } // FIXME: don't hard code, make a setting panel, see above
    val isBound =
      annotations.any { it.qualifiedName?.endsWith("Bound") == true } // FIXME: make a inspection that prevents [Closed] and [Bound] annotates the same type

    return when {
      isClosed -> Kind.Closed
      isBound -> Kind.Bound
      else -> Kind.Inherit
    }
  }

  object DeleteAnnotationFix : LocalQuickFix {
    override fun getFamilyName() = KalaBundle.message("kala.aya.dblity.delete.annotation.fix.name")
    override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
      val annotation = descriptor.psiElement as? PsiAnnotation ?: return
      annotation.delete()
    }
  }

  /**
   * @return null if necessary information is missing, the inspection should be stopped.
   */
  fun getKind(expr: PsiExpression, holder: ProblemsHolder): Kind? {
    val ty = expr.type ?: return null
    if (ty == PsiTypes.nullType()) return null

    val basicKind = getKind(ty)
    // if [expr] is already annotated or cannot be used for inferring
    // TODO: comment this line out to trigger the "unused annotation" inspection
    if (basicKind == null || basicKind != Kind.Inherit) return basicKind

    // otherwise, try to infer the real kind
    // this includes:
    // * getter of record
    // * method of some class, like Closure
    when (expr) {
      is PsiParenthesizedExpression -> {
        val innerExpr = expr.expression
        if (innerExpr != null) {
          return getKind(innerExpr, holder)
        }
      }

      is PsiReferenceExpression -> {
        val def = expr.resolve() ?: return basicKind
        if (def is LightRecordField) {
          // First see annotations
          val kind = getKind(def.annotations)
          // if there is explicit annotation, use those
          if (kind != null && kind != Kind.Inherit) return kind
          // otherwise, infer from the switch
        }
        if (def is PsiPatternVariable) {
          val parent = def.parentOfTypes(PsiInstanceOfExpression::class, PsiSwitchBlock::class, withSelf = false)
          when (parent) {
            is PsiInstanceOfExpression -> {
              val operadKind = getKind(parent.operand, holder)
              if (operadKind != null && operadKind != Kind.Inherit) {
                proposeDeleteAnnotations(def.annotations, holder)
              }
              return operadKind
            }
            is PsiSwitchBlock -> {
              val expression = parent.expression
              if (expression != null) {
                val exprKind = getKind(expression, holder)
                if (exprKind != null && exprKind != Kind.Inherit) {
                  proposeDeleteAnnotations(def.annotations, holder)
                }
                return exprKind
              }
            }
          }
        }
      }

      is PsiMethodCallExpression -> {
        val methodExpr = expr.methodExpression
        val receiver = methodExpr.qualifierExpression

        if (receiver != null) {
          val receiverKind = getKind(receiver, holder)
          if (receiverKind != null) {
            // basicKind (the return type of [expr]) is Inherit, and we know the kind of [receiver]
            // thus the real kind of [expr] is the kind of [receiver]

            return receiverKind
          }
        }
      }

      is PsiNewExpression -> {
        // check if the class is marked
        val annotations = expr.resolveConstructor()
          ?.containingClass
          ?.annotations

        if (annotations != null) {
          val classKind = getKind(annotations)
          if (classKind != null) return classKind
        }
      }
    }

    return basicKind
  }

  private fun proposeDeleteAnnotations(
    annotations: Array<out PsiAnnotation>,
    holder: ProblemsHolder
  ) {
    annotations.forEach {
      holder.registerProblem(
        it, KalaBundle.message("kala.aya.dblity.unused.annotation"),
        ProblemHighlightType.LIKE_UNUSED_SYMBOL, it.textRangeInParent,
        DeleteAnnotationFix
      )
    }
  }

  fun doInspect(
    expected: PsiType,
    actual: PsiExpression,
    holder: ProblemsHolder,
    session: LocalInspectionToolSession,
    strict: Boolean
  ) {
    // we may assume [param] is explicitly annotated, otherwise no inspection can be performed
    // this case mostly happens on constructor
    val expectedKind = getKind(expected)
    val actualKind = getKind(actual, holder)

    if (expectedKind == null
      || expectedKind == Kind.Inherit
      || actualKind == null
      // in strict mode, we treat `Inherit` as `Bound` at rhs
      || (!strict && actualKind == Kind.Inherit)
    ) return

    val cmp = expectedKind.isAssignable(actualKind)
    if (cmp < 0) {
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
    } else if (cmp > 0) {
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

        // TODO: deal with vararg
        val zipped = resolved.parameterList.parameters.zip(args.expressions)
        for ((param, arg) in zipped) {
          doInspect(param.type, arg, holder, session, true)
        }
      }

      override fun visitAssignmentExpression(expression: PsiAssignmentExpression) {
        super.visitAssignmentExpression(expression)

        if (expression.operationTokenType != JavaTokenType.EQ) return

        // just get kind, left expression is normally not complicate
        val lKind = expression.lExpression.type ?: return
        doInspect(lKind, expression.rExpression ?: return, holder, session, false)
      }

      override fun visitDeclarationStatement(statement: PsiDeclarationStatement) {
        super.visitDeclarationStatement(statement)
        statement.declaredElements.forEach { e ->
          if (e is PsiLocalVariable) {
            val initializer = e.initializer ?: return@forEach
            val expected = e.type
            doInspect(expected, initializer, holder, session, false)
          }
        }
      }
    }
  }
}