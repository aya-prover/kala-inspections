package org.ice1000.kala

import com.intellij.codeInspection.ProblemsHolder

class ViewToMapInspection : KalaInspection() {
  override fun getDisplayName() = KalaBundle.message("kala.view-to-map.name")
  override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean) = methodCallVisitor { call ->
  }
}
