package org.ice1000.kala

import com.intellij.codeInspection.*
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.psi.*
import com.intellij.psi.util.parentOfTypes
import org.jetbrains.annotations.Nls

class DblityInspection : AbstractBaseJavaLocalInspectionTool() {
  override fun isEnabledByDefault() = true
  override fun getGroupDisplayName() = KalaBundle.message("kala.aya.group.name")

  enum class Kind {
    Inherit, Bound, Closed;

    fun toAnnotationName(): String {
      return "@$this"
    }

    /**
     * Check if [other] is assignable to [this], or in other words, [this] is assignable from [other]
     * @return negative if not assignable, positive if assignable with cast
     */
    fun isAssignable(other: Kind): Int {
      val other = if (other == Inherit) Bound else other
      return other.compareTo(this)
    }
  }

  object DeleteAnnotationFix : LocalQuickFix {
    override fun getFamilyName() = KalaBundle.message("kala.aya.dblity.delete.annotation.fix.name")
    override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
      val annotation = descriptor.psiElement as? PsiAnnotation ?: return
      annotation.delete()
    }
  }

  /// This is not "proper" DFA, just a sequential analysis
  class SequentialDFA(
    val holder: ProblemsHolder,
    val known: MutableMap<TextRange, Kind?> = mutableMapOf()
  ) : JavaElementVisitor() {
    companion object {
      /**
       * DO NOT use this on rhs, as rhs can have [PsiTypes.nullType], see [DblityInspection.SequentialDFA.getKind]
       */
      fun getKind(ty: PsiAnnotationOwner): Kind? = getKind(ty.annotations)

      fun getKind(annotations: Array<out PsiAnnotation>): Kind? {
        // https://github.com/JetBrains/intellij-community/blob/d18a3edba879d572a2e1581bc39ce8faaa0c565c/java/openapi/src/com/intellij/codeInsight/NullableNotNullDialog.java
        val isClosed =
          annotations.any { it.qualifiedName?.endsWith("Closed") == true } // FIXME: don't hard code, make a setting panel, see above
        val isBound =
          annotations.any { it.qualifiedName?.endsWith("Bound") == true } // FIXME: make a inspection that prevents [Closed] and [Bound] annotates the same type
        val isNoInherit =
          annotations.any { it.qualifiedName?.endsWith("NoInherit") == true }

        // When a method is annotated with `NoInherit`, that means the method cannot infer the db-lity of its return type from the receiver.
        // such as the db-lity is depends on its parameters which have complex db-lity, such as `ImmutableSeq<Term>`.
        // For example, `Term#instantiate`
        if (isNoInherit) return null

        return when {
          isClosed -> Kind.Closed
          isBound -> Kind.Bound
          else -> Kind.Inherit
        }
      }
    }

    fun foreplay(parameterList: PsiParameterList) {
      for (param in parameterList.parameters) {
        val kind = param.annotations
        known[param.textRange] = getKind(kind)
      }
    }

    override fun visitCodeBlock(block: PsiCodeBlock) {
      block.statements.forEach { it.accept(this) }
    }

    override fun visitBlockStatement(statement: PsiBlockStatement) {
      visitCodeBlock(statement.codeBlock)
    }

    override fun visitCallExpression(callExpression: PsiCallExpression) {
      val params = callExpression.resolveMethod()?.parameterList?.parameters ?: return
      val args = callExpression.argumentList?.expressions ?: return

      // TODO: deal with vararg
      if (params.size <= args.size) {
        // note that param.isVarArgs iff.not param == params.last()
        var param: PsiParameter? = null

        args.forEachIndexed { idx, arg ->
          if (param == null || !param.isVarArgs) {
            param = params[idx]
          }

          var type = param.type

          if (param.isVarArgs && type is PsiEllipsisType) {
            type = type.componentType
          }

          val kind = getKind(type)
          if (kind != null) {
            doInspect(kind, getKind(arg), arg, holder, true)
          }
        }
      }

    }

    override fun visitAssignmentExpression(expression: PsiAssignmentExpression) {
      if (expression.operationTokenType != JavaTokenType.EQ) return

      // just get kind, left expression is normally not complicate
      val lExpr = expression.lExpression
      val lKind = lExpr.type ?: return
      val expected = getKind(lKind)
      if (expected != null) {
        val rhs = expression.rExpression ?: return
        val actualKind = getKind(rhs)
        known[lExpr.textRange] = expected
        if (expected == actualKind) {
          proposeDeleteAnnotations(lKind.annotations, holder)
        }
        doInspect(expected, actualKind, rhs, holder, false)
      }
    }

    override fun visitDeclarationStatement(statement: PsiDeclarationStatement) {
      statement.declaredElements.forEach { e ->
        if (e is PsiLocalVariable) {
          val initializer = e.initializer ?: return@forEach
          val expected = getKind(e.annotations)
          if (expected != null) {
            val actualKind = getKind(initializer)
            known[e.textRange] = expected
            if (expected == actualKind) {
              proposeDeleteAnnotations(e.annotations, holder)
            }
            doInspect(expected, actualKind, initializer, holder, false)
          }
        }
      }
    }

    override fun visitPatternVariable(variable: PsiPatternVariable) {
      val parent = variable.parentOfTypes(PsiInstanceOfExpression::class, PsiSwitchBlock::class, withSelf = false)
      when (parent) {
        is PsiInstanceOfExpression -> {
          val operadKind = getKind(parent.operand)
          if (operadKind != null && operadKind != Kind.Inherit) {
            proposeDeleteAnnotations(variable.annotations, holder)
          }
          known[variable.textRange] = operadKind
        }

        is PsiSwitchBlock -> {
          val expression = parent.expression
          if (expression != null) {
            val exprKind = getKind(expression)
            if (exprKind != null && exprKind != Kind.Inherit) {
              proposeDeleteAnnotations(variable.annotations, holder)
            }
            known[variable.textRange] = exprKind
          }
        }
      }
    }

    override fun visitSwitchStatement(statement: PsiSwitchStatement) {
      doVisitSwitch(statement)
    }

    override fun visitSwitchExpression(expression: PsiSwitchExpression) {
      doVisitSwitch(expression)
    }

    private fun doVisitSwitch(sw: PsiSwitchBlock) {
      val expression = sw.expression
      expression?.accept(this)
      sw.body?.statements?.forEach {
        it.accept(this)
      }
    }

    override fun visitSwitchLabeledRuleStatement(statement: PsiSwitchLabeledRuleStatement) {
      statement.caseLabelElementList?.accept(this)
      statement.caseLabelElementList?.elements?.forEach {
        if (it is PsiPattern) visitPattern(it)
      }

      statement.body?.accept(this)
    }

    override fun visitCaseLabelElementList(list: PsiCaseLabelElementList) {
      list.elements.forEach {
        it.accept(this)
      }
    }

    override fun visitPattern(pattern: PsiPattern) {
      // dont call visitTypeTestPattern(), visitPattern is considered a fallback of visitTypeTestPattern
      when (pattern) {
        is PsiTypeTestPattern -> {
          visitPatternVariable(pattern.patternVariable ?: return)
        }

        is PsiDeconstructionPattern -> {
          val subpatterns = pattern.deconstructionList.deconstructionComponents
          subpatterns.forEach(this::visitPattern)
        }

        // UnnamedPattern, we don't care
        else -> {}
      }
    }

    override fun visitIfStatement(statement: PsiIfStatement) {
      statement.condition?.accept(this)
      statement.thenBranch?.accept(this)
      statement.elseBranch?.accept(this)
    }

    override fun visitExpressionStatement(statement: PsiExpressionStatement) {
      statement.expression.accept(this)
      println("Expression stmt: ${statement.text}")
    }

    override fun visitExpressionListStatement(statement: PsiExpressionListStatement) {
      statement.expressionList.expressions.forEach { it.accept(this) }
    }

    override fun visitConditionalExpression(expression: PsiConditionalExpression) {
      expression.condition.accept(this)
      expression.thenExpression?.accept(this)
      expression.elseExpression?.accept(this)
    }

    /**
     * Check if [actualKind] is assignable to [expectedKind].
     * @param strict true if treat [Kind.Inherit] as [Kind.Bound] at rhs (actualKind).
     */
    fun doInspect(
      expectedKind: Kind,
      actualKind: Kind?,
      actual: PsiExpression,
      holder: ProblemsHolder,
      strict: Boolean
    ) {
      if (expectedKind == Kind.Inherit || actualKind == null || (!strict && actualKind == Kind.Inherit)) return

      val cmp = expectedKind.isAssignable(actualKind)
      if (cmp < 0) {
        // not assignable
        holder.registerProblem(
          actual,
          KalaBundle.message(
            "kala.aya.dblity.not.assignable",
            "'${actualKind.toAnnotationName()}'",
            "'${expectedKind.toAnnotationName()}'"
          ),
          ProblemHighlightType.WARNING
        )
      } else if (cmp > 0) {
        // assignable with implicit cast
        // TODO: I want to make some highlight, but how?
        // sorry holder
        // FIXME: this seems invisible for some reason, this path is reachable
        holder.registerProblem(
          actual,
          KalaBundle.message("kala.aya.dblity.smart.cast", expectedKind.toAnnotationName()),
          ProblemHighlightType.INFORMATION
        )
      }
    }

    /**
     * @return null if necessary information is missing, the inspection should be stopped.
     */
    fun getKind(expr: PsiExpression): Kind? {
      val ty = expr.type ?: return null
      if (ty == PsiTypes.nullType()) return null

      val basicKind = getKind(ty)
      // if [expr] is already annotated or cannot be used for inferring
      if (basicKind == null || basicKind != Kind.Inherit) return basicKind

      // try get from type definition
      if (ty is PsiClassType) {
        val anno = ty.resolve()?.annotations
        if (anno != null) {
          val defKind = getKind(anno)
          if (defKind != null && defKind != Kind.Inherit) return defKind
        }
      }

      // otherwise, try to infer the real kind
      // this includes:
      // * getter of record
      // * method of some class, like Closure
      when (expr) {
        is PsiParenthesizedExpression -> {
          val innerExpr = expr.expression
          if (innerExpr != null) return getKind(innerExpr)
        }

        is PsiReferenceExpression -> {
          val def = expr.resolve() ?: return basicKind
          // TODO: not all reference can do this,
          //       i.e. all reference that its not define in the method, like Class or Field
          val resolved = known[def.textRange]
          if (resolved != null) return resolved

          // figure out why null
          val ty = expr.type
          if (ty != null) {
            // then the def is not visited by dfa
            val kind = getKind(ty)
            if (kind != null) return kind
          }

          // ty == null || kind == null, then `expr` is either:
          // * some thing which has no type, such as Class,
          // * or some thing which type is unknown cause by syntax error
          // * or marked with NoInherit
          // fall though
        }

        is PsiMethodCallExpression -> {
          val methodExpr = expr.methodExpression
          val receiver = methodExpr.qualifierExpression

          if (receiver != null) {
            val receiverKind = getKind(receiver)
            if (receiverKind != null) {
              // basicKind (the return type of [expr]) is Inherit, and we know the kind of [receiver]
              // thus the real kind of [expr] is the kind of [receiver]

              return receiverKind
            }
          }
        }
      }

      return basicKind
    }

    private fun proposeDeleteAnnotations(
      annotations: Array<out PsiAnnotation>,
      holder: ProblemsHolder
    ) {
      annotations.forEach {
        holder.registerProblem(
          it, KalaBundle.message("kala.aya.dblity.unused.annotation"),
          ProblemHighlightType.LIKE_UNUSED_SYMBOL, it.textRangeInParent,
          DeleteAnnotationFix
        )
      }
    }
  }

  override fun getDisplayName(): @Nls(capitalization = Nls.Capitalization.Sentence) String {
    return KalaBundle.message("kala.aya.dblity")
  }

  override fun buildVisitor(
    holder: ProblemsHolder,
    isOnTheFly: Boolean,
    session: LocalInspectionToolSession
  ): PsiElementVisitor = object : JavaElementVisitor() {
    override fun visitMethod(method: PsiMethod) {
      super.visitMethod(method)
      val body = method.body ?: return
      val dfa = SequentialDFA(holder)
      dfa.foreplay(method.parameterList)
      body.accept(dfa)
    }
  }
}