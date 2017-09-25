package com.karasiq.gdrive.query

import scala.language.implicitConversions

import com.karasiq.gdrive.query.GApiQuery.Expression._

object GApiQuery {
  sealed trait Expression

  object Expression {
    final case class FieldName(name: String) extends Expression
    final case class Literal(value: Any) extends Expression
    final case class ExpressionList(values: Expression*) extends Expression
    final case class PropertyExpression(properties: Expression*) extends Expression
    final case class Less(left: Expression, right: Expression) extends Expression
    final case class Greater(left: Expression, right: Expression) extends Expression
    final case class LessOrEquals(left: Expression, right: Expression) extends Expression
    final case class GreaterOrEquals(left: Expression, right: Expression) extends Expression
    final case class Equals(left: Expression, right: Expression) extends Expression
    final case class NotEquals(left: Expression, right: Expression) extends Expression
    final case class In(left: Expression, right: Expression) extends Expression
    final case class Has(left: Expression, right: Expression) extends Expression
    final case class Contains(left: Expression, right: Expression) extends Expression
    final case class And(left: Expression, right: Expression) extends Expression
    final case class Or(left: Expression, right: Expression) extends Expression
    final case class Not(expression: Expression) extends Expression
  }

  object DSL {
    implicit class GApiExpressionOps(left: Expression) {
      def ===(right: Expression) = Equals(left, right)
      def !==(right: Expression) = NotEquals(left, right)
      def <(right: Expression) = Less(left, right)
      def >(right: Expression) = Greater(left, right)
      def <=(right: Expression) = LessOrEquals(left, right)
      def >=(right: Expression) = GreaterOrEquals(left, right)
      def in(right: Expression) = In(left, right)
      def has(right: Expression) = Has(left, right)
      def contain(right: Expression) = Contains(left, right)
      def &&(right: Expression) = And(left, right)
      def ||(right: Expression) = Or(left, right)
      def unary_! = Not(left)
      def hasProps(properties: Expression*) = left has PropertyExpression(properties:_*)
    }

    implicit class GApiExpressionSymbolOps(symbol: Symbol) extends GApiExpressionOps(FieldName(symbol.name))

    implicit class GApiExpressionStringOps(string: String) extends GApiExpressionOps(FieldName(string)) {
      def field = FieldName(string)
      def literal = Literal(string)
    }

    implicit def implicitAnyValToGApiLiteral(value: AnyVal): Literal = Literal(value)
    implicit def implicitStringToGApiLiteral(value: String): Literal = value.literal
    implicit def implicitSymbolToGApiField(value: Symbol): FieldName = value.name.field
    implicit def implicitListToGApiExpression[T](seq: Seq[T])(implicit ev: T ⇒ Expression): ExpressionList = ExpressionList(seq.map(ev))
    implicit def implicitGApiExpressionToString(expression: Expression): String = GApiQuery.toString(expression)
  }

  def toString(expression: Expression): String = expression match {
    case FieldName(name) ⇒ name
    case Literal(str: String) ⇒ "'" + escapeStringLiteral(str) + "'"
    case Literal(value) ⇒ value.toString
    case ExpressionList(values @ _*) ⇒ values.map(toString).mkString("[", ", ", "]")
    case PropertyExpression(values @ _*) ⇒ values.map(toString).mkString("{ ", "", " }")
    case Less(left, right) ⇒ toString(left) + " < " + toString(right)
    case Greater(left, right) ⇒ toString(left) + " > " + toString(right)
    case LessOrEquals(left, right) ⇒ toString(left) + " <= " + toString(right)
    case GreaterOrEquals(left, right) ⇒ toString(left) + " >= " + toString(right)
    case Equals(left, right) ⇒ toString(left) + " = " + toString(right)
    case NotEquals(left, right) ⇒ toString(left) + " != " + toString(right)
    case In(left, right) ⇒ toString(left) + " in " + toString(right)
    case Has(left, right) ⇒ toString(left) + " has " + toString(right)
    case Contains(left, right) ⇒ toString(left) + " contains " + toString(right)
    case And(left, right) ⇒ "(" + toString(left) + " and " + toString(right) + ")"
    case Or(left, right) ⇒ "(" + toString(left) + " or " + toString(right) + ")"
    case Not(exp) ⇒ "not " + toString(exp)
  }

  private[this] def escapeStringLiteral(name: String): String = {
    name.replaceAllLiterally("'", "\\'")
  }
}
