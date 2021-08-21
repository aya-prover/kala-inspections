package org.ice1000.kala

import com.intellij.codeInspection.*

class KalaInspectionProvider : InspectionToolProvider {
  override fun getInspectionClasses(): Array<Class<out LocalInspectionTool>> = arrayOf(
    MudaInspection::class.java,
    PreferEmptyInspection::class.java,
  )
}

abstract class KalaInspection : LocalInspectionTool() {
  override fun isEnabledByDefault() = true
  final override fun getGroupDisplayName() = "Kala collections"
}
