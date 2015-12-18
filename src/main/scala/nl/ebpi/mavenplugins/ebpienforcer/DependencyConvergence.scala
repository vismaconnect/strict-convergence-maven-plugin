package nl.ebpi.mavenplugins.ebpienforcer

/**
 * key needed to find projects while searching for conflicting versions. Projects with this key
 * must have the same version, or it will be an error
 *
 * @author adesole
 */
case class ProjectKey(groupId: String, artifactId: String)

/**
 * groupId, artifactId, version
 */
case class Project(key: ProjectKey, version: String)

case class DependentChain(involvedProjects: List[Project]) {

  def tail: DependentChain = DependentChain(involvedProjects.tail)
  def head: Project = involvedProjects.head

  // last is this project being analyzed
  // init.last is direct dependency of this project
  def directDependency : Project = involvedProjects.init.last

  /**
   * Transforms the dependent chain into a dependency chain.
   *
   * reverse.head depends on the reverse.tail
   */
  def reverse: List[Project] = involvedProjects.reverse
}

/**
 * See [[DependencyConvergenceGoal]]
 */
object DependencyConvergence {

  type Conflict = Map[Project, List[DependentChain]]

  def determineConflicts(allDependentChains: List[DependentChain],
                         isAssumedSufficientFor: Map[Project, Project],
                         shallowCheck : Boolean): Iterable[Conflict] = {

    val filterConflicts : Iterable[Conflict] => Iterable[Conflict] = {

      def isShallowConflict(conflict : Conflict) : Boolean = {
        val allPathsToAllVersionsOfDisputedArtifact = conflict.values.flatten

        // Strip the disputed node and this project from all paths
        val intermediatePaths = allPathsToAllVersionsOfDisputedArtifact.map(_.involvedProjects.tail.init)

        // Conflicts that are not relevant can be eliminated by removing one
        // node from the dependency graph. If that is the case, the conflict
        // is 'deep' and therefore not shallow.
        !PathsOfDAGHaveCutSize1(intermediatePaths)
      }

      if (shallowCheck) _.filter(isShallowConflict) else identity
    }

    def toBeReplacedBy(someProject: Project): Project = {
      isAssumedSufficientFor.getOrElse(someProject, someProject)
    }

    /**
     * Partitions a list of chains by the project *key* that the chain starts from.
     * This groups together possible conflicts, because the version is not included in the partitioning key.
     */
    def byKey(chains: List[DependentChain]): Map[ProjectKey, List[DependentChain]] = {
      chains.groupBy(_.involvedProjects.head.key)
    }


    def allConflicts: Iterable[Conflict] = {
      val allChains = allDependentChains
      val groupedProjectsWithDependents = byKey(allChains).values
      groupedProjectsWithDependents.map(conflictOption).flatMap(Option.option2Iterable)
    }

    /**
     * Checks if the list of chains starts with the same project (version).
     * Returns a map of sides.
     *
     * @param chains chains such that for any elements p and q in chains, p.key == q.key
     * @return possible conflict
     */
    def conflictOption(chains: List[DependentChain]): Option[Conflict] = {
      val sides = chains.groupBy(_.involvedProjects.head)

      val sidesAfterAssumptions = (sides.keys map toBeReplacedBy).toSet

      if (sidesAfterAssumptions.size > 1)
        Some(sides)
      else
        None
    }

    def shrinkConflicts(allConflicts: Iterable[Conflict]): Iterable[Conflict] = {

      // Remove one layer of failed dependencies to see if a smaller subproblem
      // can be presented to the user without loss of information. (Problems near the root must be resolved first)
      def stripConflict: Conflict => Option[Conflict] = { conflict =>
        val strippedChains = conflict.values.flatten.map(_.tail).toList
        conflictOption(strippedChains)
      }

      val allConflictsSet = allConflicts.toSet
      allConflicts filter (stripConflict andThen {
        case None => true // no simpler further conflict, so we need to show this one
        case Some(strippedConflict) => // forget about this one if a simpler conflict is already included
          !allConflictsSet.contains(strippedConflict)
      })
    }

    (filterConflicts andThen shrinkConflicts)(allConflicts)
  }

}
