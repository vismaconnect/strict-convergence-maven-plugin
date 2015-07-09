package nl.ebpi.mavenplugins.ebpienforcer

import scala.collection.mutable
import scala.runtime.AbstractFunction1

/**
 * Remembers outputs for all inputs it receives.
 *
 * @tparam A The type of input values
 * @tparam K A type whose values will be unique for equivalent input values. This can be equal to A.
 * @tparam B The type of output values
 *
 * @param work The expensive procedure
 * @param key This function will be used to determine the map keys that will be used to find remembered values
 *            It should be pure and it should map non-equivalent `A`s tot non-equal `B`s
 */
class Memo[A,K,B](work: A => B, key : A => K) extends AbstractFunction1[A,B] {
  val memoTable : mutable.Map[K,B] = mutable.Map[K,B]()

  override def apply(v: A): B = {
    val k = key(v)
    memoTable.getOrElse(k, {
      val out = work(v)
      memoTable.put(k, out)
      out
    })
  }
}
object Memo {
  def apply[A,B](work : A => B) : AbstractFunction1[A,B] = {
    new Memo(work, identity)
  }
  def apply[A,K,B](work : A => B, key : A => K) : AbstractFunction1[A,B] = {
    new Memo(work, key)
  }
}