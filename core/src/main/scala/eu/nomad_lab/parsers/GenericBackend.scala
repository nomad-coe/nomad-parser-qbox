package eu.nomad_lab.parsers;
import ucar.ma2.{Array => NArray}
import ucar.ma2.DataType
import ucar.ma2.ArrayString
import scala.collection.mutable
import scala.collection.mutable.ListBuffer
import eu.nomad_lab.Base64
import eu.nomad_lab.JsonUtils
import eu.nomad_lab.meta.MetaInfoEnv
import eu.nomad_lab.meta.MetaInfoRecord
import org.json4s.{JNothing, JNull, JBool, JDouble, JDecimal, JInt, JString, JArray, JObject, JValue, JField}

/** A generic backend that can support direct storing, or caching in the Section
  *
  * Not supporting both might be slightly faster, but considered not worth the duplication
  * for now.
  */
object GenericBackend {

  class MissingSectionException(
    sectionName: String, gIndex: Long, msg: String, what: Throwable = null
  ) extends Exception(s"Missing section $sectionName with gIndex $gIndex, $msg", what) { }


  /** the backend was called in an invalid way given the metadata known
    */
  class InvalidAssignementException(
    metaInfo: MetaInfoRecord,
    msg:String
  ) extends Exception(s"Invalid assignement for meta data ${metaInfo.name}: $msg") { }

  /** root class for managers of sections
    */
  abstract class SectionManager {
    /** the direct parent sections of this section
      */
    def parentSectionNames: Array[String];

    /** the last section opened
      */
    def lastSectionGIndex: Long;

    /** returns the gIndexes of the sections that are still open
      */
    def openSectionsGIndexes(): Iterator[Long];

    /** sets info values of an open section.
      *
      * references should be references to gIndex of the root sections this section refers to.
      */
    def setSectionInfo(gIndex: Long, references: Map[String, Long]): Unit;

    /** returns the gIndex of a newly opened section
      */
    def openSection(backend: GenericBackend): Long;

    /** closes the given section
      */
    def closeSection(backend: GenericBackend, gIndex: Long);

    /** Information on an open section
      */
    def openSectionInfo(gIndex: Long): String;
  }

  /** base class for an object that handles values for the given metaInfo
    *
    * metaInfo should be in the section handled by sectionManager.
    * This makes sense only for concrete meta infos.
    */
  abstract class MetaDataManager(
    val metaInfo: MetaInfoRecord
  ) {
    /** Scection manager of the section of this value
      */
    def sectionManager: SectionManager;

    /** Converts a json value to a long
      */
    final def convertJValue_i(value: JValue, shouldThrow: Boolean = true): Option[Long] = {
      value match {
        case JInt(i) =>
          Some(i.longValue)
        case JDecimal(d) =>
          Some(d.longValue)
        case JDouble(d) =>
          Some(d.longValue)
        case JNothing =>
          None
        case _ =>
          if (shouldThrow)
            throw new InvalidAssignementException(metaInfo, s"invalid value $value when expecting integer")
          None
      }
    }

    /** Converts a json value to a floating point double
      */
    final def convertJValue_f(value: JValue, shouldThrow: Boolean = true): Option[Double] = {
      value match {
        case JInt(i) =>
          Some(i.doubleValue)
        case JDecimal(d) =>
          Some(d.doubleValue)
        case JDouble(d) =>
          Some(d)
        case JNothing =>
          None
        case _ =>
          if (shouldThrow)
            throw new InvalidAssignementException(metaInfo, s"invalid value $value when expecting a floating point")
          None
      }
    }

    /** Converts a json value to a json dictionary
      */
    final def convertJValue_D(value: JValue, shouldThrow: Boolean = true): Option[JObject] = {
      value match {
        case JObject(obj) =>
          Some(JObject(obj))
        case JNothing =>
          None
        case _ =>
          if (shouldThrow)
            throw new InvalidAssignementException(metaInfo, s"invalid value $value when expecting a json dictionary")
          None
      }
    }

    /** Converts a json value to a string
      */
    final def convertJValue_C(value: JValue, shouldThrow: Boolean = true): Option[String] = {
      value match {
        case JString(s) =>
          Some(s)
        case JNothing =>
          None
        case _ =>
          if (shouldThrow)
            throw new InvalidAssignementException(metaInfo, s"invalid value $value when expecting a floating point")
          None
      }
    }

    /** Converts a json value to a Base64 encoded binary value
      */
    final def convertJValue_B64(value: JValue, shouldThrow: Boolean = true): Option[String] = {
      value match {
        case JString(s) =>
          Some(s)
        case JArray(arr) =>
          val byteArray = arr.flatMap {
            case JNothing =>
              None
            case JInt(i) =>
              if (i < Byte.MinValue || i > 255)
                throw new InvalidAssignementException(metaInfo, s"value $value out of bounds for Byte")
              Some(i.byteValue)
            case _ =>
              throw new InvalidAssignementException(metaInfo, s"unexpected value ($value) for Byte")
          }.toArray
          Some(Base64.b64EncodeStr(byteArray))
        case JNothing =>
          None
        case _ =>
          if (shouldThrow)
            throw new InvalidAssignementException(metaInfo, s"invalid value $value when expecting a base64 encoded value")
          None
      }
    }

    /** Adds value to the section the value is in
      */
    def addValue(value: JValue, gIndex: Long = -1): Unit; /*= {
      throw new InvalidAssignementException(metaInfo, "addValue not supported")
    }*/

    /** Adds a floating point value
      */
    def addRealValue(value: Double, gIndex: Long = -1): Unit; /*= {
      throw new InvalidAssignementException(metaInfo, "addRealValue not supported")
    }*/

    /** Adds a new array of the given size
      */
    def addArrayValue(shape: Seq[Long], gIndex: Long = -1): Unit; /*= {
      throw new InvalidAssignementException(metaInfo, "addArrayValue not supported")
    }*/

    /** Adds values to the last array added
      */
    def setArrayValues(values: NArray, offset: Option[Seq[Long]] = None, gIndex: Long = -1): Unit; /*= {
      throw new InvalidAssignementException(metaInfo, "setArrayValues not supported")
    }*/

    /** Adds a new array with the given values
      */
    def addArrayValues(values: NArray, gIndex: Long = -1): Unit; /*= {
      addArrayValue(values.getShape().map( _.toLong).toSeq, gIndex)
      setArrayValues(values, gIndex = gIndex)
    }*/
  }

