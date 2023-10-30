package org.ice1000.kala

import com.intellij.AbstractBundle
import com.intellij.codeInspection.InspectionToolProvider
import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.psi.JavaElementVisitor
import com.intellij.psi.PsiMethodCallExpression
import com.intellij.psi.PsiNewExpression
import org.jetbrains.annotations.NonNls
import org.jetbrains.annotations.PropertyKey
import java.util.*

class KalaInspectionProvider : InspectionToolProvider {
  override fun getInspectionClasses(): Array<Class<out LocalInspectionTool>> = arrayOf(
    MudaInspection::class.java,
    PreferEmptyInspection::class.java,
    FuseImmSeqInspection::class.java,
    NeedlessCollectInspection::class.java,
    TupleOfInspection::class.java,
    ViewSizeInspection::class.java,
    SizeCompareInspection::class.java,
    MapPutUnetaInspection::class.java,
    ViewToMapInspection::class.java,
    SamenessInspection::class.java
  )
}

const val PKG = "kala.collection"
const val INM_PKG = "$PKG.immutable"
const val MU_PKG = "$PKG.mutable"
const val IMMUTABLE_SEQ = "$INM_PKG.ImmutableSeq"
const val TU_PKG = "kala.tuple"

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

inline fun methodCallVisitor(crossinline f: (PsiMethodCallExpression) -> Unit) =
  object : JavaElementVisitor() {
    override fun visitMethodCallExpression(expression: PsiMethodCallExpression) = f(expression)
  }

inline fun newVisitor(crossinline f: (PsiNewExpression) -> Unit) =
  object : JavaElementVisitor() {
    override fun visitNewExpression(expression: PsiNewExpression) = f(expression)
  }

val INM_CLZ_FACTORIES = listOf(
  "$PKG.Seq" to "toSeq",
  IMMUTABLE_SEQ to "toImmutableSeq",
  "$INM_PKG.ImmutableArray" to "toImmutableArray",
  "$INM_PKG.ImmutableVector" to "toImmutableVector",
  "$INM_PKG.ImmutableLinkedSeq" to "toImmutableLinkedSeq",
  "$INM_PKG.ImmutableSizedLinkedSeq" to "toImmutableSizedLinkedList",
)
