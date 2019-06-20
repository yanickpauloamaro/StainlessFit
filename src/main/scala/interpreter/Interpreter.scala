package interpreter

import _root_.trees._

import stainless.annotation._
import stainless.collection._
import stainless.lang._

object Interpreter {

  def isValue(e: Tree) = e match {
    case BottomTree => true
    case UnitLiteral => true
    case NatLiteral(_) => true
    case BoolLiteral(_) => true
    case Var(_, _) => true
    case Lambda(_, _) => true
    case _ => false
  }

  def replaceBind(bind: Bind, v: Tree): Tree = {
    bind match {
      case Bind(Some(x), body) => replace(x, v, body)
      case Bind(_, body) => body
    }
  }

  def replace(xvar: Var, v: Tree, body: Tree): Tree = {
    decreases(body)
    val Var(id, x): Var = xvar
    body match {
      case BottomTree => body
      case UnitLiteral => body
      case NatLiteral(n) => body
      case BoolLiteral(b) => body
      case Var(id1, y) if (x == y && id1 == id) => v
      case Var(id1, y) => body
      case IfThenElse(cond, t1, t2) =>
        IfThenElse(
          replace(xvar, v, cond),
          replace(xvar, v, t1),
          replace(xvar, v, t2)
        )
      case App(t1, t2) =>
        App(replace(xvar, v, t1), replace(xvar, v, t2))
      case Tuple(s) => Tuple(s.map(replace(xvar, v, _)))
      case TupleSelect(t, i) => TupleSelect(replace(xvar, v, t), i)
      case LeftTree(t) => LeftTree(replace(xvar, v, t))
      case RightTree(t) => RightTree(replace(xvar, v, t))
      case Because(t1, t2) => Because(replace(xvar, v, t1), replace(xvar, v, t2))
      case NatEq(t1, t2) => NatEq(replace(xvar, v, t1), replace(xvar, v, t2))
      case NatLeq(t1, t2) => NatLeq(replace(xvar, v, t1), replace(xvar, v, t2))
      case Add(t1, t2) => Add(replace(xvar, v, t1), replace(xvar, v, t2))
      case Mul(t1, t2) => Mul(replace(xvar, v, t1), replace(xvar, v, t2))

      case Bind(None(), e) => body
      case Bind(Some(Var(id1, y)), e) if (x == y && id1 == id) => body
      case Bind(yvar, e) => Bind(yvar, replace(xvar, v, e))

      case Lambda(tp, bind) => replace(xvar, v, bind) match {
        case b: Bind => Lambda(tp, b)
        case _ => BottomTree
      }
      case Fix(bind) => replace(xvar, v, bind) match {
        case b: Bind => Fix(b)
        case _ => BottomTree
      }
      case LetIn(tp, v1, bind) =>replace(xvar, v, bind) match {
        case b: Bind => LetIn(tp, v1, b)
        case _ => BottomTree
      }
      case Match(t, t0, bind) => replace(xvar, v, bind) match {
        case b: Bind => Match(replace(xvar, v, t), replace(xvar, v, t0), b)
        case _ => BottomTree
      }
      case EitherMatch(t, bind1, bind2) => (replace(xvar, v, bind1), replace(xvar, v, bind2)) match {
        case (b1: Bind, b2: Bind) => EitherMatch(replace(xvar, v, t), b1, b2)
        case _ => BottomTree
      }

      case _ => body
    }
  }

  def evaluate(e: Tree, fuel: BigInt): Tree = {
    require(fuel >= 0)
    decreases(fuel)
    if(isValue(e)) e
    else if(fuel == 0) BottomTree
    else {
      e match {
        case IfThenElse(c, t1, t2) => {
          val c1: Tree = evaluate(c, fuel - 1)
          c1 match {
            case BoolLiteral(true)  => evaluate(t1, fuel - 1)
            case BoolLiteral(false) => evaluate(t2, fuel - 1)
            case _ => BottomTree
          }
        }
        case App(t1, t2) => {
          val f: Tree = evaluate(t1, fuel - 1)
          val v: Tree = evaluate(t2, fuel - 1)
          f match {
            case Lambda(_, bind) => 
              bind match {
                case b: Bind => evaluate(replaceBind(b, v), fuel - 1)
                case _       => BottomTree
              }
            case _ => BottomTree
          }
        }
        case Tuple(s) => {
          val s1 = s.foldRight(List(): List[Tree]) {
            case (t, r) => evaluate(t, fuel - 1)::r
          }
          Tuple(s1)
        }
        case TupleSelect(t, i) => { // Check if it is a tuple before => lazy evaluation
          val v: Tree = evaluate(t, fuel - 1)
          v match {
            case Tuple(s) => 
              if(i >= 0 && i < s.size) {
                i >= 0 && i < s.size
                s(i)
              }
              else BottomTree
            case _ => BottomTree
          }
        }
        case Fix(bind) => {
          val ret = replaceBind(bind, Lambda(None(), Bind(None(), e)))
          evaluate(ret, fuel - 1)
        }
        case NatEq(e1, e2) => {
          val x: Tree = evaluate(e1, fuel - 1)
          val y: Tree = evaluate(e2, fuel - 1)
          (x, y) match {
            case (NatLiteral(n), NatLiteral(m)) => BoolLiteral(n == m)
            case (_, _) => BottomTree
          }
        }
        case NatLeq(e1, e2) => {
          val x: Tree = evaluate(e1, fuel - 1)
          val y: Tree = evaluate(e2, fuel - 1)
          (x, y) match {
            case (NatLiteral(n), NatLiteral(m)) => BoolLiteral(n <= m)
            case (_, _) => BottomTree
          }
        }
        case Add(e1, e2) => {
          val x: Tree = evaluate(e1, fuel - 1)
          val y: Tree =evaluate(e2, fuel - 1)
          (x, y) match {
            case (NatLiteral(n), NatLiteral(m)) => NatLiteral(n + m)
            case (_, _) => BottomTree
          }
        }
        case Mul(e1, e2) => {
          val x: Tree = evaluate(e1, fuel - 1)
          val y: Tree = evaluate(e2, fuel - 1)
          (x, y) match {
            case (NatLiteral(n), NatLiteral(m)) => NatLiteral(n * m)
            case (_, _) => BottomTree
          }
        }
        case Match(t, t0, bind) => {
          val nat : Tree = evaluate(t, fuel - 1)
          nat match {
            case NatLiteral(n) if(n == 0) => evaluate(t0, fuel - 1)
            case NatLiteral(n) => 
              bind match {
                case b: Bind => evaluate(replaceBind(b, NatLiteral(n - 1)), fuel - 1)
                case _       => BottomTree
              }
            case _ => BottomTree
          }
        }
        case EitherMatch(t, bind1, bind2) => {
          val e1: Tree = evaluate(t, fuel - 1)
          val (e, v): (Tree, Tree) = e1 match {
              case LeftTree(v) => (bind1, evaluate(v, fuel - 1))
              case RightTree(v) => (bind2, evaluate(v, fuel - 1))
              case _ => (BottomTree, BottomTree)
          }
          e match {
            case bind: Bind => evaluate(replaceBind(bind, v), fuel - 1)
            case _ => BottomTree
          }
        }
        case LetIn(tp, v, bind) => evaluate(replaceBind(bind, v), fuel - 1)
        case _ => e
      }
    }
  }
}