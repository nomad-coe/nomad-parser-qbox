package eu.nomad_lab.webservice

import akka.actor.Actor
import spray.routing._
import spray.http._
import MediaTypes._
import org.{json4s => jn}
import org.json4s.native.JsonMethods
import org.json4s.JsonDSL._
import org.json4s.jackson.JsonMethods._
import scala.collection.mutable.ArrayBuffer
import scala.collection.{mutable, breakOut}
import java.io.File
import java.nio.file.Paths
import java.nio.file.Files
import java.nio.charset.StandardCharsets
import eu.nomad_lab.meta.KnownMetaInfoEnvs
import eu.nomad_lab.meta.MetaInfoEnv
import eu.nomad_lab.meta.MetaInfoRecord
import eu.nomad_lab.meta.MetaInfoCollection
import eu.nomad_lab.meta.RelativeDependencyResolver
import eu.nomad_lab.meta.SimpleMetaInfoEnv
import eu.nomad_lab.{MarkDownProcessor, JsonSupport, JsonUtils}
import com.typesafe.scalalogging.StrictLogging
import org.json4s.{JNothing, JNull, JBool, JDouble, JDecimal, JInt, JString, JArray, JObject, JValue, JField}
import scala.util.matching.Regex

object Stats{
  class StatInfo(val parserName: String, val parserVersion: String, val parserInfo: JValue) {
    val stats: mutable.Map[String, Int] = mutable.Map()

    def append(data: JValue): Boolean = {
      val name = data \ "parser" \ "name"
      val version = data \ "parser" \ "version"
      val newD = data \ "data"
      newD match {
        case JObject(obj) =>
          obj.foreach { case (k, v) =>
            v match {
              case JInt(i) =>
                stats += (k -> (i.intValue + stats.getOrElse(k, 0: Int)))
              case _ => ()
            }
          }
          return true
        case _ => ()
      }
      false
    }

    def toJValue: JValue = {
      JObject(
        ("parser" -> parserInfo) ::
          ("data" -> JObject(stats.map { case (k,v) => k -> JInt(v) }.toList)) :: Nil
      )
    }
        
  }

  class AllStats {
    val stats: mutable.Map[String, StatInfo] = mutable.Map()
    
    def append(data: JValue): Boolean = {
      val name = data \ "parser" \ "name"
      val version = data \ "parser" \ "version"
      name match {
        case JString(s) =>
          version match {
            case JString(s2) =>
              val fullName = s + " " + s2
              stats.get(fullName) match {
                case Some(stat) =>
                  return stat.append(data)
                case None =>
                  val newD = new StatInfo(s,s2, data \ "parser")
                  stats += (fullName -> newD)
                  return newD.append(data)
              }
            case _ => ()
          }
        case _ => ()
      }
      false
    }

    def toJValue: JValue = {
      JObject( stats.map { case (k,v) =>
        k -> v.toJValue }.toList
      )
    }
  }
  
  val myStats = new AllStats

}


// we don't implement our route structure directly in the service actor because
// we want to be able to test it independently, without having to spin up an actor
class NomadMetaInfoActor extends Actor with NomadMetaInfoService {

  lazy val metaInfoCollection: MetaInfoCollection = KnownMetaInfoEnvs

  // the HttpService trait defines only one abstract member, which
  // connects the services environment to the enclosing actor or test
  def actorRefFactory = context

  // this actor only runs our route, but you could add
  // other things here, like request stream processing
  // or timeout handling
  def receive = runRoute(myRoute)
}


// this trait defines our service behavior independently from the service actor
trait NomadMetaInfoService extends HttpService with StrictLogging {

  implicit val formats = JsonSupport.formats

  /** Connection to the known meta infos
    */
  def metaInfoCollection: MetaInfoCollection;

  lazy val mainCss: String = {
    val classLoader: ClassLoader = getClass().getClassLoader();
    val filePath = classLoader.getResource("css/main.css").getFile()
    new String(Files.readAllBytes(Paths.get(filePath)), StandardCharsets.UTF_8)
  }

