package eu.nomad_lab.webservice

import akka.actor.Actor
import spray.routing._
import spray.http._
import MediaTypes._
import org.{json4s => jn}
import org.json4s.native.JsonMethods
//import spray.httpx.unmarshalling._
//import spray.httpx.marshalling.{streamMarshaller}
//import spray.httpx.Json4sSupport
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

// we don't implement our route structure directly in the service actor because
// we want to be able to test it independently, without having to spin up an actor
class NomadMetaInfoActor extends Actor with NomadMetaInfoService {

  lazy val metaInfoCollection: MetaInfoCollection = {
    val classLoader: ClassLoader = getClass().getClassLoader();
    val filePath = classLoader.getResource("nomad-meta-info/nomad_meta_info/main.nomadmetainfo.json").getFile()
    val resolver = new RelativeDependencyResolver
    val mainEnv = SimpleMetaInfoEnv.fromFilePath(filePath, resolver)
    val w = new java.io.FileWriter("/tmp/t.dot")
    mainEnv.writeDot(w)
    w.close()
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
trait NomadMetaInfoService extends HttpService {

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
    if (!versions.hasNext)
      layout(s"Unknown version", Stream.empty,
        Stream(s"Version $version unknown"))
    else {
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
   def metaInfoForVersionAndNameJsonCompleteInfo (version: String, name: String): jn.JValue = {
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
        val rootsByKind = v.firstAncestorsByType(name)
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
    pathPrefix("api"){
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
          path("completeinfo.json") {
            get {
              respondWithMediaType(`application/json`) {
                complete {
                  JsonSupport.writePrettyStr(metaInfoForVersionAndNameJsonCompleteInfo(version, name))                   
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
