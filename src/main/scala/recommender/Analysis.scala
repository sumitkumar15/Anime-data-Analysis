package recommender

import org.apache.spark.rdd.RDD
import org.apache.spark.{SparkConf, SparkContext}

/**
  * Created by sumit on 27/7/17.
  */
object Analysis {
  @transient lazy val conf: SparkConf = new SparkConf().setMaster("local").setAppName("Anime-Recommender")
  @transient lazy val sc: SparkContext = new SparkContext(conf)
  sc.setLogLevel("ERROR")

  /** Main function */
  def main(args: Array[String]): Unit = {


    //RDD[Anime]
    lazy val animeList = readAnimeList
    //RDD[RatedData]
    lazy val ratingList = readRatingData

//    So what are some questions we can ask?
//
//    Are ratings consistent across genres?
//      Are ratings consistent across the number of episodes?
//    Do more episodes lead to a higher or lower average rating?
//      How does popularity relate to the number of episodes?
//      Do individuals rating on the same scale, or different scales?
//      How many people rate an anime, versus watch it? Does it depend on genre?
//    How do ratings vary by type (e.g. movie, TV, Special, OVA, etc)

//     >>counting anime by genre
    println("Tag    Count")
    mostTaggedGenre(animeList)
      .collect()
      .foreach(println)

    // >> consistency of each rating being given
    println("Rating Count   (-1 if watched but not rated)")
        consistentRatings(ratingList)
            .sortBy(_._1)
          .collect()
          .foreach(println)

// >> Avg Rating of Genre
    lazy val percentRatedByGen = percentRatedByGenre(ratingList,animeList)

    // >>  How many people rate an anime, versus watch it? Does it depend on genre?
        time{
          println("%Rated| mean(Genre)| Genre")
          percentRatedByGen
            .collect()
                .sortBy(f => f._2)
                  .foreach(p => println(f"${p._2._1.formatted("%2.2f")} | ${p._2._2.formatted("%1.2f")} |  ${p._1}"))
        }

    // >> All Ratings across type of show
    print("\n")
    variationByType(ratingList, animeList)
      .collect()
      .foreach(p => {
        print(p._1 + " ")
        for (i <- -1 to 10) print("{"+i+","+p._2.getOrElse(i,0) + "}  ")
        print("\n")
      }
      )
  }

  def readAnimeList: RDD[Anime] = {
    //    anime_id,name,genre,type,episodes,rating,members
    val lines = sc.textFile("src/main/resources/anime.csv")
    val toBeDel = lines.first()
    new AnimeData(lines.filter(_ != toBeDel)).animeInfo
  }
  def readRatingData: RDD[RatedData]={
    val ratingLines = sc.textFile("src/main/resources/rating.csv")
    val toBeDel = ratingLines.first()
    //RDD[RatedData]
    new RatingData(ratingLines.filter(f => f != toBeDel)).ratingInfo
  }

  // Do individuals rating on the same scale, or different scales?

  def time[R](block: => R): R = {
    val t0 = System.nanoTime()
    val result = block
    // call-by-name
    val t1 = System.nanoTime()
    println("Elapsed time: " + (t1 - t0) + "ns")
    result
  }

  def countTags(data: RDD[Anime]):RDD[(String,Int)] = {
    data.map(anime => anime.genre.replaceAll("\"","").split(",")
      .map(tag =>(tag.trim,1)))
        .flatMap(list => list)
          .groupByKey()
            .mapValues(v => v.size)
  }
  /**
    * prints out list of anime genre with no of anime tagged in
    * @param data RDD of case class Anime
    */
  def mostTaggedGenre(data: RDD[Anime]):RDD[(String,Int)] = countTags(data).sortBy(lam => lam._2,ascending = false)

  // no of ratings of each rating value
  def consistentRatings(data: RDD[RatedData]):RDD[(Int,Int)] ={
    data.map(rated => (rated.userRating,1))
      .groupByKey()
        .mapValues(_.size)
  }

  def percentRatedByGenre(_ratingData: RDD[RatedData], _animeData: RDD[Anime]): RDD[(String,(Double,Double))] ={
    val result = getRatingCountById(_ratingData,_animeData)
    getPercentageRatedByGenre(result)
  }

  def getRatingCountById(_ratingData: RDD[RatedData], _animeData: RDD[Anime]): RDD[(Int,(String,Option[(Int,Int,Iterable[Int])]))]={
    // no or ratings / total watched *100
    val animeWithGenre = _animeData
      .map(anime => (anime.id, anime.genre.replaceAll("\"","").replaceAll(" ","").split(",")))
        .flatMap{case (id: Int,arr: Array[String]) => arr.map( (p) => (id,p) )}

    val animeIdWithRatingCount = _ratingData
      .groupBy(rated => rated.animeId)
        .mapValues(value => (value.count(p => p.userRating != -1),value.size,
          {
            val temp=value.filter(p => p.userRating != -1)
            temp.map(lam => lam.userRating)
          }
        ))

    animeWithGenre.leftOuterJoin(animeIdWithRatingCount)
  }

  def getPercentageRatedByGenre(data: RDD[(Int,(String,Option[(Int,Int,Iterable[Int])]))]): RDD[(String,(Double,Double))]={
    data.map(param => (param._2._1,param._2._2.getOrElse( (0,0,Iterable[Int]()) )))
        .mapValues(f => (f._1,f._2,(f._3.sum,f._3.size)))
          .groupByKey()
            .mapValues(f => f.foldLeft((0,0,(0,0)))({case (x,y) => (x._1+y._1,x._2+y._2,(x._3._1+y._3._1,x._3._2+y._3._2))}))
              .mapValues(func => ((func._1.toDouble / func._2.toDouble)*100,func._3._1.toDouble/func._3._2))
  }

def variationByType(_ratingData: RDD[RatedData],_animeData: RDD[Anime]): RDD[(String,Map[Int,Int])] ={
  val ratingPairWithId = _ratingData.map( lam => (lam.animeId,lam.userRating))
  val animePairWithId = _animeData.map(lam => (lam.id,lam.atype))

  ratingPairWithId.leftOuterJoin(animePairWithId)
    .map(lam => (lam._2._2.getOrElse("NA"),lam._2._1))
    .groupByKey()
    .mapValues(lam => lam.map(l => (l,1)).groupBy(k => k._1).map(m => (m._1,m._2.size)))
}

}