  /** dummy class that ignores input, useful for things that should be ignored
    */
  abstract class DummyMetaDataManager(
    metaInfo: MetaInfoRecord
  ) extends MetaDataManager(metaInfo) {

    /** Adds a floating point value
      */
    override def addRealValue(value: Double, gIndex: Long = -1): Unit = { }

    /** Adds a new array of the given size
      */
    override def addArrayValue(shape: Seq[Long], gIndex: Long = -1): Unit = { }

    /** Adds values to the last array added
      */
    override def setArrayValues(values: NArray, offset: Option[Seq[Long]] = None, gIndex: Long = -1): Unit = { }

    /** Adds a new array with the given values
      */
    override def addArrayValues(values: NArray, gIndex: Long = -1): Unit = { }
  }

  /** abstact class to handle integer scalar values
    */
  abstract class MetaDataManager_i(
    metaInfo: MetaInfoRecord
  ) extends MetaDataManager(metaInfo) {

    def dispatch_i(value: Long, gIndex: Long);

    override def addValue(value: JValue, gIndex: Long = -1): Unit = {
      val gI = if (gIndex == -1)
        sectionManager.lastSectionGIndex
      else
        gIndex
      convertJValue_i(value) match {
        case Some(v) => dispatch_i(v, gI)
        case None => ()
      }
    }

    override def addRealValue(value: Double, gIndex: Long = -1): Unit = {
      val gI = if (gIndex == -1)
        sectionManager.lastSectionGIndex
      else
        gIndex
      dispatch_i(value.longValue, gI)
    }

    override def addArrayValue(shape: Seq[Long], gIndex: Long = -1): Unit = {
      if (!(shape.isEmpty || shape.length == 1 && shape(0) == 1))
        throw new InvalidAssignementException(metaInfo, "tried to add an non scalar array value to a scalar integer")
    }

    override def setArrayValues(values: NArray, offset: Option[Seq[Long]] = None, gIndex: Long = -1): Unit = {
       val gI = if (gIndex == -1)
        sectionManager.lastSectionGIndex
      else
        gIndex
      if (values.getSize() != 1)
        throw new InvalidAssignementException(metaInfo, s"invalid size (${values.getSize()}) for scalar value")
      offset match {
        case None =>
          ()
        case Some(o) =>
          if (!o.isEmpty && (o.length != 1 || o(0) != 0))
            throw new InvalidAssignementException(metaInfo, s"invalid offset ${o.mkString("[",",","]")} for scalar value")
      }
      dispatch_i(values.getLong(0), gI)
    }

    override def addArrayValues(values: NArray, gIndex: Long = -1): Unit = {
       val gI = if (gIndex == -1)
        sectionManager.lastSectionGIndex
      else
        gIndex
      if (values.getSize() != 1)
        throw new InvalidAssignementException(metaInfo, s"invalid size (${values.getSize()}) for scalar value")
      dispatch_i(values.getLong(0), gI)
    }
  }

