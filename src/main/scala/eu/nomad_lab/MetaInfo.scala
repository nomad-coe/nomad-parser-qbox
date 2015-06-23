case class MetaInfoRecord(
  val name : String,
  val description : String,
  val kind_str : String,
  val super_names : String,
  val units : Option[String],
  val dtype_str : Option[String],
  val repeats : Option[Boolean],
  val shape : Seq[Int] ) {
  def
}

class MetaInfoEnv(
  val name: String,
  var gids: Map[String, String],
  var metaInfos: Map[String, MetaInfoRecord] ) {

}

class MetaInfoDbEnv(
  val name: String,
  val dbConnection: AkkaRef,
  val lazyLoad: Boolean) {

}
