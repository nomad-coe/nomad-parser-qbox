package eu.nomad_lab
import org.json4s._
import org.json4s.native.JsonMethods._

/** Represents a piece of nomad meta info referring to other meta info by name.
  *
  * Can be interpreted within a context [[eu.nomad_lab.MetaInfoEnv]], but matches the
  * preferred way one describes it in a json file.
  */
case class MetaInfoRecord(
  val name: String,
  val kindStr: String,
  val description: String,
  val superNames: String,
  val units: Option[String] = None,
  val dtypeStr: Option[String] = None,
  val repeats: Option[Boolean] = None,
  val shape: Option[Seq[Int]] = None,
  val otherKeys: Seq[JsonAST.JField] = Seq()) {
};

/* class MetaInfoRecordSerializer extends CustomSerializer[MetaInfoRecord](format => (
         {
           case obj :JObject => {
             var name: String;
             val kindStr: String;
             val description: String;
             val superNames: String;
             val units: Option[String] = None;
             val dtypeStr: Option[String] = None;
             val repeats: Option[Boolean] = None;
             val shape: Option[Seq[Int]] = None;
             val otherKeys: Seq[JsonAST.JField] = Seq();
             obj.map {
               case JField("name", value) =>
                 name = value
               case JField(key, value) =>
                 otherKeys += JField(key, value)
             }
           }
         },
         {
           case x: =>
             JObject(JField("start", JInt(BigInt(x.startTime))) ::
                     JField("end",   JInt(BigInt(x.endTime))) :: Nil)
         }
       ))*/


class MetaInfoEnv(
  val name: String,
  var gids: Map[String, String],
  var metaInfos: Map[String, MetaInfoRecord] ) {

}

class MetaInfoDbEnv(
  val name: String,
//  val dbConnection: ActorRef,
  val lazyLoad: Boolean) {

}
