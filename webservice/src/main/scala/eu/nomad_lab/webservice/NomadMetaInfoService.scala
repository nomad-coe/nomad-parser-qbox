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
import scala.collection.breakOut
import scala.collection.mutable
import java.io.File
import java.nio.file.Paths
import java.nio.file.Files
import java.nio.charset.StandardCharsets
import eu.nomad_lab.meta.MetaInfoEnv
import eu.nomad_lab.meta.MetaInfoRecord
import eu.nomad_lab.meta.MetaInfoCollection
import eu.nomad_lab.meta.RelativeDependencyResolver
import eu.nomad_lab.meta.SimpleMetaInfoEnv
import eu.nomad_lab.JsonSupport
import eu.nomad_lab.JsonUtils
import com.typesafe.scalalogging.StrictLogging
import org.json4s.{JNothing, JNull, JBool, JDouble, JDecimal, JInt, JString, JArray, JObject, JValue, JField}

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

  lazy val metaInfoCollection: MetaInfoCollection = {
    val classLoader: ClassLoader = getClass().getClassLoader();
    val filePath = classLoader.getResource("nomad_meta_info/main.nomadmetainfo.json").getFile()
    val resolver = new RelativeDependencyResolver
    val mainEnv = SimpleMetaInfoEnv.fromFilePath(filePath, resolver)
    new SimpleMetaInfoEnv(
      name = "last",
      description = "latest version, unlike all others this one is symbolic and will change in time",
      source = jn.JObject( jn.JField("path", jn.JString(Paths.get(filePath).getParent().toString())) ),
      nameToGid = Map[String, String](),
      gidToName = Map[String, String](),
      metaInfosMap = Map[String, MetaInfoRecord](),
      dependencies = Seq(mainEnv),
      kind = MetaInfoEnv.Kind.Version)
  }

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
    import jn.JsonDSL._

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
          import jn.JsonDSL._
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
          val dtypeStr = if (r.dtypeStr.isEmpty) "" else r.dtypeStr.get
          val childrenItr = v.allDirectChildrenOf(name)
          val children = ArrayBuffer[String]()
          while (childrenItr.hasNext) {
            children += childrenItr.next
          }
          jn.JObject(jn.JField("type", "nomad_meta_versions_1_0") ::
            jn.JField("versions", version) ::
            jn.JField("name", name) ::
            jn.JField("description", r.description) ::
            jn.JField("gid", r.gid) ::
            jn.JField("units", r.units) ::
            jn.JField("dtypeStr", dtypeStr) ::
            jn.JField("kindStr", r.kindStr) ::
            jn.JField("superNames", superNames) ::
            jn.JField("children", children) ::
            Nil)
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

  /** Create JSON for the "name" meta tag. Contains complete data including ancestors and children
    */
  def metaInfoForVersionAndNameJsonAllParents (version: String, name: String): jn.JValue = {
    val versions = metaInfoCollection.versionsWithName(version)
    if (!versions.hasNext)
      jn.JNull
    else {
      val v = versions.next
      val metaInfo = v.metaInfoRecordForName(name, selfGid = true, superGids = true)
      metaInfo match {
        case Some(r) =>
          import jn.JsonDSL._
          var JSONStr = s"""

        """ 
          val rootsByKind = v.firstAncestorsByType(name)
          if (!rootsByKind.isEmpty){
            var attributesName = r.kindStr match {
              case "type_document_content" => """ "color":"red","shape":"box" """
              case "type_unknown" => """ "color":"green","shape":"box" """
              case "type_unknown_meta" => """ "color":"green","shape":"box" """
              case "type_document" => """ "color":"grey","shape":"box" """
              case "type_meta" => """ "color":"blue","shape":"box" """
              case "type_abstract_document_content" => """ "color":"red","shape":"box" """
              case "type_section" => """ "color":"blue","shape":"box" """
              case "type_connection" => """ "orange":"blue","shape":"box" """
              case _ => """ "color":"pink","shape":"box" """
            }
            JSONStr += s""" {
               "nodes":{ 
                  "${name}":{ $attributesName,"label":"${name}"},
                """ 
            
            rootsByKind.foreach { case (kind, (roots, rest)) =>
              var attributes = kind match {
                case "type_document_content" => """ "color":"yellow","shape":"box" """
                case "type_unknown" => """ "color":"green","shape":"box" """
                case "type_unknown_meta" => """ "color":"green","shape":"box" """
                case "type_document" => """ "color":"grey","shape":"box" """
                case "type_meta" => """ "color":"blue","shape":"box" """
                case "type_abstract_document_content" => """ "color":"red","shape":"box" """
                case "type_section" => """ "color":"blue","shape":"box" """
                case "type_connection" => """ "orange":"blue","shape":"box" """
                case _ => """ "color":"pink","shape":"box" """
              }
              for(root <- roots){
                if (root != roots.last){
                  JSONStr += s"""
                    "${root}":{ $attributes,"label":"${root}"},  """
                }
                else {
                  JSONStr += s"""
                    "${root}":{ $attributes,"label":"${root}"}
                      """
                }
              }
              if (!rest.isEmpty){
                JSONStr += s""", """ // This comma is needed as after the last element there will be some more data
                for(child <- rest)
                  if (child != rest.last) //Notice the "," at the end of the line. Json parsing crashes if there is comman after the last element
                    JSONStr += s"""
                    "${child}":{ $attributes,"label":"${child}"}, 
                      """
                  else
                    JSONStr += s"""
                    "${child}":{ $attributes,"label":"${child}"}  """

                if (kind != rootsByKind.last._1){
                  JSONStr += s""", """
                }

              }

            }
            JSONStr += s"""
              },
              "edges":{
            
            """    
            //Now add the edges, hence second iteration over the rootsByKind; First add nodes to all the roots and then add
            //Again another iteration is needed due to the syntax of arborjs json
            if(!rootsByKind.isEmpty){
              JSONStr += s"""
                      "${name}":{ """  
              rootsByKind.foreach { case (kind, (roots, rest)) =>
                for(root <- roots){
                  if (root != roots.last){
                    JSONStr += s"""
                        "${root}":{},  """
                  }
                  else {
                    JSONStr += s"""
                        "${root}":{}
                          """
                  }
                }
                if (kind != rootsByKind.last._1){
                  JSONStr += s""", """
                }
              }
              JSONStr += s"""
                        }, """  
            }

            rootsByKind.foreach { case (kind, (roots, rest)) =>
              for(root <- roots){
                JSONStr += s"""
                      "${root}":{  """  
                if (!rest.isEmpty){
                  for(child <- rest)
                    if (child != rest.last) //Notice the "," at the end of the line. Json parsing crashes if there is comman after the last element
                      JSONStr += s"""
                    "${child}":{}, 
                      """
                    else
                      JSONStr += s"""
                    "${child}":{}
                    """
                }
                JSONStr += s"""
                    }  """ 
              }
              if (kind != rootsByKind.last._1){
                JSONStr += s""", """
              }
            }
            JSONStr += s"""
              }       
            }
            """ //Close of edges and the json object
          }
          parse(JSONStr)
        case None => jn.JNull
          
      }
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
          path("annotated.json") {
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
