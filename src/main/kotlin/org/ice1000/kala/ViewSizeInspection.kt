package org.ice1000.kala

import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.util.InheritanceUtil

class ViewSizeInspection : KalaInspection() {
  override fun getDisplayName() = KalaBundle.message("kala.view-size.name")
  override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean) = methodCallVisitor {
    if (!it.argumentList.isEmpty) return@methodCallVisitor
    val m = it.methodExpression
    val refName = m.referenceNameElement ?: return@methodCallVisitor
    if (refName.text != "size") return@methodCallVisitor
    val type = m.qualifierExpression?.type ?: return@methodCallVisitor
    if (InheritanceUtil.isInheritor(type, "$PKG.base.AnyTraversable") &&
      !InheritanceUtil.isInheritor(type, "$PKG.AnyCollection")) {
      // This means it's potentially a 'view'
      holder.registerProblem(holder.manager.createProblemDescriptor(m,
        refName.textRangeInParent, displayName, ProblemHighlightType.WARNING, isOnTheFly))
    }
  }
}
