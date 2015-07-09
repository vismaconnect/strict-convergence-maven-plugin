package nl.ebpi.mavenplugins.ebpienforcer

import org.apache.maven.model.Dependency

class Assumption {
  var artifact : Dependency = null

  /**
   * Comma separated, whitespace will be trimmed
   */
  var isSufficientFor : String = null
}

class Assumptions (assumptions: Iterable[Assumption]) {

  assumptions.map(assumption => assumption)
}