  /** abstact class to handle floating point scalar values
    */
  abstract class MetaDataManager_f(
    metaInfo: MetaInfoRecord
  ) extends MetaDataManager(metaInfo) {

    def dispatch_f(value: Double, gIndex: Long);

    override def addValue(value: JValue, gIndex: Long = -1): Unit = {
      val gI = if (gIndex == -1)
        sectionManager.lastSectionGIndex
      else
        gIndex
      convertJValue_f(value) match {
        case Some(d) => dispatch_f(d, gI)
        case None => ()
      }
    }

    override def addRealValue(value: Double, gIndex: Long = -1): Unit = {
      val gI = if (gIndex == -1)
        sectionManager.lastSectionGIndex
      else
        gIndex
      dispatch_f(value, gI)
    }

    override def addArrayValue(shape: Seq[Long], gIndex: Long = -1): Unit = {
      if (!(shape.isEmpty || shape.length == 1 && shape(0) == 1))
        throw new InvalidAssignementException(metaInfo, "tried to add an non scalar array value to a scalar integer")
    }

    override def setArrayValues(values: NArray, offset: Option[Seq[Long]] = None, gIndex: Long = -1): Unit = {
       val gI = if (gIndex == -1)
        sectionManager.lastSectionGIndex
      else
        gIndex
      if (values.getSize() != 1)
        throw new InvalidAssignementException(metaInfo, s"invalid size (${values.getSize()}) for scalar value")
      offset match {
        case None =>
          ()
        case Some(o) =>
          if (!o.isEmpty && (o.length != 1 || o(0) != 0))
            throw new InvalidAssignementException(metaInfo, s"invalid offset ${o.mkString("[",",","]")} for scalar value")
      }
      dispatch_f(values.getDouble(0), gI)
    }

    override def addArrayValues(values: NArray, gIndex: Long = -1): Unit = {
       val gI = if (gIndex == -1)
        sectionManager.lastSectionGIndex
      else
        gIndex
      if (values.getSize() != 1)
        throw new InvalidAssignementException(metaInfo, s"invalid size (${values.getSize()}) for scalar value")
      dispatch_f(values.getDouble(0), gI)
    }
  }

  /** abstact class to handle json dictionary scalar values
    */
  abstract class MetaDataManager_D(
    metaInfo: MetaInfoRecord
  ) extends MetaDataManager(metaInfo) {
    def dispatch_D(value: JObject, gIndex: Long);

    override def addValue(value: JValue, gIndex: Long = -1): Unit = {
      val gI = if (gIndex == -1)
        sectionManager.lastSectionGIndex
      else
        gIndex
      convertJValue_D(value) match {
        case Some(obj) => dispatch_D(obj, gI)
        case None      => ()
      }
    }

    override def addRealValue(value: Double, gIndex: Long = -1): Unit = {
      throw new InvalidAssignementException(metaInfo, "addRealValue not supported")
    }

    override def addArrayValue(shape: Seq[Long], gIndex: Long = -1): Unit = {
      if (shape.length != 1)
        throw new InvalidAssignementException(metaInfo, "tried to add an non scalar array value to a scalar dictionary")
    }

    override def setArrayValues(values: NArray, offset: Option[Seq[Long]] = None, gIndex: Long = -1): Unit = {
       val gI = if (gIndex == -1)
        sectionManager.lastSectionGIndex
      else
        gIndex
      if (values.getSize() != 1)
        throw new InvalidAssignementException(metaInfo, s"invalid size (${values.getSize()}) for scalar value")
      offset match {
        case None =>
          ()
        case Some(o) =>
          if (!o.isEmpty && (o.length != 1 || o(0) != 0))
            throw new InvalidAssignementException(metaInfo, s"invalid offset ${o.mkString("[",",","]")} for scalar value")
      }
      /*JsonUtils.parseStr(values.getString(0)) match {
        case JObject(obj) =>
          dispatch_D(JObject(obj), gI)
        case JNothing =>
          ()
        case _ =>
          throw new InvalidAssignementException(metaInfo, s"invalid value ${values.getString(0)} when expecting a dictionary")
      }*/
    }

    override def addArrayValues(values: NArray, gIndex: Long = -1): Unit = {
      if (values.getSize() != 1)
        throw new InvalidAssignementException(metaInfo, s"invalid size (${values.getSize()}) for scalar value")
       val gI = if (gIndex == -1)
        sectionManager.lastSectionGIndex
      else
        gIndex
      /*JsonUtils.parseStr(values.getString(0)) match {
        case JObject(obj) =>
          dispatch_D(JObject(obj), gI)
        case JNothing =>
          ()
        case _ =>
          throw new InvalidAssignementException(metaInfo, s"invalid value $value when expecting a dictionary")
      }*/
    }
  }

