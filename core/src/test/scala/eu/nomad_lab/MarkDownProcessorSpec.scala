package eu.nomad_lab

import org.pegdown.PegDownProcessor
import org.specs2.mutable.Specification

class MarkDownProcessorSpec extends Specification{

  "markDownProcessor" in {
    val strList = Set("cat", "ground")
    val testString: String =
      """ cat is going in two the ground, cat is playing
        |### heading3
        |## heading2
        |#heading $x^2*r*1$
        |\[ x^2 *r* 1 < 4 > 7 \]
        |`\[ x^2 *r* 1 < 4 > 7 \]`
        |\\\\
        | <a href="/yrl/url"> text </a>""".stripMargin
    MarkDownProcessor.processMarkDown(testString,strList, (s: String) => "(link:" + s + ")" ) must_==
      "<p>(link:cat) is going in two the (link:ground), (link:cat) is playing</p>"

  }
}
