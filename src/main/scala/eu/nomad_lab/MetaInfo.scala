case class MetaInfoRecord(
  val name : String,
  val description : String,
  val kind_str : String,
  val super_names : String,
  val units : Option[String],
  val dtype_str : Option[String],
  val repeats : Option[Boolean],
  val shape : Seq[Int]
) {

}

class Env
