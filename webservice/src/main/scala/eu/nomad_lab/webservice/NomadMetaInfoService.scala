package eu.nomad_lab.webservice

import akka.actor.Actor
import akka.actor.FSM.->
import spray.routing._
import spray.http._
import MediaTypes._
import org.json4s.JsonDSL._
import scala.annotation.tailrec
import scala.collection.mutable.ArrayBuffer
import scala.collection.{mutable, breakOut}
import java.nio.file.Paths
import java.nio.file.Files
import java.nio.charset.StandardCharsets
import eu.nomad_lab.meta.KnownMetaInfoEnvs
import eu.nomad_lab.meta.MetaInfoEnv
import eu.nomad_lab.meta.MetaInfoRecord
import eu.nomad_lab.meta.MetaInfoCollection
import eu.nomad_lab.{MarkDownProcessor, JsonSupport, JsonUtils}
import com.typesafe.scalalogging.{Logger, StrictLogging}
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
  def metaInfoCollection: MetaInfoCollection

  lazy val mainCss: String = {
    val classLoader: ClassLoader = getClass().getClassLoader()
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
      s"""<li><a href="v/$version/info.html">$version</a></li>""".toString
    }.toStream
    layout("Nomad Meta Info Versions", Stream.empty,
      """    <h1>Nomad Meta Info Versions</h1>
    <ul>""" #:: versionsH #::: "</ul>" #:: Stream.empty)
  }

  def versionsJson: JValue = {
    val versions = metaInfoCollection.allUniqueEnvs{ env: MetaInfoEnv =>
      env.kind == MetaInfoEnv.Kind.Version }.toList.groupBy{ env: MetaInfoEnv => env.name }
    val validVersions = versions.flatMap{ case (i,l) => if (l.length == 1) Some(i) else None }
    val problematicVersions = versions.flatMap{ case (i,l) => if (l.length != 1) Some(i) else None }

    JObject(JField("type", "nomad_meta_versions_1_0") ::
      JField("versions", validVersions) ::
      JField("problematicVersions", problematicVersions) :: Nil)
  }

  def versionJson(version: String): JValue = {
    val versions = metaInfoCollection.versionsWithName(version)
    if (!versions.hasNext)
      JNull
    else {
      val v = versions.next
      v.toJValue(_ => JNothing, selfGid = true, superGids = true)
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
        s"""<li><a href="n/$name/info.html">$name</a></li>"""
      }.toStream

      layout(s"Nomad Meta Info Version $version", Stream.empty,
        s"""
  <h1>Nomad Meta Info <a href="../../info.html">$version</a></h1>
  <p class="description">${v.description}</p>
  <h2>Meta Infos</h2>
  <ul>
""" #:: names #::: """
  </ul>""" #:: Stream.empty)
    }
  }

  def metaInfoForVersionAndNameJson(version: String, name: String): JValue = {
    val versions = metaInfoCollection.versionsWithName(version)
    if (!versions.hasNext)
      JNull
    else {
      val v = versions.next
      val metaInfo = v.metaInfoRecordForName(name, selfGid = true, superGids = true)
      metaInfo match {
        case Some(r) => r.toJValue()
        case None => JNull
      }
    }
  }


  /** Create JSON with all meta tag. Contains complete data including ancestors and children
    */
  def annotatedVersionJson(version: String): JValue = {
    val versions = metaInfoCollection.versionsWithName(version)
    if (!versions.hasNext)
      JNull
    else {
      val v = versions.next
      val mInfo = v.toJValue(_ => JNothing)
      val nameList = v.allNames.toList
      var daList: List[JValue] = Nil
      for(name <- nameList){
        daList = metaInfoForVersionAndNameJsonAnnotatedInfo(version, name) :: daList
      }
      JObject(
        JField("type",mInfo.\("type")) ::
        JField("name",mInfo.\("name")) ::
        JField("description",mInfo.\("description")) ::
        JField("dependencies",mInfo.\("dependencies")) ::
        JField("metaInfos", daList) :: Nil
      )
    }
  }


  /** Create JSON for the "name" meta tag. Contains complete data including ancestors and children
    */
  def metaInfoForVersionAndNameJsonAnnotatedInfo (version: String, name: String): JValue = {
    val versions = metaInfoCollection.versionsWithName(version)
    if (!versions.hasNext)
      JNull
    else {
      val v = versions.next
      val metaInfo = v.metaInfoRecordForName(name, selfGid = true, superGids = true)
      metaInfo match {
        case Some(r) => //r.toJValue()
          val superNames = ArrayBuffer[String]()
          if (r.superNames.nonEmpty) {
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
            JString(str match {
              case "f"   => "f (floating point value)"
              case "i"   => "i (integer value)"
              case "f32" => "f32 (single precision float)"
              case "i32" => "i32 (32 bit integer)"
              case "f64" => "f64 (double precision floating point)"
              case "i64" => "i64 (64 bit integer)"
              case "b"   => "b (boolean value)"
              case "B"   => "B (variable length byte array i.e. a blob)"
              case "C"   => "C (a unicode string)"
              case "D"   => "D (a json dictionary)"
              case "r"   => "r (reference to a section)"
              case  v    => s"$v (unknown type)"
          })
          case None =>
            JNothing
          }
          def hrefCreate(x:String):String =  {
            if(x==name)
               s"""<em>$x</em>"""
            else
               s"""<a href="#/$version/$x"> $x </a> """
          }
          val metaShape = r.shape match {
                case None => JNothing
                case Some(s) =>
                  val listShape: List[JValue] = s.map {
                    case Left(i) => JInt(i)
                    case Right(s) => JString(hrefCreate(s))
                  }(breakOut)
                  JArray(listShape)
              }
          val children = v.allDirectChildrenOf(name).toList
          var allParents:List[String] = Nil
          v.metaInfoRecordForNameWithAllSuper(name, selfGid = true, superGids = false).foreach {
            (metaInfo: MetaInfoRecord) => allParents =  metaInfo.name :: allParents }
          val rootSectionAncestors = v.firstAncestorsByType(name).get("type_section") match {
            case Some((rootSections,_)) =>
              rootSections.toSeq
            case None =>
              Seq()
          }
          val refSectionsWithLinks: JValue = r.referencedSections match {
              case Some(sects) => JArray(sects.map((x: String) => JString(hrefCreate(x)))(breakOut))
              case None => JNothing
            }

          val descriptionHTML = MarkDownProcessor.processMarkDown(r.description,v.allNames.toSet,hrefCreate)

          JObject(JField("type", "nomad_meta_versions_1_0") ::
            JField("versions", version) ::
            JField("name", name) ::
            JField("description",descriptionHTML) ::
            JField("gid", r.gid) ::
            JField("units", r.units) ::
            JField("dtypeStr", dtypeStr) ::
            JField("repeats", r.repeats) ::
            JField("redundant", r.redundant) ::
            JField("derived", r.derived) ::
            JField("kindStr", r.kindStr) ::
            JField("referencedSections", refSectionsWithLinks) ::
            JField("superNames", superNames) ::
            JField("children", children) ::
            JField("allparents", allParents ) ::
            JField("rootSectionAncestors", rootSectionAncestors ) ::
            ("shape" -> metaShape) ::
            r.otherKeys) 
        case None => JNull
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
    if (i >= 0 && i < keys.length)
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



  /** Get node class depending upon its kind string
    *
    */
  def nodeClassByKindStr(kindStr:String):String = {
    kindStr match {
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
    }
  }


  def anySuperSection(v: MetaInfoEnv,superNames: Seq[String]): Boolean = {
    var flag = false
      for(superName <- superNames ){
        if(v.metaInfoRecordForName(superName).get.kindStr == "type_section")
          flag = true
      }
    flag
  }


  @tailrec final def metaInfoAncestors(v: MetaInfoEnv, sourceRef:Map[String,Map[String,Option[Seq[String]]]], toDo: mutable.Set[String], nMap:Map[String,(String, String,Boolean)], eMap:Map[(String, String),String]): (Map[String,(String, String,Boolean)],Map[(String, String),String]) = {
    var nodesMap = nMap  // name -> (shape, class(for color etc))
    var edgesMap = eMap // (source, target) -> class
    // Add element and its parents
    if(toDo.isEmpty)
      Tuple2(nodesMap, edgesMap)
    else {
      val now = toDo.head
      toDo.remove(now)
      val nowR = v.metaInfoRecordForName(now)
      if (nowR.isEmpty)
        throw new MetaInfoEnv.DependsOnUnknownNameException(v.name, nMap.toString, now)
      val metaInfo = nowR.get
      metaInfo.superNames.foreach { s: String =>
        val su = v.metaInfoRecordForName(s)
        if (su.isEmpty)
          throw new MetaInfoEnv.DependsOnUnknownNameException(v.name, nMap.toString, s)
        val superName = su.get
//        logger.info("metaInfo: "+metaInfo.name + " SuperName: "+ superName.name  +" kindStr: "+ superName.kindStr)
        if (superName.kindStr == metaInfo.kindStr) {
          if(!edgesMap.contains(Tuple2(metaInfo.name,superName.name))) //This is needed to prevent overriding of the "Self_Reference"
            edgesMap += (Tuple2(metaInfo.name,superName.name) -> "casual")
          if (!nodesMap.contains(superName.name)) {
            nodesMap += (superName.name -> Tuple3("circle",nodeClassByKindStr(superName.kindStr),false))
            toDo += superName.name
          }
          else if(nodesMap(superName.name)._3) // If set as a ghost node then override, since it is in the parent now
            nodesMap += (superName.name -> Tuple3(nodesMap(superName.name)._1,nodeClassByKindStr(superName.kindStr),false))
            toDo += superName.name //Still add to do as it was added by the ref graph
        }
      }

      //Handle its references
      sourceRef.get(metaInfo.name) match {
        case Some(refs) =>
          for ((thr,referencedSection) <- refs) {
            referencedSection match {
              case Some(refSections) => //Set[String]
                for (t <- refSections) {
                  val target = v.metaInfoRecordForName(t).get
                  //Add edges
                  if (target.name == metaInfo.name)
                    edgesMap += (thr, target.name) -> "Self_Reference"
                  else {
                    edgesMap += (thr, target.name) -> "reference"
                    edgesMap += (thr, metaInfo.name) -> "casual"
                  }
                  //Add nodes
                  if (!nMap.contains(target.name)) {
                    //Add the target if not exists as a ghost node
                    //In case requirement change add this node to :to Do: List
                    nodesMap += target.name -> Tuple3("circle", nodeClassByKindStr(target.kindStr), true) // This can through an error if v.metaInfoRecordForName(target).get is empyth
                  }
                  if (!nMap.contains(thr)) {
                    //Add the connecting node  to the reference
                    //In case requirement change add this node to to Do: List
                    nodesMap += thr -> Tuple3("circle", nodeClassByKindStr(v.metaInfoRecordForName(thr).get.kindStr), false) // This can not through an error as this check has been made while adding the "thr" node to all ref
                  }
                }
              case _ =>
            }
          }
        case _ =>
      }
      metaInfoAncestors(v, sourceRef, toDo, nodesMap, edgesMap)
    }
  }

  def createGraphJson(nodesMap: Map[String, (String, String,Boolean)], edgesMap: Map[(String, String), String]): (List[JValue], List[JValue])  =
  {
    var nodes: List[JValue] = Nil
    var edges: List[JValue] = Nil
    var selfRefEdges = Map[(String, String), String]()
    nodesMap foreach { case (key, value) =>
      val nodeOpacity = if ( value._3 ) 0.75 else 1.0 //if ghost node is true; decrease opacity
      nodes = JObject(
        ("data" -> JObject(
          ("id" -> JString(key.mkString)) :: Nil)) ::
//        ("classes" -> JObject(
//          ("id" -> JString(nodeClass)) :: Nil)) ::
          ("style" -> JObject(
            ("background-color" -> JString(value._2)) ::
            ("opacity" -> JDouble(nodeOpacity)) ::
            ("shape" -> JString(value._1))
            :: Nil)) :: Nil) :: nodes
    }
    edgesMap foreach { case (key, value) =>
      if (value == "Self_Reference")
        selfRefEdges += (key -> value)
      else
      edges = JObject(
        ("data" ->
          JObject(
            ("source" -> JString(key._1)) ::
            ("target" -> JString(key._2)) ::
              Nil)) ::
        ("classes" -> JString(value)) :: Nil) :: edges
    }

    //Add self reference edge twice;
    selfRefEdges foreach { case (key, value) =>
      edges = JObject(
        ("data" ->
          JObject(
            ("source" -> JString(key._1)) ::
            ("target" -> JString(key._2)) ::
              Nil)) ::
          ("classes" -> JString("reference")) :: Nil) ::
        JObject(
          ("data" ->
            JObject(
              ("source" -> JString(key._1)) ::
              ("target" -> JString(key._2)) ::
                Nil)) ::
            ("classes" -> JString("casual")) :: Nil) :: edges
    }
    Tuple2(nodes,edges)
  }

  /** Create JSON containing information graph information about multiple MetaInfos, for the metaInfoList. Contains information only about the ancestors
    */
  def multipleMetaInfoGraph(version: String, metaInfoList: String): JValue = {
    val metaInfos = metaInfoList.split(",").map(_.trim)
    if(metaInfos.length == 0)
      JNull
    else {
      val versions = metaInfoCollection.versionsWithName(version)
      if (!versions.hasNext)
        JNull
      else {
        val v = versions.next
        val sourceRef = findAllReference(version)
        val toDo = mutable.Set[String]()
        var nMap = Map[String, (String, String,Boolean)]() // name -> (shape, class(for color etc))
        var eMap = Map[(String, String), String]() // (source, target) -> class

        for(i <- metaInfos ){
          if(v.allNames.contains(i)){
            val metaInfo = v.metaInfoRecordForName(i).get
            nMap += (metaInfo.name -> Tuple3("star",nodeClassByKindStr(metaInfo.kindStr),false))
            for( (kindStr,(roots, _)) <- v.firstAncestorsByType(metaInfo.name)) {
              for (r <- roots){
                nMap += r -> Tuple3("circle", nodeClassByKindStr(v.metaInfoRecordForName(r).get.kindStr), false)
                eMap += ((metaInfo.name, r) -> "casual")
                toDo += r
              }
            }
            //Handle its references
            sourceRef.get(metaInfo.name) match {
              case Some(refs) =>
                for ((thr,referencedSection) <- refs) {
                  referencedSection match {
                    case Some(refSections) => //Set[String]
                      for (t <- refSections) {
                        val target = v.metaInfoRecordForName(t).get
                        //Add edges
                        if (target.name == metaInfo.name)
                          eMap += (thr, target.name) -> "Self_Reference"
                        else {
                          eMap += (thr, target.name) -> "reference"
                          eMap += (thr, metaInfo.name) -> "casual"
                        }
                        //Add nodes
                        if (!nMap.contains(target.name)) {
                          //Add the target if not exists as a ghost node
                          //In case requirement change add this node to :to Do: List
                          nMap += target.name -> Tuple3("circle", nodeClassByKindStr(target.kindStr), true) // This can through an error if v.metaInfoRecordForName(target).get is empyth
                        }
                        if (!nMap.contains(thr)) {
                          //Add the connecting node  to the reference
                          //In case requirement change add this node to to Do: List
                          nMap += thr -> Tuple3("circle", nodeClassByKindStr(v.metaInfoRecordForName(thr).get.kindStr), false) // This can not through an error as this check has been made while adding the "thr" node to all ref
                        }
                      }
                    case _ =>
                  }
                }
              case _ =>
            }

          }
        }
//      Main function to calculate the graph
        var (nodesMap, edgesMap) = metaInfoAncestors(v, sourceRef,toDo, nMap, eMap) //Traverse the graph using tail recursion

        for(i <- metaInfos){
          if(v.allNames.contains(i)){
            val currNode = nodesMap(i); // Change the shape of the current node. This can be handled by the metaInfoAncestors but then we need
            // to add another variable to it then
            nodesMap += (i -> Tuple3("star", currNode._2, false))
          }
        }
        val (nodes, edges) = createGraphJson(nodesMap,edgesMap)

        JObject(
          ("nodes" -> JArray(nodes)) ::
          ("edges" -> JArray(edges)) ::
            Nil)
      }
    }
  }

  /**Get all references as a map in the given version
    *
    *
    */

  def findAllReference(version: String):Map[String,Map[String,Option[Seq[String]]]] = {
    val versions = metaInfoCollection.versionsWithName(version)
    if (!versions.hasNext)
      Map()
    else {
      val v = versions.next
      var allReferences: Map[String,Map[String,Option[Seq[String]]]] = Map()
      v.allNames foreach {
        name => v.metaInfoRecordForName(name)  match {
            case Some(metaInfo) =>
              if( metaInfo.kindStr == "type_document_content" && metaInfo.dtypeStr.getOrElse("Empty dtypeStr") == "r" ){
                v.rootAnchestorsOfType("type_section",metaInfo.name).foreach{   ancTypeSec =>
                val internalMap = if(allReferences.contains(ancTypeSec))
                       allReferences(ancTypeSec) + (metaInfo.name -> metaInfo.referencedSections)
                  else
                       Map(metaInfo.name -> metaInfo.referencedSections)
                  allReferences = allReferences + (ancTypeSec -> internalMap)
                }
              }
            case _ =>
        }
      }
      allReferences
    }
  }


  /** Create JSON containing information graph information, for the "name" meta tag. Contains complete data including ancestors and children
    */
  def metaInfoAncestorChildrenGraphJson (version: String, name: String): JValue = {
    val versions = metaInfoCollection.versionsWithName(version)
    if (!versions.hasNext)
      JNull
    else {
      val v = versions.next
      v.metaInfoRecordForName(name) match {
        case Some(metaInfo) =>
          val toDo: mutable.Set[String] = mutable.Set()
          val refMap = Map[String,Option[Seq[String]]]()
          var nMap = Map[String, (String, String, Boolean)]() // name -> (shape, class(for color etc), ghost)
          var eMap = Map[(String, String), String]() // (source, target) -> class
          val sourceRef = findAllReference(version)
          //Handle node and its first ancestor by type
          //Note: The first node is handled out of the recursion for the following recursion;
          //1. For first node all ancestor by type are added to the graph, but it is not the case for the other graph
          //2. It has different shape (star)

          nMap += (metaInfo.name -> Tuple3("star",nodeClassByKindStr(metaInfo.kindStr),false))
          for( (kindStr,(roots, _)) <- v.firstAncestorsByType(metaInfo.name)) {
            for (r <- roots){
              nMap += r -> Tuple3("circle", nodeClassByKindStr(v.metaInfoRecordForName(r).get.kindStr), false)
              eMap += ((metaInfo.name, r) -> "casual")
              toDo += r
            }
          }
          //Handle its references
          sourceRef.get(metaInfo.name) match {
          case Some(refs) =>
            for ((thr,referencedSection) <- refs) {
              referencedSection match {
                case Some(refSections) => //Set[String]
                  for (t <- refSections) {
                    val target = v.metaInfoRecordForName(t).get
                    //Add edges
                    if (target.name == metaInfo.name)
                      eMap += (thr, target.name) -> "Self_Reference"
                    else {
                      eMap += (thr, target.name) -> "reference"
                      eMap += (thr, metaInfo.name) -> "casual"
                    }
                    //Add nodes
                    if (!nMap.contains(target.name)) {
                      //Add the target if not exists as a ghost node
                      //In case requirement change add this node to :to Do: List
                      nMap += target.name -> Tuple3("circle", nodeClassByKindStr(target.kindStr), true) // This can through an error if v.metaInfoRecordForName(target).get is empyth
                    }
                    if (!nMap.contains(thr)) {
                      //Add the connecting node  to the reference
                      //In case requirement change add this node to to Do: List
                      nMap += thr -> Tuple3("circle", nodeClassByKindStr(v.metaInfoRecordForName(thr).get.kindStr), false) // This can not through an error as this check has been made while adding the "thr" node to all ref
                    }
                  }
                case _ =>
              }
            }
          case _ =>
          }
          //Recursively add all the to Do nodes to the graph
          val (nodesMap, edgesMap) =  metaInfoAncestors(v, sourceRef, toDo, nMap, eMap)
          val (nodes, edges) = createGraphJson(nodesMap,edgesMap)
          ///// Direct Children related Stuff;
          val sortedChildren = v.allDirectChildrenOf(name).toList.sortWith(_ > _)
          var children: List[JValue] = Nil
          for (child <- sortedChildren) {
            if (!nodesMap.contains(child)) {
              // Add only if the child has not been traversed
              val metaInfo = v.metaInfoRecordForName(child).get
              children = JObject(
                ("data" -> JObject(
                  ("id" -> JString(metaInfo.name)) ::
                    Nil)) ::
                  ("style" -> JObject(
                    ("background-color" -> JString(nodeClassByKindStr(metaInfo.kindStr)
                    )) :: Nil)) :: Nil) :: children
            }
          }

          JObject(
            ("nodes" -> JArray(nodes)) ::
              ("edges" -> JArray(edges)) ::
              ("children" -> JArray(children)) ::
              Nil)

        case None => JNull
      }
    }
  }

  /*
        /** Create JSON containing information graph information, for the "name" meta tag. Contains complete data including ancestors and children
    */
  def metaInfoAncestorChildrenGraphJson2 (version: String, name: String): JValue =
  {
    val versions = metaInfoCollection.versionsWithName(version)
    if (!versions.hasNext)
      JNull
    else {
      val v = versions.next
      if(!v.allNames.contains(name))
        JNull
      else {
        var known = mutable.Set[String]()
        val toDo = mutable.ListBuffer[String]()
        var nMap = Map[String, (String, String)]() // name -> (shape, class(for color etc))
        var eMap = Map[(String, String), String]() // (source, target) -> class

        //Handle the current node separetely outside of the recursion
        val metaInfo = v.metaInfoRecordForName(name).get
        val nodeClass = nodeClassByKindStr(metaInfo.kindStr)
        nMap += (metaInfo.name -> Tuple2("star",nodeClass))
        metaInfo.superNames.foreach { superName: String =>
          if(!eMap.contains(Tuple2(metaInfo.name,superName))) //This is needed to prevent overriding of the "Self_Reference"
            eMap += (Tuple2(metaInfo.name,superName) -> "casual")
          if (!known(superName)) {
            toDo.append(superName)
            known += superName
          }
        }
        known += name
        //Add reference
        val (flagRef, newNodes, refEdges) = getRefenceGraph(v, metaInfo.name)
        if(flagRef) {
          eMap ++= refEdges
          for(nNode <- newNodes){
            if (!known(nNode)) {
              toDo.append(nNode)
              known += nNode
            }
          }
        }
        val (nodesMap, edgesMap) = metaInfoAncestors(v, toDo, known, nMap, eMap) //Traverse the graph using tail recursion
        var children: List[JValue] = Nil
        //Add all ancestors of the current node

        val (nodes, edges) = createGraphJson(nodesMap,edgesMap)

        ///// Direct Children related Stuff;
        val sortedChildren = v.allDirectChildrenOf(name).toList.sortWith(_ > _)
        for (child <- sortedChildren) {
          if (!nodesMap.contains(child)) {
            // Add only if the child has not been traversed
            val metaInfo = v.metaInfoRecordForName(child).get
            children = JObject(
              ("data" -> JObject(
                ("id" -> JString(metaInfo.name)) ::
                  Nil)) ::
                ("style" -> JObject(
                  ("background-color" -> JString(nodeClassByKindStr(metaInfo.kindStr)
                  )) :: Nil)) :: Nil) :: children
          }
        }

        JObject(
          ("nodes" -> JArray(nodes)) ::
            ("edges" -> JArray(edges)) ::
            ("children" -> JArray(children)) ::
            Nil)
      }
    }
  }
*/
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
          if (r.units.nonEmpty)
            data.append(s"""
   <p><span class="key">units:</span> <span class="value">${r.units.get}</span></p>""")
          if (r.repeats.nonEmpty && r.repeats.get)
            data.append(s"""
   <p><span class="key">repeats:</span> <span class="value">true</span></p>""")
          if (r.dtypeStr.nonEmpty)
            data.append(s"""
   <p><span class="key">dtypeStr:</span> <span class="value">${r.dtypeStr.get}</span></p>""")
          if (r.shape.nonEmpty)
            data.append(s"""
   <p><span class="key">shape:</span> <span class="value">${r.shape.get.mkString("[",", ","]")}</span></p>""")
          if (r.otherKeys.nonEmpty)
            r.otherKeys.foreach { case JField(key, value) =>
              data.append(s"""
   <p><span class="key">$key:</span> <span class="value">${JsonSupport.writePrettyStr(value)}</span></p>""")
            }
          data.append("""
  </div>""")
          if (r.superNames.nonEmpty) {
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
            if (rootsByKind.nonEmpty)
              data.append("""
  <h2>All parents by type</h2>
  <div class="indent">""")

            rootsByKind.foreach { case (kind, (roots, rest)) =>
              data.append(s"""
   <div class="indent"><h4><a href="../$kind/info.html">$kind</a></h4>
    <div class="indent">""")
              for (root <- roots)
                data.append(s"""\n     <a href="../$root/info.html">$root</a>""")
              if (rest.nonEmpty){
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
          path("metainfograph.json") {
            get {
              respondWithMediaType(`application/json`) {
                complete {
                  JsonSupport.writePrettyStr(metaInfoAncestorChildrenGraphJson(version, name))
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
        }~
        path("multiplemetainfograph.json" /) {
          get {
            parameter('metaInfoList) { metaInfoList =>
              respondWithMediaType(`application/json`) {
                complete{
                  JsonSupport.writePrettyStr(multipleMetaInfoGraph(version, metaInfoList))
                }
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