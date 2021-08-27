package org.ice1000.kala

import com.intellij.codeInspection.*
import com.intellij.openapi.project.Project
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiNewExpression

class TupleOfInspection : KalaInspection() {
  private companion object FIX : LocalQuickFix {
    override fun getFamilyName() = CommonQuickFixBundle.message("fix.replace.with.x", "Tuple.of")
    override fun applyFix(project: Project, pd: ProblemDescriptor) {
      val it = pd.psiElement as? PsiNewExpression ?: return
      val tyArgs = it.classReference?.parameterList?.text ?: return
      val args = it.argumentList ?: return
      it.replace(JavaPsiFacade.getElementFactory(project)
        .createExpressionFromText("Tuple.${tyArgs.takeIf { it != "<>" }.orEmpty()}of${args.text}", it))
    }
  }

  override fun getDisplayName() = KalaBundle.message("kala.prefer-factory-valhalla.name")
  override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean) = newVisitor {
    val cls = it.classReference?.resolve() as? PsiClass ?: return@newVisitor
    val qname = cls.qualifiedName ?: return@newVisitor
    if (qname == "$TU_PKG.TupleXXL") return@newVisitor
    if (!qname.startsWith("$TU_PKG.Tuple")) return@newVisitor
    holder.registerProblem(holder.manager.createProblemDescriptor(
      it, it.firstChild.textRangeInParent, displayName,
      ProblemHighlightType.LIKE_DEPRECATED, isOnTheFly, FIX))
  }
}