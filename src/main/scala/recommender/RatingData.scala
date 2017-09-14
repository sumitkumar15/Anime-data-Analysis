package recommender

import org.apache.spark.rdd.RDD

/**
  * Created by sumit on 28/8/17.
  */
class RatingData(data: RDD[String]) {

  def rawData(raw: RDD[String]):RDD[RatedData]={
    raw.map ( line => {
      val lineData = line.split(",")
      RatedData(
        userId = lineData(0).toInt,
        animeId = lineData(1).toInt,
        userRating = lineData(2).toInt
      )
    })
  }
  def ratingInfo = rawData(data)
}
case class RatedData(userId: Int,animeId: Int, userRating: Int)
