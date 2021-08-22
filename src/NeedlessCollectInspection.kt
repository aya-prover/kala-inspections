package org.ice1000.kala

import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.JavaElementVisitor
import com.intellij.psi.PsiElementVisitor

class NeedlessCollectInspection : KalaInspection() {
  override fun getDisplayName() = KalaBundle.message("kala.needless-collect.name")
  override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean) = object : JavaElementVisitor() {

  }
}
