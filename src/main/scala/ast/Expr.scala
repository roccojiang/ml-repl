package ml.ast

import scala.language.implicitConversions

import parsley.Parsley
import parsley.implicits.zipped.Zipped2
import parsley.genericbridges.*

import ml.inference.{PolyType, given}
import ml.inference.BasicType.*
import ml.inference.TypeConst.*

sealed trait Expr:
  // TODO: remove redundant parentheses on let and fix
  override def toString: String =
    // Abbreviates consecutive abstractions
    def absStr(e: Expr): String = e match
      case EAbs(EVar(x), e @ EAbs(_, _)) => s" $x${absStr(e)}"
      case EAbs(EVar(x), e)              => s" $x. $e"
      case e                             => s". $e"

    def fixStr(e: Expr): String = e match
      case EFix(g, e) => s"fix $g. $e"
      case e          => letStr(e)

    def letStr(e: Expr): String = e match
      case ELet(x, e1, e2) => s"let $x = ${appStr(e1)} in ${appStr(e2)}"
      case e               => appStr(e)

    def appStr(e: Expr): String = e match
      case EApp(e1, e2) => s"${appStr(e1)} ${varStr(e2)}"
      case e            => varStr(e)

    def varStr(e: Expr): String = e match
      case EVar(x)   => x
      case EConst(c) => c.toString
      case e         => s"($e)"

    this match
      case EAbs(EVar(x), e) => s"λ$x${absStr(e)}"
      case e                => fixStr(e)

case class EVar(x: String) extends Expr
case class EConst(c: TermConst) extends Expr
case class EAbs(x: EVar, e: Expr) extends Expr
case class EApp(e1: Expr, e2: Expr) extends Expr
case class ELet(x: EVar, e1: Expr, e2: Expr) extends Expr
case class EFix(g: EVar, e: Expr) extends Expr

object EVar extends ParserBridge1[String, EVar]
object EConst extends ParserBridge1[TermConst, EConst]
object EAbs extends ParserBridge2[EVar, Expr, EAbs]:
  def apply(xs: Parsley[List[EVar]], e: Parsley[Expr]): Parsley[Expr] =
    (xs, e).zipped(_.foldRight(_)(this.apply))
object EApp extends ParserBridge2[Expr, Expr, EApp]
object ELet extends ParserBridge3[EVar, Expr, Expr, ELet]
object EFix extends ParserBridge2[EVar, Expr, EFix]

given Conversion[String, EVar] = EVar(_)

sealed trait TermConst:
  /** The function 'v', which maps each term constant to its (closed) type. */
  def constType: PolyType =
    val phi: TVar = TVar(0)

    this match
      case _: CInt  => PolyType(TInt)
      case _: CChar => PolyType(TChar)
      case _: CBool => PolyType(TBool)

      case CCond => // ∀φ. Bool -> φ -> φ -> φ
        PolyType(Set(phi), TFun(TBool, TFun(phi, TFun(phi, phi))))
      case CEq => // ∀φ. φ -> φ -> Bool
        PolyType(Set(phi), TFun(phi, TFun(phi, TBool)))
      case CAdd | CSub => // Int -> Int -> Int
        PolyType(TFun(TInt, TFun(TInt, TInt)))

  override def toString: String = this match
    case CInt(x)  => x.toString
    case CChar(c) => s"'$c'"
    case CBool(b) => if b then "True" else "False"

    case c => TermConst.constStrs(c)

object TermConst:
  val constIds: Map[String, TermConst] = Map(
    "Cond" -> CCond,
    "Eq" -> CEq,
    "Add" -> CAdd,
    "Sub" -> CSub
  )

  private val constStrs = constIds.map(_.swap)

case object CCond extends TermConst
case object CEq extends TermConst
case object CAdd extends TermConst
case object CSub extends TermConst

case class CInt(x: BigInt) extends TermConst
case class CChar(c: Char) extends TermConst
case class CBool(b: Boolean) extends TermConst
object CInt extends ParserBridge1[BigInt, CInt]
object CChar extends ParserBridge1[Char, CChar]
object CBool extends ParserBridge1[Boolean, CBool]

given Conversion[TermConst, EConst] = EConst(_)

given Conversion[Int, EConst] = CInt(_)
given Conversion[Char, EConst] = CChar(_)
given Conversion[Boolean, EConst] = CBool(_)