  /** abstact class to handle string scalar values
    */
  abstract class MetaDataManager_C(
    metaInfo: MetaInfoRecord
  ) extends MetaDataManager(metaInfo) {

    def dispatch_C(value: String, gIndex: Long);

    override def addValue(value: JValue, gIndex: Long = -1): Unit = {
      val gI = if (gIndex == -1)
        sectionManager.lastSectionGIndex
      else
        gIndex
      convertJValue_C(value) match {
        case Some(s) =>
          dispatch_C(s, gI)
        case None =>
          ()
      }
    }

    override def addRealValue(value: Double, gIndex: Long = -1): Unit = {
      throw new InvalidAssignementException(metaInfo, "addRealValue not supported")
    }

    override def addArrayValue(shape: Seq[Long], gIndex: Long = -1): Unit = {
      if (shape.length != 1)
        throw new InvalidAssignementException(metaInfo, "tried to add an non scalar array value to a scalar string")
    }

    override def setArrayValues(values: NArray, offset: Option[Seq[Long]] = None, gIndex: Long = -1): Unit = {
       val gI = if (gIndex == -1)
        sectionManager.lastSectionGIndex
      else
        gIndex
      if (values.getSize() != 1)
        throw new InvalidAssignementException(metaInfo, s"invalid size (${values.getSize()}) for scalar value")
      offset match {
        case None =>
          ()
        case Some(o) =>
          if (!o.isEmpty && (o.length != 1 || o(0) != 0))
            throw new InvalidAssignementException(metaInfo, s"invalid offset ${o.mkString("[",",","]")} for scalar value")
      }
      //dispatch_C(values.getString(0), gI)
    }

    override def addArrayValues(values: NArray, gIndex: Long = -1): Unit = {
       val gI = if (gIndex == -1)
        sectionManager.lastSectionGIndex
      else
        gIndex
      if (values.getSize() != 1)
        throw new InvalidAssignementException(metaInfo, s"invalid size (${values.getSize()}) for scalar value")
      //dispatch_C(values.getString(0), gI)
    }
  }

  /** abstact class to handle binary blob scalar values
    */
  abstract class MetaDataManager_B64(
    metaInfo: MetaInfoRecord
  ) extends MetaDataManager(metaInfo) {
    def dispatch_B64(value: String, gIndex: Long);

    override def addValue(value: JValue, gIndex: Long = -1): Unit = {
      val gI = if (gIndex == -1)
        sectionManager.lastSectionGIndex
      else
        gIndex
      convertJValue_B64(value) match {
        case Some(s) =>
          dispatch_B64(s, gI)
        case None =>
          ()
      }
    }

    override def addRealValue(value: Double, gIndex: Long = -1): Unit = {
      throw new InvalidAssignementException(metaInfo, "addRealValue not supported")
    }

    override def addArrayValue(shape: Seq[Long], gIndex: Long = -1): Unit = {
      if (shape.length != 1)
        throw new InvalidAssignementException(metaInfo, "tried to add an non scalar array value to a scalar dictionary")
    }

    override def setArrayValues(values: NArray, offset: Option[Seq[Long]] = None, gIndex: Long = -1): Unit = {
       val gI = if (gIndex == -1)
        sectionManager.lastSectionGIndex
      else
        gIndex
      if (values.getSize() != 1)
        throw new InvalidAssignementException(metaInfo, s"invalid size (${values.getSize()}) for scalar value")
      offset match {
        case None =>
          ()
        case Some(o) =>
          if (!o.isEmpty && (o.length != 1 || o(0) != 0))
            throw new InvalidAssignementException(metaInfo, s"invalid offset ${o.mkString("[",",","]")} for scalar value")
      }
      //dispatch_B64(values.getString(0), gI)
    }

    override def addArrayValues(values: NArray, gIndex: Long = -1): Unit = {
      if (values.getSize() != 1)
        throw new InvalidAssignementException(metaInfo, s"invalid size (${values.getSize()}) for scalar value")
      //dispatch_B64(values.getString(0))
    }
  }

  /** returns shape an a sample elements of the nested json array
    *
    * if given expectedRank is the expected rank of the array, then the returned
    * shape will not be bigger than than expectedRank, it might still be smaller
    * and the caller is expected to handle that error
    */
  def shapeAndSampleEl(value: JValue, expectedRank: Int = -1): (Seq[Long],JValue) = {
    val shape: ListBuffer[Long] = ListBuffer[Long]()
    var el: JValue = value
    var missingRank: Int = expectedRank
    while (missingRank != 0) {
      el match {
        case JArray(arr) =>
          shape.append(arr.count{
            case JNothing => false
            case _ => true}.toLong)
          missingRank -= 1
          if (arr.isEmpty) {
            // if missingRank > 0 we could throw/warn, bt we leave it to the caller
            missingRank = 0
            el = JNothing
          } else {
            el = arr(0)
          }
        case _ =>
          // if missingRank > 0 we could throw/warn, bt we leave it to the caller
          missingRank = 0
      }
    }
    shape.toSeq -> el
  }

