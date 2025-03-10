package org.ice1000.kala

import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.openapi.project.Project
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.PsiReferenceExpression
import com.intellij.psi.util.InheritanceUtil
import com.siyeh.InspectionGadgetsBundle

class SamenessInspection : KalaInspection() {
  companion object {
    private const val METHOD_NAME: String = "sameElements"
  }
  
  private object FIX : LocalQuickFix {
    override fun getFamilyName(): String = InspectionGadgetsBundle.message("constant.conditional.expression.simplify.quickfix")
    
    override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
      descriptor.psiElement.replace(JavaPsiFacade.getElementFactory(project)
        .createExpressionFromText("true", descriptor.endElement))
    }
  }

  override fun getDisplayName(): String = InspectionGadgetsBundle.message("boolean.expression.can.be.simplified.problem.descriptor", "true")
  
  override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor = methodCallVisitor {
    val args = it.argumentList.expressions
    val methodExpr = it.methodExpression
    val selfExpr = methodExpr.qualifierExpression as? PsiReferenceExpression ?: return@methodCallVisitor
    val selfQualifier = selfExpr.qualifierExpression
    if (args.isEmpty()) return@methodCallVisitor
    if (methodExpr.referenceName != METHOD_NAME) return@methodCallVisitor
    val argExpr = args.first()  as? PsiReferenceExpression ?: return@methodCallVisitor
    val argQualifier = argExpr.qualifierExpression
    // Ideally we should recursively compare the qualifier expressions, but this is enough to fix `ModulePath`
    if (selfQualifier == null && argQualifier != null) return@methodCallVisitor
    if (selfQualifier != null && argQualifier == null) return@methodCallVisitor

    val self = selfExpr.reference?.resolve() ?: return@methodCallVisitor
    if (!InheritanceUtil.isInheritor(selfExpr.type, "$PKG.base.AnyTraversable"))
      return@methodCallVisitor
    val arg = argExpr.reference?.resolve() ?: return@methodCallVisitor

    if (self == arg) {
      holder.registerProblem(holder.manager.createProblemDescriptor(
        it, displayName, FIX, ProblemHighlightType.WARNING, isOnTheFly
      ))
    }
  }
}