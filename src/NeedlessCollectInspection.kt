package org.ice1000.kala

import com.intellij.codeInspection.ProblemsHolder

class NeedlessCollectInspection : KalaInspection() {
  override fun getDisplayName() = KalaBundle.message("kala.needless-collect.name")
  override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean) = methodCallVisitor {
  }
}