  /** If shape is not compatible with refShape throws an exception
    */
  def shapeCheck(metaInfo: MetaInfoRecord, shape: Seq[Long], refShape: Seq[Either[Long, String]]): Unit = {
    if (shape.length != refShape.length)
      throw new InvalidAssignementException(metaInfo, s"invalid shape.length (${shape.length} vs ${refShape.length})")
    for ((expected,actual) <- refShape.zip(shape)) {
      expected match {
        case Left(i) =>
          if (i != actual)
            throw new InvalidAssignementException(metaInfo, s"shape $shape is not compatible with the expected one ${refShape.mkString("[",",","]")}")
        case Right(s) =>
          ()
      }
    }
  }

  /** An iterator on a rectangular JArray of known shape for a given meta info
    */
  class RectangularJArrayIterator(
    val metaInfo: MetaInfoRecord,
    val shape: Seq[Long],
    var el: JValue,
    var iterators: List[List[JValue]] = Nil,
    var depth: Int = 0,
    idx: Option[Seq[Long]] = None,
    offset0: Option[Seq[Long]] = None
  ) extends Iterator[(Array[Long],JValue)] {
    val offset = Array.fill[Long](shape.size)(0)
    if (!offset0.isEmpty)
      offset0.get.copyToArray(offset)
    val index = Array.fill[Long](shape.size)(0)
    if (!idx.isEmpty)
      idx.get.copyToArray(index)
    else
      offset.copyToArray(index)
    val prevIndex = Array.fill[Long](shape.size)(-1)

    /** goes on on the same level or up until it can progress
      */
    def advance(): Unit = {

      iterators match {
        case currentList :: otherLists =>
          currentList.dropWhile(_ == JNothing) match {
            case newEl :: newList =>
              el = newEl
              iterators = newList :: otherLists
              index(depth - 1) += 1
            case Nil =>
              depth -= 1
              index(depth) = offset(depth)
              iterators = otherLists
              advance()
          }
        case Nil =>
          el = JNothing
      }
    }

    /** checks if the iterator ended properly
      */
    def checkEnd(): Unit = {
      val leftOver = el
      el = JNothing
      var invalid: Boolean = false
      for ((i, j) <- shape.zip(index)) {
        if (i != j + 1)
          invalid = true
      }
      val invalidMsg = if (invalid)
        s"iterator finished with index ${index.mkString("[",",","]")} when shape is ${shape.mkString("[",",","]")}"
      else
        ""
      if (leftOver != JNothing)
        throw new InvalidAssignementException(
          metaInfo,
          s"array finished with leftover objects, $invalidMsg, left over: ${JsonUtils.prettyStr(leftOver)}")
      if (invalid)
        throw new InvalidAssignementException(metaInfo, invalidMsg)
    }

    /** Goes deeper until it reaches the wanted depth (rank)
      */
    def goToRank(): Unit = {
      while (depth != shape.size) {
        el match {
          case JArray(arr) =>
            arr.dropWhile( _ == JNothing) match {
              case newEl :: rest =>
                el = newEl
                iterators =  rest :: iterators
                depth += 1
              case Nil =>
                checkEnd()
                return
            }
          case JNothing =>
            checkEnd()
          case _ =>
            checkEnd()
        }
      }
    }

    // guarantee that the initial state is valid state
    // this simplifies hasNext, next
    goToRank()

    /** returns true if the iterator has more elements
      */
    def hasNext: Boolean = el != JNothing

    /** goes to the next element and returns it
      *
      * The index array will be invalidated with the next call to next()
      */
    def next(): (Array[Long], JValue) = {
      index.copyToArray(prevIndex)
      val prevEl = el
      advance()
      goToRank()
      (prevIndex, prevEl)
    }
  }

  /** converts a JValue to an array
    *
    * assumes that createArray returns a canonically ordered array with the given shape
    */
  def convertJValueToArray[T](
    metaInfo: MetaInfoRecord,
    value: JValue,
    createArray: (Seq[Long]) => T,
    setValueAtFlatIndex: (T, Long, JValue) => Unit
  ): T = {
    val expectedShape = metaInfo.shape.getOrElse(Seq())
    val (shape, sampleVal) = GenericBackend.shapeAndSampleEl(value, expectedRank = expectedShape.length)
    GenericBackend.shapeCheck(metaInfo, shape, expectedShape)
    val iter = new RectangularJArrayIterator(
      metaInfo = metaInfo,
      shape = shape,
      el = value)
    val array = createArray(shape)
    var ii: Long = 0
    while (iter.hasNext) {
      val (index, el) = iter.next()
      setValueAtFlatIndex(array, ii, el)
      ii += 1
    }
    array
  }