  def layout(title: String, extraHead: Stream[String], content: Stream[String]): Stream[String] = {
    """<!DOCTYPE html>
<html>
 <head>
  <meta charset="UTF-8">
  <link rel="stylesheet" type="text/css" href="/nmi/css/nomadBase.css">
  <title>""" #:: title #:: """</title>
""" #:: extraHead #::: """
 </head>
 <body>
""" #:: content #::: """
 </body>
</html>
""" #:: Stream.empty
  }

  def versionsHtml: Stream[String] = {
    //import com.dongxiguo.fastring.Fastring.Implicits._
    val versions = metaInfoCollection.allUniqueEnvs{ env: MetaInfoEnv =>
      env.kind == MetaInfoEnv.Kind.Version }.toList.groupBy{ env: MetaInfoEnv => env.name }
    val validVersions = versions.flatMap{ case (i,l) => if (l.length == 1) Some(i) else None }
    val problematicVersions = versions.flatMap{ case (i,l) => if (l.length != 1) Some(i) else None }

    val versionsH = validVersions.map{ version: String =>
      s"""<li><a href="v/${version}/info.html">${version}</a></li>""".toString
    }.toStream
    layout("Nomad Meta Info Versions", Stream.empty,
      """    <h1>Nomad Meta Info Versions</h1>
    <ul>""" #:: versionsH #::: "</ul>" #:: Stream.empty)
  }

  def versionsJson: jn.JValue = {
    val versions = metaInfoCollection.allUniqueEnvs{ env: MetaInfoEnv =>
      env.kind == MetaInfoEnv.Kind.Version }.toList.groupBy{ env: MetaInfoEnv => env.name }
    val validVersions = versions.flatMap{ case (i,l) => if (l.length == 1) Some(i) else None }
    val problematicVersions = versions.flatMap{ case (i,l) => if (l.length != 1) Some(i) else None }

    jn.JObject(jn.JField("type", "nomad_meta_versions_1_0") ::
      jn.JField("versions", validVersions) ::
      jn.JField("problematicVersions", problematicVersions) :: Nil)
  }

  def versionJson(version: String): jn.JValue = {
    val versions = metaInfoCollection.versionsWithName(version)
    if (!versions.hasNext)
      jn.JNull
    else {
      val v = versions.next
      v.toJValue(_ => jn.JNothing, selfGid = true, superGids = true)
    }
  }

  // should be changed so that Http error status can be returned
  def versionHtml(version: String): Stream[String] = {
    val versions = metaInfoCollection.versionsWithName(version)
    if (!versions.hasNext) {
      layout(s"Unknown version", Stream.empty,
        Stream(s"Version $version unknown"))
    } else {
      val v = versions.next
      val names = v.allNames.toSet.toSeq.sorted.map{ name: String =>
        s"""<li><a href="n/${name}/info.html">${name}</a></li>"""
      }.toStream

      layout(s"Nomad Meta Info Version $version", Stream.empty,
        s"""
  <h1>Nomad Meta Info <a href="../../info.html">${version}</a></h1>
  <p class="description">${v.description}</p>
  <h2>Meta Infos</h2>
  <ul>
""" #:: names #::: """
  </ul>""" #:: Stream.empty)
    }
  }

  def metaInfoForVersionAndNameJson(version: String, name: String): jn.JValue = {
    val versions = metaInfoCollection.versionsWithName(version)
    if (!versions.hasNext)
      jn.JNull
    else {
      val v = versions.next
      val metaInfo = v.metaInfoRecordForName(name, selfGid = true, superGids = true)
      metaInfo match {
        case Some(r) => r.toJValue()
        case None => jn.JNull
      }
    }
  }


  /** Create JSON with all meta tag. Contains complete data including ancestors and children
    */
  def annotatedVersionJson(version: String): jn.JValue = {
    val versions = metaInfoCollection.versionsWithName(version)
    if (!versions.hasNext)
      jn.JNull
    else {
      val v = versions.next
      val mInfo = v.toJValue(_ => jn.JNothing)
      val nameList = v.allNames.toList
      var daList: List[jn.JValue] = Nil
      for(name <- nameList){
        daList = metaInfoForVersionAndNameJsonAnnotatedInfo(version, name) :: daList
      }
      jn.JObject(
        jn.JField("type",mInfo.\("type")) ::
        jn.JField("name",mInfo.\("name")) ::
        jn.JField("description",mInfo.\("description")) ::
        jn.JField("dependencies",mInfo.\("dependencies")) ::
        jn.JField("metaInfos", daList) :: Nil
      )
    }
  }


  /** Create JSON for the "name" meta tag. Contains complete data including ancestors and children
    */
  def metaInfoForVersionAndNameJsonAnnotatedInfo (version: String, name: String): jn.JValue = {
    val versions = metaInfoCollection.versionsWithName(version)
    if (!versions.hasNext)
      jn.JNull
    else {
      val v = versions.next
      val metaInfo = v.metaInfoRecordForName(name, selfGid = true, superGids = true)
      metaInfo match {
        case Some(r) => //r.toJValue()
          val superNames = ArrayBuffer[String]()
          if (!r.superNames.isEmpty) {
            if (r.superGids.length == r.superNames.length) {
              r.superNames.zipWithIndex.foreach{ case (sName, i) =>
                superNames += sName }
            }else {
              r.superNames.foreach{ sName: String =>
                superNames += sName
              }
            }
          }
          val dtypeStr = r.dtypeStr match {
            case Some(str) =>
            jn.JString(str match {
            case "f" => "f (floating point value)"
            case "i" => "i (integer value)"
            case "f32" => "f32 (single precision float)"
            case "i32" => "i32 (32 bit integer)"
            case "f64" => "f64 (double precision floating point)"
            case "i64" => "i64 (64 bit integer)"
            case "b"   => "b (boolean value)"
            case "B"   => "B (variable length byte array i.e. a blob)"
            case "C"   => "C (a unicode string)"
            case "D"   => "D (a json dictionary)"
            case  v    => s"$v (unknown type)"
          })
          case None =>
            jn.JNothing
          }
          def hrefCreate(x:String):String =  {
            if(x==name)
               s"""<em>$x</em>"""
            else
               s"""<a href="#/$version/$x"> $x </a> """
          }
          val metaShape = r.shape match {
                case None => jn.JNothing
                case Some(s) =>
                  val listShape: List[jn.JValue] = s.map {
                    case Left(i) => jn.JInt(i)
                    case Right(s) => jn.JString(hrefCreate(s))
                  }(breakOut)
                  jn.JArray(listShape)
              }
          val children = v.allDirectChildrenOf(name).toList
          var allParents:List[String] = Nil
          v.metaInfoRecordForNameWithAllSuper(name, selfGid = true, superGids = false).foreach {
            (metaInfo: MetaInfoRecord) => allParents =  (metaInfo.name) :: allParents }
          val rootSectionAncestors = v.firstAncestorsByType(name).get("type_section") match {
            case Some((rootSections,_)) =>
              rootSections.toSeq
            case None =>
              Seq()
          }
          val descriptionHTML = MarkDownProcessor.processMarkDown(r.description,v.allNames.toSet,hrefCreate);

          jn.JObject(jn.JField("type", "nomad_meta_versions_1_0") ::
            jn.JField("versions", version) ::
            jn.JField("name", name) ::
            jn.JField("description",descriptionHTML) ::
            jn.JField("gid", r.gid) ::
            jn.JField("units", r.units) ::
            jn.JField("dtypeStr", dtypeStr) ::
            jn.JField("kindStr", r.kindStr) ::
            jn.JField("superNames", superNames) ::
            jn.JField("children", children) ::
            jn.JField("allparents", allParents ) ::
            jn.JField("rootSectionAncestors", rootSectionAncestors ) ::
            ("shape" -> metaShape) ::
            Nil ) 
        case None => jn.JNull
      }
    }
  }

  def addStatsStr(content: String):String = {
    //logger.info(s"got data: '$content'")
    addStats(JsonUtils.parseStr(content))
  }

  def addStats(newData: JValue):String = {
    if (Stats.myStats.append(newData))
      "stats added\n"
    else
      "stats skipped\n"
  }

  def overviewStats: JValue = {
    Stats.myStats.toJValue
  }

  def details(i: Int): JValue = {
    val keys: Array[String] = Stats.myStats.stats.keys.toArray.sorted
    if (i >= 0 && i < keys.size)
      Stats.myStats.stats(keys(i)).toJValue
    else
      JNull
  }

  def overviewStatsHtml: Stream[String] = {
    val keys: Array[(String, Int)] = Stats.myStats.stats.keys.toArray.sorted.zipWithIndex
    val parserStats = keys.map { case (p, i) =>
      val pStats = Stats.myStats.stats(p)
      val nFiles = pStats.stats.getOrElse("section_run", 0: Int)
      val nTotalEnergies = pStats.stats.getOrElse("totalDftEnergyT0", 0: Int)
      val nBands = pStats.stats.getOrElse("band_segm_labels", 0: Int)
      val nGeometries = pStats.stats.getOrElse("CoreConfiguration", 0: Int)
      val nKeys = pStats.stats.size
      val nData = pStats.stats.values.foldLeft(0:Int)(_ + _)
      s"""<tr><td>${pStats.parserName}</td><td>${pStats.parserVersion}</td><td>$nFiles</td><td>$nGeometries</td><td>$nTotalEnergies</td><td>$nBands</td><td>$nKeys</td><td>$nData</td><td><a href="details/$i/info.json">details</a></td></tr>"""}.toStream

    layout(
      title = "Parsers Overview",
      extraHead = Stream("""<style>
th
{border:1px solid black; padding:5px;}
td
{border:1px solid black; padding:5px;}
table
{border:1px solid black;}
</style>"""),
      content = s"""
<h1>Nomad Parsers Overview</h1>

 <ul>
  <li><a href="https://gitlab.rzg.mpg.de/nomad-lab/nomad-lab-base/wikis/Parser-Assignment">Parser assignements</a></li>
 </ul>

<h2>Statistics</h2>
  <table >
  <tr><th>
    Code
  </th><th>
    Parser Version
  </th><th>
    #Files
  </th><th>
    #Geometries
  </th><th>
    #Energies
  </th><th>
    #Band Structures
  </th><th>
    Type of Quantities
  </th><th>
    Total Quantities
  </th><th>
    Extra Info
  </th></tr>
""" #:: parserStats #::: """
  </ul>""" #:: Stream.empty)
  }

  def detailedStats: JValue = {
    JNull
  }


  /** Create JSON containing information graph information, for the "name" meta tag. Contains complete data including ancestors and children
    */
  def metaInfoForVersionAndNameJsonAllParents (version: String, name: String): jn.JValue = 
  {
    val versions = metaInfoCollection.versionsWithName(version)
    if (!versions.hasNext)
      jn.JNull
    else {
      val v = versions.next
      var nodes: List[jn.JValue] = Nil
      var edges: List[jn.JValue] = Nil
      v.metaInfoRecordForNameWithAllSuper(name, selfGid = true, superGids = false).foreach {
        (metaInfo: MetaInfoRecord) =>
          nodes = jn.JObject(
            ("data" -> jn.JObject(
              ("id" -> jn.JString(metaInfo.name)) :: Nil)) ::
            ("style" -> JObject(
              ("background-color" -> jn.JString(metaInfo.kindStr match {
              case "type_document_content" => "#333333"
              case "type_unknown" => "#00EE00"
              case "type_unknown_meta" => "#00EE00"
              case "type_document" => "#A0A0A0"
              case "type_meta" => "#0000EE"
              case "type_abstract_document_content" => "#00AAAA"
              case "type_section" => "#EE0000"
              case "type_connection" => "#AA1111"
              case "type_dimension" => "#EE00EE"
              case _ => "#1111AA"
            })) ::
            ("shape" -> (if (metaInfo.name == name)
               jn.JString("star")
              else
               jn.JNothing)) :: Nil)) :: Nil) :: nodes
          val rootsByKind = v.firstAncestorsByType(metaInfo.name)
          rootsByKind.foreach {   
            case (kindNow, (roots, _)) =>
              if (metaInfo.name == name || metaInfo.kindStr == kindNow) {
                roots.foreach { (root: String) =>
                  edges = jn.JObject(
                      ("data" ->
                       jn.JObject(
                          ("source" -> jn.JString(metaInfo.name)) ::
                          ("target" -> jn.JString(root)) :: Nil
                        )
                      ) :: Nil
                    ) :: edges
                }
              }
          }
      }
      val rootMetaInfo = v.metaInfoRecordForName(name).get
      val nChilds = v.allDirectChildrenOf(name).length
      for (child <- v.allDirectChildrenOf(name)) {
        val metaInfo = v.metaInfoRecordForName(child).get
        if (nChilds <= 5 || metaInfo.kindStr == rootMetaInfo.kindStr) {
          nodes = jn.JObject(
            ("data" -> jn.JObject(
              ("id" -> jn.JString(metaInfo.name)) :: Nil)) ::
            ("style" -> JObject(
              ("background-color" -> jn.JString(metaInfo.kindStr match {
              case "type_document_content" => "#333333"
              case "type_unknown" => "#00EE00"
              case "type_unknown_meta" => "#00EE00"
              case "type_document" => "#A0A0A0"
              case "type_meta" => "#0000EE"
              case "type_abstract_document_content" => "#00AAAA"
              case "type_section" => "#EE0000"
              case "type_connection" => "#AA1111"
              case _ => "#EE00EE"
            })) :: Nil)) :: Nil) :: nodes
          edges = jn.JObject(
                      ("data" ->
                       jn.JObject(
                          ("source" -> jn.JString(child)) ::
                          ("target" -> jn.JString(name)) :: Nil
                        )
                      ) :: Nil
                    ) :: edges
        }
      }
      jn.JObject(
        ("nodes" -> jn.JArray(nodes)) ::
        ("edges" -> jn.JArray(edges)) :: Nil)
    }
  }


  def metaInfoForVersionAndNameHtml(version: String, name: String): Stream[String] = {
    val versions = metaInfoCollection.versionsWithName(version)
    if (!versions.hasNext)
      layout(s"Unknown version", Stream.empty, Stream(s"Version $version unknown"))
    else {
      val v = versions.next
      val metaInfo = v.metaInfoRecordForName(name, selfGid = true, superGids = true)
      metaInfo match {
        case Some(r) =>
          val data = mutable.ListBuffer[String]()
          val start = s"""
  <div class="rightBanner"><span class="kindStr"><a href="../${r.kindStr}/info.html">${r.kindStr}</a></span> <span class="gid">${r.gid}</span></div>
  <h1><a href="../../info.html">$version</a>/$name</h1>
  <p class="description">${r.description}</p>
  <h2>Keys</h2>
  <div class="indent">"""
          data.append(start)
          if (!r.units.isEmpty)
            data.append(s"""
   <p><span class="key">units:</span> <span class="value">${r.units.get}</span></p>""")
          if (!r.repeats.isEmpty && r.repeats.get)
            data.append(s"""
   <p><span class="key">repeats:</span> <span class="value">true</span></p>""")
          if (!r.dtypeStr.isEmpty)
            data.append(s"""
   <p><span class="key">dtypeStr:</span> <span class="value">${r.dtypeStr.get}</span></p>""")
          if (!r.shape.isEmpty)
            data.append(s"""
   <p><span class="key">shape:</span> <span class="value">${r.shape.get.mkString("[",", ","]")}</span></p>""")
          if (!r.otherKeys.isEmpty)
            r.otherKeys.foreach { case jn.JField(key, value) =>
              data.append(s"""
   <p><span class="key">$key:</span> <span class="value">${JsonSupport.writePrettyStr(value)}</span></p>""")
            }
          data.append("""
  </div>""")
          if (!r.superNames.isEmpty) {
            data.append("""
  <h2>Ancestors</h2>
  <div class="indent">
   <h3>Explicit parents</h3>
   <div class="indent">""")

            if (r.superGids.length == r.superNames.length) {
              r.superNames.zipWithIndex.foreach{ case (sName, i) =>
                data.append(s"""
    <span title="${r.superGids(i)}"><a href="../$sName/info.html">$sName</a></span>""")
              }
            } else {
              r.superNames.foreach{ sName: String =>
                data.append(s"""
    <a href="../$sName/info.html">$sName</a>""")
              }
            }
            data.append("""
   </div>
  </div>""")
            val rootsByKind = v.firstAncestorsByType(name)
            if (!rootsByKind.isEmpty)
              data.append("""
  <h2>All parents by type</h2>
  <div class="indent">""")

            rootsByKind.foreach { case (kind, (roots, rest)) =>
              data.append(s"""
   <div class="indent"><h4><a href="../$kind/info.html">$kind</a></h4>
    <div class="indent">""")
              for (root <- roots)
                data.append(s"""\n     <a href="../$root/info.html">$root</a>""")
              if (!rest.isEmpty){
                data.append("(")
                for (child <- rest)
                  data.append(s"""\n     <a href="../$child/info.html">$child</a>""")
                data.append(")")
              }
              data.append("""
    </div>
   </div>""")
            }
          }
          data.append("""
  </div>""")
          val childrens = v.allDirectChildrenOf(name)
          if (childrens.hasNext) {
            data.append("""
  <h2>DirectChildrens</h2>
  <div class="indent">""")
            while (childrens.hasNext) {
              val p = childrens.next
              data.append(s"""
   <a href="../$p/info.html">$p</a>""")
            }
            data.append(s"""
  </div>""")
          }
          layout(s"$version/$name", Stream.empty, data.toStream)
        case None =>
          layout(s"Unknown name", Stream.empty,
            Stream(s"Version $version does not contain a meta info with name $name"))
      }
    }
  }

  val myRoute =
    pathPrefix("parsers"){
      path("addStat") {
        post {
           decompressRequest() {
             entity(as[String]) { content: String =>
               complete(addStatsStr(content))
             }
           }
        }
      } ~
      pathPrefix("stats" / "last") {
        path("overview.json") {
          get {
            respondWithMediaType(`application/json`) {
              complete(JsonUtils.prettyStr(overviewStats))
            }
          }
        } ~
        path("overview.html") {
          get {
            respondWithMediaType(`text/html`) {
              complete(overviewStatsHtml)
            }
          }
        } ~
        pathPrefix("details" / IntNumber ) { (i :Int) =>
          path("info.json") {
            get {
              respondWithMediaType(`application/json`) {
                complete(JsonUtils.prettyStr(details(i)))
              }
            }
          }
        }
      }
    } ~
    pathPrefix("ui"){
      getFromResourceDirectory("frontend") 
    } ~  
    pathPrefix("nmi") {
      pathPrefix("css") {
        path("nomadBase.css") {
          get {
            respondWithMediaType(`text/css`) {
              complete {
                mainCss
              }
            }
          }
        }
      } ~
      pathPrefix("v" / Segment) { version =>
        pathPrefix("n" / Segment) { name =>
          path("info.html") {
            get{
              respondWithMediaType(`text/html`) {
                complete(metaInfoForVersionAndNameHtml(version, name))
              }
            }
          } ~
          path("info.json") {
            get {
              respondWithMediaType(`application/json`) {
                complete {
                  JsonSupport.writePrettyStr(metaInfoForVersionAndNameJson(version, name))                      
                }
              }
            }
          } ~
          path("ed.json") {
            get {
              respondWithMediaType(`application/json`) {
                complete {
                  JsonSupport.writePrettyStr(metaInfoForVersionAndNameJsonAnnotatedInfo(version, name))                   
                }
              }
            }
          }~
          path("allparents.json") {
            get {
              respondWithMediaType(`application/json`) {
                complete {
                  JsonSupport.writePrettyStr(metaInfoForVersionAndNameJsonAllParents(version, name))                   
                }
              }
            }
          }
        } ~
        path("info.html") {
          get {
            respondWithMediaType(`text/html`) {
              complete{versionHtml(version)}
            }
          }
        } ~
        path("annotatedinfo.json") {
          get {
            respondWithMediaType(`application/json`) {
              complete {
                JsonSupport.writePrettyStr(annotatedVersionJson(version))
              }
            }
          }
        } ~
        path("info.json") {
          get {
            respondWithMediaType(`application/json`) {
              complete {
                JsonSupport.writePrettyStr(versionJson(version))
              }
            }
          }
        }
      } ~
      path("info.html") {
        get {
          respondWithMediaType(`text/html`) {
            complete(versionsHtml)
          }
        }
      } ~
      path("info.json") {
        get {
          respondWithMediaType(`application/json`) {
            complete {
              JsonSupport.writePrettyStr(versionsJson)
            }
          }
        }
      }  
    }
}
