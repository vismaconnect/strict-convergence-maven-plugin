package nl.ebpi.mavenplugins.ebpienforcer

import test.TestBase

class PathsOfDAGHaveCutSize1Test extends TestBase {
  describe("pathsOfDAGHaveCutSize1") {
    it("returns false when the cut size is >1 (example 1)") {
      val dag = List(List(10, 20, 30), List(11, 21, 31))
      PathsOfDAGHaveCutSize1(dag) mustNot hold
    }
    it("returns false when the cut size is >1 (example 2)") {
      val dag = List(List(10, 20, 30), List(11, 20, 31), List(11, 22, 32))
      PathsOfDAGHaveCutSize1(dag) mustNot hold
    }
    it("returns false when the cut size is >1 (example 3)") {
      val dag = List(List(1, 2), List(2, 3), List(3, 4), List(4,5))
      PathsOfDAGHaveCutSize1(dag) mustNot hold
    }
    it("returns true when the cut size is 1 (example 1)") {
      val dag = List(List(10, 20, 30), List(11, 20, 31))
      PathsOfDAGHaveCutSize1(dag) must hold
    }
    it("returns true when the cut size is 1 (example 2)") {
      val dag = List(List(10, 20, 30), List(11, 20))
      PathsOfDAGHaveCutSize1(dag) must hold
    }
    it("returns true when the cut size is 1 (example 3)") {
      val dag = List(List(10, 20, 30), List(20))
      PathsOfDAGHaveCutSize1(dag) must hold
    }
    it("returns true when the cut size is 1 (example 4)") {
      val dag = List(List(20), List(20))
      PathsOfDAGHaveCutSize1(dag) must hold
    }
    it("returns true when the cut size is 1 (example 5)") {
      val dag = List(List(10, 20, 30), List(11, 20, 31), List(20, 32))
      PathsOfDAGHaveCutSize1(dag) must hold
    }
  }

  val hold = be(right = true)
}