  abstract class ArrayMetaDataManager(
    metaInfo: MetaInfoRecord
  ) extends MetaDataManager(metaInfo) {

    def addRealValue(value: Double, gIndex: Long = -1): Unit = {
      throw new InvalidAssignementException(metaInfo, "addRealValue not supported")
    }
  }

  /** meta info for array of ints
    */
  abstract class ArrayMetaDataManager_i32(
    metaInfo: MetaInfoRecord
  ) extends ArrayMetaDataManager(metaInfo) {

    def createArray(shape: Seq[Long]): NArray = {
      NArray.factory(DataType.INT, shape.map(_.intValue).toArray)
    }

    def convertAllJValue_i32(value: JValue): Int = {
      convertJValue_i(value) match {
        case Some(longVal) =>
          if (longVal < Int.MinValue || longVal > Int.MaxValue)
            throw new InvalidAssignementException(metaInfo, s"Out of bounds value $value for type i32")
          longVal.intValue
        case None =>
          throw new InvalidAssignementException(metaInfo, s"cannot convert value from $value")
      }
    }

    def setFlatIndex_i32(array: NArray, i: Long, value: Int): Unit = {
      array.setInt(i.intValue, value)
    }

    override def addValue(value: JValue, gIndex: Long): Unit = {
      addArrayValues(convertJValueToArray[NArray](metaInfo, value, createArray _, {
        (array: NArray, i: Long, value: JValue) =>
        setFlatIndex_i32(array, i, convertAllJValue_i32(value))
      }), gIndex)
    }
  }

  /** meta info for array of longs
    */
  abstract class ArrayMetaDataManager_i64(
    metaInfo: MetaInfoRecord
  ) extends ArrayMetaDataManager(metaInfo) {

    def createArray(shape: Seq[Long]): NArray = {
      NArray.factory(Long.getClass(), shape.map(_.intValue).toArray)
    }

    def convertAllJValue_i64(value: JValue): Long = {
      convertJValue_i(value) match {
        case Some(i) =>
          i
        case None =>
          throw new InvalidAssignementException(metaInfo, s"cannot convert value from $value")
      }
    }

    def setFlatIndex_i64(array: NArray, i: Long, value: Long): Unit = {
      array.setLong(i.intValue, value)
    }

    override def addValue(value: JValue, gIndex: Long): Unit = {
      addArrayValues(convertJValueToArray[NArray](metaInfo, value, createArray _, {
        (array: NArray, i: Long, value: JValue) =>
        setFlatIndex_i64(array, i, convertAllJValue_i64(value))
      }), gIndex)
    }
  }

  /** meta info for array of floats
    */
  abstract class ArrayMetaDataManager_f32(
    metaInfo: MetaInfoRecord
  ) extends ArrayMetaDataManager(metaInfo) {

    def createArray(shape: Seq[Long]): NArray = {
      NArray.factory(Float.getClass(), shape.map(_.intValue).toArray)
    }

    def convertAllJValue_f32(value: JValue): Float = {
      convertJValue_f(value) match {
        case Some(d) =>
          d.floatValue
        case None =>
          throw new InvalidAssignementException(metaInfo, s"cannot convert value from $value")
      }
    }

    def setFlatIndex_f32(array: NArray, i: Long, value: Float): Unit = {
      array.setFloat(i.intValue, value)
    }

    override def addValue(value: JValue, gIndex: Long): Unit = {
      addArrayValues(convertJValueToArray[NArray](metaInfo, value, createArray _, {
        (array: NArray, i: Long, value: JValue) =>
        setFlatIndex_f32(array, i, convertAllJValue_f32(value))
      }), gIndex)
    }
  }

  /** meta info for array of doubles
    */
  abstract class ArrayMetaDataManager_f64(
    metaInfo: MetaInfoRecord
  ) extends ArrayMetaDataManager(metaInfo) {

    def createArray(shape: Seq[Long]): NArray = {
      NArray.factory(Float.getClass(), shape.map(_.intValue).toArray)
    }

    def convertAllJValue_f64(value: JValue): Double = {
      convertJValue_f(value) match {
        case Some(d) =>
          d
        case None =>
          throw new InvalidAssignementException(metaInfo, s"cannot convert value from $value")
      }
    }

    def setFlatIndex_f64(array: NArray, i: Long, value: Double): Unit = {
      array.setDouble(i.intValue, value)
    }

    override def addValue(value: JValue, gIndex: Long): Unit = {
      addArrayValues(convertJValueToArray[NArray](metaInfo, value, createArray _, {
        (array: NArray, i: Long, value: JValue) =>
        setFlatIndex_f64(array, i, convertAllJValue_f64(value))
      }), gIndex)
    }
  }

