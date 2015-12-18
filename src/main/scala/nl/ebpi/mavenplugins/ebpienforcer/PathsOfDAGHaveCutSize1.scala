package nl.ebpi.mavenplugins.ebpienforcer

/**
 * Compute whether a single node cuts all paths of a directed acyclic graph
 *
 * https://en.wikipedia.org/wiki/Minimum_cut
 */
object PathsOfDAGHaveCutSize1 {
  /**
   * Compute whether a single node cuts all paths of a directed acyclic graph
   *
   * https://en.wikipedia.org/wiki/Minimum_cut
   */
  def apply[A](paths: IndexedSeq[Set[A]]): Boolean = {
    paths match {
      case IndexedSeq() => false
      case somePath +: otherPaths =>
        somePath exists { someNode =>
          otherPaths forall { otherPath =>
            otherPath contains someNode
          }
        }
    }
  }

  /**
   * Compute whether a single node cuts all paths of a directed acyclic graph
   *
   * https://en.wikipedia.org/wiki/Minimum_cut
   */
  def apply[A](paths: Iterable[Iterable[A]]): Boolean = {
    apply(paths.map(_.toSet)(collection.breakOut) : IndexedSeq[Set[A]])
  }
}
