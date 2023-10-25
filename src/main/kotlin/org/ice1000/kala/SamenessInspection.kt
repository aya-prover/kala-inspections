package org.ice1000.kala

import com.intellij.codeInspection.CommonQuickFixBundle
import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.openapi.project.Project
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiElementVisitor

// TODO: This also works for non-kala method
class SamenessInspection : KalaInspection() {
  companion object {
    private const val METHOD_NAME: String = "sameElements"
  }
  
  private object FIX : LocalQuickFix {
    override fun getFamilyName(): String = CommonQuickFixBundle.message("fix.replace.with.x", "true")
    
    override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
      descriptor.psiElement.replace(JavaPsiFacade.getElementFactory(project)
        .createExpressionFromText("true", descriptor.endElement))
    }
  }
  
  override fun getDisplayName(): String = KalaBundle.message("kala.sameness")
  
  override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor = methodCallVisitor {
    val args = it.argumentList.expressions
    val methodExpr = it.methodExpression
    val self = methodExpr.qualifierExpression?.reference?.resolve() ?: return@methodCallVisitor
    if (args.isEmpty()) return@methodCallVisitor
    if (methodExpr.referenceName != METHOD_NAME) return@methodCallVisitor
    val arg = args.first().reference?.resolve() ?: return@methodCallVisitor
    
    if (self == arg) {
      holder.registerProblem(holder.manager.createProblemDescriptor(
        it, displayName, FIX, ProblemHighlightType.WARNING, isOnTheFly
      ))
    }
  }
}