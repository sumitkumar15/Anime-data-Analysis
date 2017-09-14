package recommender

import org.apache.spark.rdd.RDD

/**
  * Created by sumit on 28/8/17.
  */
class AnimeData(data: RDD[String]) {
  def rawData(lines: RDD[String]): RDD[Anime]={
    lines.map(line => {
      val regexStr="([\"'])(?:(?=(\\\\?))\\2.)*?\\1".r
      val extractQuotes=regexStr.findAllIn(line).toArray
      val fstr=line.replaceAll("([\"'])(?:(?=(\\\\?))\\2.)*?\\1","")
      val arr = fstr.split(",")
      Anime(
        id = arr(0).toInt,
        name = if(arr(1).isEmpty) extractQuotes(0) else arr(1),
        genre = if(!extractQuotes.isEmpty) if(arr(2).isEmpty) { if(arr(1).isEmpty) extractQuotes(1) else extractQuotes(0) } else arr(2)  else arr(2),
        atype = arr(3),
        ep_count = Some(arr(4)),
        rating = arr(5) match {
          case "" => None
          case _ => Some(arr(5).toDouble)
        },
        members = arr(6).toInt
      )
    })
  }
  def animeInfo=rawData(data)
}
case class Anime(id: Int, name: String, genre: String, atype: String, ep_count: Some[String],rating: Option[Double], members: Int) extends Serializable
