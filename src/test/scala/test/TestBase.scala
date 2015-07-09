package test

import org.junit.runner.RunWith
import org.scalatest.{MustMatchers, FunSpec}
import org.scalatest.junit.JUnitRunner

@RunWith(classOf[JUnitRunner])
abstract class TestBase extends FunSpec with MustMatchers {

}
