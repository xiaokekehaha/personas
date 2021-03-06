import java.io.{File, PrintWriter}
import java.text.SimpleDateFormat
import java.util.{Calendar, Date}
import org.apache.spark.{SparkContext, SparkConf}

import scala.io.Source



/**
 * Created by lcm on 2016/3/23.
 * 此类用于过滤股票和奢侈品用户的ad
 */
object FilterAd {

  val setStockAd = new scala.collection.mutable.TreeSet[String]()
  val setRichAd = new scala.collection.mutable.TreeSet[String]()
  val setMergeAd = new scala.collection.mutable.TreeSet[String]()

  def main(args: Array[String]) {

    if (args.length == 4) {
      val srcIn = args(0) + getDay + "/" + "*"
      val StockAdOut = args(1)
      val RichAdOut = args(2)
      val MergeAdOut = args(3)

      val sparkConf = new SparkConf().setAppName("FILTER AD")
      val sc = new SparkContext(sparkConf)

      //输入的数据文件
      val data = sc.textFile("file://" + srcIn)

      //以前的股票用户ad和访问网站次数
      val fileStockAd = sc.textFile("file://" + StockAdOut).filter(_.split("\t").length == 2).map(x => {
        val arr = x.split("\t")
        (arr(0), arr(1).toInt)
      })
      //以前的奢侈品用户ad和访问网站次数
      val fileRichAd = sc.textFile("file://" + RichAdOut).filter(_.split("\t").length == 2).map(x => {
        val arr = x.split("\t")
        (arr(0), arr(1).toInt)
      })

      //过滤出新一天访问了股票用户的ad和访问网站次数
      val stockAdRdd = data.map(x => x.split("\t"))
        .filter(_.size > 3)
        .filter(!_ (1).contains("."))
        .filter(filterUrl1)
        .map(x => (x(1), 1))
        .groupByKey()
        .map(x => (x._1, x._2.size))

      //合并股票用户的ad和访问网站次数
      stockAdRdd.fullOuterJoin(fileStockAd).filter(x => x._1 != null).map(x => {
        if (x._2._2 == None) {
          x._1 + "\t" + x._2._1.get
        } else if (x._2._1 == None) {
          x._1 + "\t" + x._2._2.get
        } else {
          x._1 + "\t" + (x._2._1.get + x._2._2.get)
        }
      }).foreach(x => {
        setStockAd.+=(x)
      })

      //过滤出新一天奢侈品用户的ad和访问网站次数
      val richAdRdd = data.map(x => x.split("\t"))
        .filter(_.size > 3)
        .filter(filterUrl2)
        .filter(!_ (1).contains(".")).map(x => (x(1), 1)).groupByKey().map(x => (x._1, x._2.size)) //.saveAsTextFile("C:\\Users\\lcm\\Documents\\new_rich_ad")

      //合并奢侈品用户的ad和访问网站次数
      richAdRdd.fullOuterJoin(fileRichAd).map(x => {
        if (x._2._2 == None) {
          x._1 + "\t" + x._2._1.get
        } else if (x._2._1 == None) {
          x._1 + "\t" + x._2._2.get
        } else {
          x._1 + "\t" + (x._2._1.get + x._2._2.get)
        }
      }).foreach(x => {
        setRichAd.+=(x)
      })

      //输出的股票用户ad
      val writerStockAd = new PrintWriter(new File(StockAdOut), "UTF-8")
      //输出的奢侈品ad
      val writerRichAd = new PrintWriter(new File(RichAdOut), "UTF-8")
      //输出的合并后的ad
      val writerMergeAd = new PrintWriter(new File(MergeAdOut), "UTF-8")

      //最新ad集合输出到文件
      for (x <- setStockAd) {
        writerStockAd.write(x + "\n")
      }
      //最新奢侈品ad输出到文件
      for (x <- setRichAd) {
        writerRichAd.write(x + "\n")
      }
      //重新获取股票ad和奢侈品ad
      val newFileStockAd = sc.textFile("file://" + StockAdOut)
      val newFileRichAd = sc.textFile("file://" + RichAdOut)

      val rdd1 = newFileStockAd.map(x => x.split("\t")).filter(_.length == 2).map(x => (x(0), x(1).toInt))
      val rdd2 = newFileRichAd.map(x => x.split("\t")).filter(_.length == 2).map(x => (x(0), x(1).toInt))
      //合并相同的ad
      val rdd = rdd1.join(rdd2).map(x => {
        x._1 + "\t" + (x._2._1 + x._2._2)
      })

      rdd.foreach(x => {
        setMergeAd.+=(x)
      })

      for (x <- setMergeAd) {
        writerMergeAd.write(x + "\n")
      }
      writerStockAd.close()
      writerRichAd.close()
      writerMergeAd.close()
    }
  }

  //判断是否访问过股票网站
  def filterUrl1(arrs: Array[String]): Boolean = {
    val strStockUrl  =getUrlString("stock")
    //股票网站的url字符串
    val urls = arrs(3).split("/")
    if (urls.length > 0) {
      val url = if (urls(0) == "http:" || urls(0) == "https:") urls(2) else urls(0)
      if (strStockUrl.contains(url) || arrs(3).contains("live.sina.com.cn/zt/f/v/finance/globalnews1")) {
        return true
      }
    }
    return false
  }

  //判断是否访问过奢侈品网站
  def filterUrl2(data: Array[String]): Boolean = {
    //奢侈品和汽车的url字符串
    val strCarUrl =getUrlString("rich")
    val urls = data(3).split("/")
    if (urls.length > 0) {
      val url = if (urls(0) == "http:" || urls(0) == "https:") urls(2) else urls(0)
      if (strCarUrl.contains(url)) {
        return true
      }
    }
    return false
  }

  //读取配置文件获取需要过滤的url
  def getUrlString(fileName: String): String = {
    var str = ""
    for (line <- Source.fromInputStream(FilterAd.getClass.getResourceAsStream("/" + fileName)).getLines) {
      str = str + "," + line
    }
    str.replaceFirst(",", "")
  }

  //获取日期格式
  def getDay: String = {
    val calendar = Calendar.getInstance(); //得到日历
    calendar.setTime(new Date); //把当前时间赋给日历
    calendar.add(Calendar.DAY_OF_MONTH, -1);
    //设置为前一天
    val dBefore = calendar.getTime();
    val sdf: SimpleDateFormat = new SimpleDateFormat("yyyy-MM-dd")
    val date: String = sdf.format(dBefore)
    date
  }

}
