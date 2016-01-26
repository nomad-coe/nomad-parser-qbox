package eu.nomad_lab

import org.pegdown.PegDownProcessor
import org.specs2.mutable.Specification

//TODO: Write more test
class MarkDownProcessorSpec extends Specification{
  val strList = Set("cat", "ground", "list", "link2")

  val testCases = Seq( ("str", "<p>str</p>"),
    ("# heading", "<h1>heading</h1>"), //Simple Markdown
    ("## heading2","<h2>heading2</h2>"), //Simple Markdown
    ("[some Random String]", "<p>[some Random String]</p>"), //Square Bracker without link; no change expected
    ("[String](link)", """<p><a href="link">String</a></p>"""),
    ("some Random [String](link)", """<p>some Random <a href="link">String</a></p>"""),
    ("""escape Math `\[ x^2 *r* 1 < 4 > 7 \]`""","""<p>escape Math <code>\[ x^2 *r* 1 &lt; 4 &gt; 7 \]</code></p>"""),
    ("""escape Math2 $ x^2 *r* 1 < 4 > 7 $""","""<p>escape Math2 $ x^2 *r* 1 &lt; 4 &gt; 7 $</p>"""),
    ("cat","""<p>(link:cat)</p>"""),
    ("""\\\\""","""<p>\\</p>"""),
    ("""\\ cat list random \\""","""<p>\ (link:cat) (link:list) random \</p>"""),
    ("""\\ $ cat list random $ \\""","""<p>\ $ cat list random $ \</p>"""),
    ("""\\ [ cat list random ](link) \\""","""<p>\ <a href="link"> cat list random </a> \</p>"""),
    ("""\\ [ cat list random ] \\""","""<p>\ [ cat list random ] \</p>"""),
    ("""\\ < cat \\ "list" random > \\""","""<p>\ &lt; cat \ "list" random &gt; \</p>"""),
    ("""1.  Bird
       |2.  McHale
       |3.  Parish""".stripMargin,
    """<ol>
      |  <li>Bird</li>
      |  <li>McHale</li>
      |  <li>Parish</li>
      |</ol>""".stripMargin ), //Complex Mardown
    (""" "tagString$tag  [ ] < > list" ""","""<p>"tagString$tag [ ] &lt; &gt; (link:list)" </p>""")

  )

  val falseCases = Seq( //Test cases that should not pass
    ("""escape Math \[ x^2 *r* 1 < 4 > 7 \]""","""<p>escape Math <code>\[ x^2 *r* 1 &lt; 4 &gt; 7 \]</code></p>"""),
    ("simple String", "simple String"),
    ("""\\ $ cat list $ \\""","""<p>\ $ (link:cat) (link:list) $ \</p>"""),
    ("""\\ $ cat list $ \\""","""<p>\ [ (link:cat) (link:list) ] \</p>""")
  )
  "markDownProcessor" in {

    val testString: String =
      """ cat is going in two the ground, cat is playing """.stripMargin


    testCases.foreach {
     case (inp, out) => MarkDownProcessor.processMarkDown(inp, strList, (s: String) => "(link:" + s + ")") must_==out
     case _ => ()
    }
    falseCases.foreach {
      case (inp, out) => MarkDownProcessor.processMarkDown(inp, strList, (s: String) => "(link:" + s + ")") must_!=out
      case _ => ()
    }
    MarkDownProcessor.processMarkDown(testString,strList, (s: String) => "(link:" + s + ")" ) must_==
      "<p>(link:cat) is going in two the (link:ground), (link:cat) is playing </p>"
  }
}
