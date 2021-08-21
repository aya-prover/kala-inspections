package org.ice1000.kala

import com.intellij.AbstractBundle
import com.intellij.codeInspection.InspectionToolProvider
import com.intellij.codeInspection.LocalInspectionTool
import org.jetbrains.annotations.NonNls
import org.jetbrains.annotations.PropertyKey
import java.util.*

class KalaInspectionProvider : InspectionToolProvider {
  override fun getInspectionClasses(): Array<Class<out LocalInspectionTool>> = arrayOf(
    MudaInspection::class.java,
    PreferEmptyInspection::class.java,
    FuseImmSeqInspection::class.java,
  )
}

const val PKG = "kala.collection"
const val INM_PKG = "$PKG.immutable"
const val MU_PKG = "$PKG.mutable"
const val IMMUTABLE_SEQ = "$INM_PKG.ImmutableSeq"

abstract class KalaInspection : LocalInspectionTool() {
  override fun isEnabledByDefault() = true
  final override fun getGroupDisplayName() = KalaBundle.message("kala.group.name")
}

object KalaBundle {
  @NonNls private const val BUNDLE = "kala.kala-bundle"
  private val bundle: ResourceBundle by lazy { ResourceBundle.getBundle(BUNDLE) }

  fun message(@PropertyKey(resourceBundle = BUNDLE) key: String, vararg params: Any) =
    AbstractBundle.message(bundle, key, *params)
}
