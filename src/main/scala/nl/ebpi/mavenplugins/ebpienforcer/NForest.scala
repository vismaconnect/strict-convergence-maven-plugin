package nl.ebpi.mavenplugins.ebpienforcer

import scala.annotation.tailrec

/**
 * A bunch of n-trees / rose trees / ...
 * Trees with any number of children per node
 *
 * @param trees the trees
 * @tparam K key type that identifies a child
 */
final case class NForest[K](trees : Map[K,NForest[K]]) {
  import NForest._

  def updated(path : Seq[K]): NForest[K] = path match {
    case Nil => this
    case a :: b => NForest(
                            mapUpdate[K,NForest[K]](trees, a, _.getOrElse(NForest(Map.empty[K,NForest[K]])
                          ).updated(b)))
  }
  def fold[T](f : Map[K,T] => T) : T = {
    val x : NForest[K] => T = _.fold(f)
    f(trees.mapValues(x))
  }
}
object NForest {
  def empty[A] : NForest[A] = NForest(Map.empty)

  private def mapUpdate[K,V](in : Map[K,V], key : K, value : Option[V] => V) : Map[K,V] = {
    if (in.contains(key)) {
      in.updated(key, value(Some(in(key))))
    } else {
      in.updated(key, value(None))
    }
  }
}
