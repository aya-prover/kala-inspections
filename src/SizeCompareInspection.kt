package org.ice1000.kala

import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.openapi.project.Project
import com.intellij.psi.*
import com.intellij.psi.tree.IElementType
import com.intellij.psi.util.InheritanceUtil

class SizeCompareInspection : KalaInspection() {
  sealed interface FixType
  object IsEmpty : FixType
  object IsNotEmpty : FixType
  data class Const(val boolean: Boolean) : FixType
  data class Compare(val element: PsiElement, val comparison: Comparison) : FixType

  enum class Comparison {
    LT, LE, GT, GE, EQ, NE
  }

  private fun inv(comparison: Comparison) = when (comparison) {
    Comparison.LT -> Comparison.GT
    Comparison.LE -> Comparison.GE
    Comparison.GT -> Comparison.LT
    Comparison.GE -> Comparison.LE
    else -> comparison
  }

  private fun asComparison(tokenType: IElementType) = when (tokenType) {
    JavaTokenType.LT -> Comparison.LT
    JavaTokenType.LE -> Comparison.LE
    JavaTokenType.GT -> Comparison.GT
    JavaTokenType.GE -> Comparison.GE
    JavaTokenType.EQ -> Comparison.EQ
    JavaTokenType.NE -> Comparison.NE
    /*JavaTokenType.EQEQ*/ else -> Comparison.EQ
  }

  private class FIX(private val qualifier: PsiExpression, private val type: FixType) : LocalQuickFix {
    override fun getFamilyName() = KalaBundle.message("kala.size-compare.fix")
    override fun applyFix(project: Project, p1: ProblemDescriptor) {
      if (!qualifier.isValid) return
      val factory = JavaPsiFacade.getElementFactory(project)
      p1.psiElement.replace(when (type) {
        is Compare -> when (type.comparison) {
          Comparison.LT -> factory.createExpressionFromText("${qualifier.text}.sizeLessThan(${type.element.text})", qualifier)
          Comparison.LE -> factory.createExpressionFromText("${qualifier.text}.sizeLessThanOrEquals(${type.element.text})", qualifier)
          Comparison.GT -> factory.createExpressionFromText("${qualifier.text}.sizeGreaterThan(${type.element.text})", qualifier)
          Comparison.GE -> factory.createExpressionFromText("${qualifier.text}.sizeGreaterThanOrEquals(${type.element.text})", qualifier)
          Comparison.EQ -> factory.createExpressionFromText("${qualifier.text}.sizeEquals(${type.element.text})", qualifier)
          Comparison.NE -> factory.createExpressionFromText("${qualifier.text}.sizeNotEquals(${type.element.text})", qualifier)
        }
        is Const -> factory.createExpressionFromText("${type.boolean}", qualifier)
        IsEmpty -> factory.createExpressionFromText("${qualifier.text}.isEmpty()", qualifier)
        IsNotEmpty -> factory.createExpressionFromText("${qualifier.text}.isNotEmpty()", qualifier)
      })
    }
  }

  override fun getDisplayName() = KalaBundle.message("kala.size-compare.name")
  override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean) = methodCallVisitor {
    if (!it.argumentList.isEmpty) return@methodCallVisitor
    val m = it.methodExpression
    val refName = m.referenceNameElement ?: return@methodCallVisitor
    if (refName.text != "size") return@methodCallVisitor
    val qualifierE = m.qualifierExpression ?: return@methodCallVisitor
    val type = qualifierE.type ?: return@methodCallVisitor
    val parent = it.parent as? PsiBinaryExpression ?: return@methodCallVisitor
    if (!InheritanceUtil.isInheritor(type, "$PKG.base.AnyTraversable")) return@methodCallVisitor
    val lOperand = parent.lOperand
    val rOperand = parent.rOperand ?: return@methodCallVisitor
    val opToken = asComparison(parent.operationTokenType)
    val (operand, op) = if (lOperand === it) rOperand to opToken else lOperand to inv(opToken)
    holder.registerProblem(holder.manager.createProblemDescriptor(parent,
      parent.operationSign.textRangeInParent, displayName, ProblemHighlightType.WARNING, isOnTheFly,
      FIX(qualifierE, when (op) {
        Comparison.LT -> if (is0(operand)) Const(false) else Compare(operand, Comparison.LT)
        Comparison.GT -> if (is0(operand)) Const(true) else Compare(operand, Comparison.GT)
        Comparison.LE -> if (is0(operand)) IsEmpty else Compare(operand, Comparison.LE)
        Comparison.GE -> if (is0(operand)) IsNotEmpty else Compare(operand, Comparison.GE)
        Comparison.EQ -> if (is0(operand)) IsEmpty else Compare(operand, Comparison.EQ)
        Comparison.NE -> if (is0(operand)) IsNotEmpty else Compare(operand, Comparison.NE)
      })))
  }

  private fun is0(lOperand: PsiExpression) = lOperand is PsiLiteral &&
    (lOperand as PsiLiteral).textLength == 1 && lOperand.textContains('0')
  // ^ Overload resolution problem
}