  /** meta info for array of bytes
    */
  abstract class ArrayMetaDataManager_b(
    metaInfo: MetaInfoRecord
  ) extends ArrayMetaDataManager(metaInfo) {

    def createArray(shape: Seq[Long]): NArray = {
      NArray.factory(Byte.getClass(), shape.map(_.intValue).toArray)
    }

    def convertAllJValue_b(value: JValue): Byte = {
      convertJValue_i(value) match {
        case Some(i) =>
          if (i < Byte.MinValue || i > 255)
            throw new InvalidAssignementException(metaInfo, s"value $value out of bounds for Byte")
          i.byteValue
        case None =>
          throw new InvalidAssignementException(metaInfo, s"cannot convert value from $value")
      }
    }

    def setFlatIndex_b(array: NArray, i: Long, value: Byte): Unit = {
      array.setByte(i.intValue, value)
    }

    override def addValue(value: JValue, gIndex: Long): Unit = {
      addArrayValues(convertJValueToArray[NArray](metaInfo, value, createArray _, {
        (array: NArray, i: Long, value: JValue) =>
        setFlatIndex_b(array, i, convertAllJValue_b(value))
      }), gIndex)
    }
  }

  /** meta info for array of strings
    */
  abstract class ArrayMetaDataManager_C(
    metaInfo: MetaInfoRecord
  ) extends ArrayMetaDataManager(metaInfo) {

    def createArray(shape: Seq[Long]): ArrayString = {
      new ArrayString(shape.map(_.intValue).toArray)
    }

    def convertAllJValue_C(value: JValue): String = {
      convertJValue_C(value) match {
        case Some(s) =>
          s
        case None =>
          throw new InvalidAssignementException(metaInfo, s"cannot convert value from $value")
      }
    }

    def setFlatIndex_C(array: ArrayString, i: Long, value: String): Unit = {
      val index = array.getIndex() // inefficient
      index.setCurrentCounter(i.intValue)
      array.set(index, value)
    }

    override def addValue(value: JValue, gIndex: Long): Unit = {
      addArrayValues(convertJValueToArray[ArrayString](metaInfo, value, createArray _, {
        (array: ArrayString, i: Long, value: JValue) =>
        setFlatIndex_C(array, i, convertAllJValue_C(value))
      }), gIndex)
    }
  }

  /** meta info for array of byte arrays (blobs)
    *
    * This should be improved, currently data is stored as Base64 url encoded
    * strings, but ArraySequence of bytes is probably better and should be
    * evaluated
    */
  abstract class ArrayMetaDataManager_B64(
    metaInfo: MetaInfoRecord
  ) extends ArrayMetaDataManager(metaInfo) {

    def createArray(shape: Seq[Long]): ArrayString = {
      new ArrayString(shape.map(_.intValue).toArray)
    }

    def convertAllJValue_B64(value: JValue): String = {
      convertJValue_C(value) match {
        case Some(s) =>
          s
        case None =>
          throw new InvalidAssignementException(metaInfo, s"cannot convert value from $value")
      }
    }

    def setFlatIndex_B64(array: ArrayString, i: Long, value: String): Unit = {
      val index = array.getIndex()  // inefficient
      index.setCurrentCounter(i.intValue)
      array.set(index, value)
    }

    override def addValue(value: JValue, gIndex: Long): Unit = {
      addArrayValues(convertJValueToArray[ArrayString](metaInfo, value, createArray _, {
        (array: ArrayString, i: Long, value: JValue) =>
        setFlatIndex_B64(array, i, convertAllJValue_B64(value))
      }), gIndex)
    }
  }

  /** meta info for array of byte arrays (blobs)
    *
    * This should be improved, currently data is stored as Base64 url encoded
    * strings, but ArraySequence of bytes representing the UTF8 encoded json
    * is probably better and should be evaluated
    */
  abstract class ArrayMetaDataManager_D(
    metaInfo: MetaInfoRecord
  ) extends ArrayMetaDataManager(metaInfo) {

    def createArray(shape: Seq[Long]): ArrayString = {
      new ArrayString(shape.map(_.intValue).toArray)
    }

    def convertAllJValue_D(value: JValue): String = {
      convertJValue_D(value) match {
        case Some(d) =>
          JsonUtils.normalizedStr(value)
        case None =>
          throw new InvalidAssignementException(metaInfo, s"cannot convert value from $value")
      }
    }

    def setFlatIndex_D(array: ArrayString, i: Long, value: String): Unit = {
      val index = array.getIndex() // inefficient
      index.setCurrentCounter(i.intValue)
      array.set(index, value)
    }

    override def addValue(value: JValue, gIndex: Long): Unit = {
      addArrayValues(convertJValueToArray[ArrayString](metaInfo, value, createArray _, {
        (array: ArrayString, i: Long, value: JValue) =>
        setFlatIndex_D(array, i, convertAllJValue_D(value))
      }), gIndex)
    }
  }

  /** root super sections of the meta info with the given metaName
    *
    * These are the first ancestors of type type_section in the inheritance DAG
    * of metaName
    */
  def firstSuperSections(metaEnv: MetaInfoEnv, metaName: String): Set[String] = {
    val allAnchestors = metaEnv.firstAncestorsByType(metaName)
    allAnchestors.get("type_section") match {
      case Some(anc) =>
        anc._1
      case None =>
        Set()
    }
  }

}

abstract class GenericBackend(
  val metaInfoEnv: MetaInfoEnv
) extends ParserBackendInternal {

  /** the manger for the sections
    */
  def sectionManagers: Map[String, GenericBackend.SectionManager];

  /** mangers for data
    */
  def metaDataManagers: Map[String, GenericBackend.MetaDataManager];

  /** returns the sections that are still open
    *
    * sections are identified by metaName and their gIndex
    */
  override def openSections(): Iterator[(String, Long)] = {
    sectionManagers.foldLeft(Iterator.empty: Iterator[(String,Long)]){ (it: Iterator[(String,Long)], el: (String, GenericBackend.SectionManager)) =>
      val (sectionName, sectionManager) = el
      it ++ Iterator.continually(sectionName).zip(sectionManager.openSectionsGIndexes())
    }
  }

  /** returns information on an open section (for debugging purposes)
    */
  override def openSectionInfo(metaName: String, gIndex: Long): String = {
    sectionManagers(metaName).openSectionInfo(gIndex)
  }

  /** opens a new section.
    */
  override def openSection(metaName: String): Long = {
    sectionManagers(metaName).openSection(this)
  }

  /** sets info values of an open section.
    *
    * references should be references to gIndex of the root sections this section refers to.
    */
  override def setSectionInfo(metaName: String, gIndex: Long, references: Map[String, Long]) = {
    sectionManagers(metaName).setSectionInfo(gIndex, references)
  }

  /** closes a section
    *
    * after this no other value can be added to the section.
    * metaName is the name of the meta info, gIndex the index of the section
    */
  override def closeSection(metaName: String, gIndex: Long): Unit = {
    sectionManagers(metaName).closeSection(this, gIndex)
  }

  /** returns a data manager for the given name
    */
  def dataManagerForName(metaName: String): GenericBackend.MetaDataManager = {
    metaDataManagers.get(metaName) match {
      case Some(manager) =>
        manager
      case None =>
        metaInfoEnv.metaInfoRecordForName(metaName, true, true) match {
          case Some(metaInfo) =>
            throw new GenericBackend.UnexpectedDataOutputException(metaInfo)
          case None =>
            throw new GenericBackend.UnknownMetaInfoException(metaName, "When being asked to add a value.")
        }
    }
  }

  /** Adds a json value corresponding to metaName.
    *
    * The value is added to the section the meta info metaName is in.
    * A gIndex of -1 means the latest section.
    */
  override def addValue(metaName: String, value: JValue, gIndex: Long = -1): Unit = {
    dataManagerForName(metaName).addValue(value, gIndex)
  }

  /** Adds a floating point value corresponding to metaName.
    *
    * The value is added to the section the meta info metaName is in.
    * A gIndex of -1 means the latest section.
    */
  override def addRealValue(metaName: String, value: Double, gIndex: Long = -1): Unit = {
    dataManagerForName(metaName).addRealValue(value, gIndex)
  }

  /** Adds a new array value of the given size corresponding to metaName.
    *
    * The value is added to the section the meta info metaName is in.
    * A gIndex of -1 means the latest section.
    * The array is unitialized.
    */
  override def addArrayValue(metaName: String, shape: Seq[Long], gIndex: Long = -1): Unit = {
    dataManagerForName(metaName).addArrayValue(shape, gIndex)
  }

  /** Adds values to the last array added
    */
  override def setArrayValues(
    metaName: String, values: NArray,
    offset: Option[Seq[Long]] = None,
    gIndex: Long = -1): Unit = {
    dataManagerForName(metaName). setArrayValues(values, offset, gIndex)
  }

  /** Adds an array value with the given array values
    */
  override def addArrayValues(metaName: String, values: NArray, gIndex: Long = -1): Unit = {
    dataManagerForName(metaName).addArrayValues(values, gIndex)
  }

}
