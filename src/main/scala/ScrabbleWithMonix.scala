import java.io.File
import java.util
import java.util.concurrent.TimeUnit
import java.util.{ArrayList, Comparator, HashMap, Map}

import io.reactivex.internal.subscribers.BlockingFirstSubscriber
import monix.execution.{Cancelable, Scheduler}
import monix.execution.schedulers.ExecutionModel
import monix.reactive.Observable

import scala.collection.mutable
import scala.io.Source
import scala.collection.JavaConverters._

object ScrabbleWithMonix {

  var letterScores = Array(
    // a, b, c, d, e, f, g, h, i, j, k, l, m, n, o, p,  q, r, s, t, u, v, w, x, y,  z
    1, 3, 3, 2, 1, 4, 2, 4, 1, 8, 5, 1, 3, 1, 1, 3, 10, 1, 1, 1, 1, 4, 4, 8, 4, 10);

  var scrabbleAvailableLetters = Array(
    // a, b, c, d,  e, f, g, h, i, j, k, l, m, n, o, p, q, r, s, t, u, v, w, x, y, z
    9, 2, 2, 1, 12, 2, 3, 2, 9, 1, 1, 4, 2, 6, 8, 2, 1, 6, 4, 6, 4, 2, 2, 1, 2, 1);


  var scrabbleWords: mutable.Set[String] = null;
  var shakespeareWords: mutable.Set[String] = null;

  @volatile var result : Any = null;

  def main(args: Array[String]): Unit = {
    val n = 10;

    scrabbleWords = new mutable.HashSet[String]();

    for (line <- Source.fromFile(new File("files/ospd.txt")).getLines()) {
      scrabbleWords.add(line.toLowerCase())
    }

    shakespeareWords = new mutable.HashSet[String]();
    for (line <- Source.fromFile(new File("files/words.shakespeare.txt")).getLines()) {
      shakespeareWords.add(line.toLowerCase())
    }

    System.out.println(scrabbleWords.size)
    System.out.println(shakespeareWords.size)

    System.out.println(scrabble(false));

    val times = new ArrayList[Long]();

    for (i <- 1 to n) {
      val start = System.nanoTime();
      result = scrabble(false);
      val end = System.nanoTime();

      times.add(end - start)
    }

    times.sort(new Comparator[Long] {
      override def compare(o1: Long, o2: Long): Int = o1.compareTo(o2)
    });

    System.out.print("%.2f ms%n".format(times.get(times.size() / 2) / 1000000.0));

    times.clear()

    System.out.println(scrabble(true));

    for (i <- 1 to n) {
      val start = System.nanoTime();
      result = scrabble(true);
      val end = System.nanoTime();

      times.add(end - start)
    }

    times.sort(new Comparator[Long] {
      override def compare(o1: Long, o2: Long): Int = o1.compareTo(o2)
    });

    System.out.print("%.2f ms%n".format(times.get(times.size() / 2) / 1000000.0));
  }

  def scrabble(double: Boolean): Any = {
    val scoreOfALetter = (letter: Char) => {
      letterScores(letter - 'a')
    }

    val letterScore = (entry: Map.Entry[Int, Long]) => {
      letterScores(entry.getKey() - 'a') *
        Integer.min(entry.getValue().intValue(), scrabbleAvailableLetters(entry.getKey() - 'a'))
    }


    val toInteger = (string: String) => {
      Observable.range(0, string.length).map((v: Long) => string.charAt(v.toInt))
    }

    val histoOfLetters = (word: String) => {
      val map = new HashMap[Int, Long]();
      toInteger(word)
        .map((value: Char) => {
          val current = map.get(value.toInt);
          if (current == null) {
            map.put(value, 1L)
          } else {
            map.put(value, current + 1L)
          }
          map
      }).takeLast(1)
    }

    val blank = (entry: Map.Entry[Int, Long]) => {
      Math.max(0L, entry.getValue() - scrabbleAvailableLetters(entry.getKey() - 'a'))
    }

    val nBlanks = (word: String) => {
      histoOfLetters(word)
        .flatMap((v) => Observable.fromIterable(v.entrySet().asScala))
        .map(blank)
        .reduce((a, b) => a + b)
    }

    val checkBlanks = (word: String) => nBlanks(word).map((v: Long) => v <= 2L)

    val score2 = (word: String) => {
        histoOfLetters(word)
        .flatMap((v) => Observable.fromIterable(v.entrySet().asScala))
        .map(letterScore)
        .reduce((a, b) => a + b)
    }

    val first3 = (word: String) => toInteger(word).take(3)

    val last3 = (word: String) => toInteger(word).drop(3)

    val toBeMaxed = (word: String) => Observable.concat(first3(word), last3(word))

    val bonusForDoubleLetter = (word: String) =>
      toBeMaxed(word)
        .map(scoreOfALetter)
        .reduce((a, b) => Math.max(a, b))


    val score3 = (word: String) => {
      if (double) {
        Observable.concat(
          score2(word), score2(word),
          bonusForDoubleLetter(word), bonusForDoubleLetter(word),
          Observable.now(
            if (word.length == 7) {
              50
            } else {
              0
            }
          )
        )
        .reduce((a, b) => a + b)
      } else {
        Observable.concat(
          score2(word).map((v) => v * 2),
          bonusForDoubleLetter(word).map((v) => v * 2),
          Observable.now(
            if (word.length == 7) {
              50
            } else {
              0
            }
          )
        )
        .reduce((a, b) => a + b)
      }
    }

    val buildHistoOnScore = (score: (String) => Observable[Int]) => {
      val map = new util.TreeMap[Int, util.List[String]](new Comparator[Int]() {
        override def compare(o1: Int, o2: Int): Int = Integer.compare(o2, o1)
      })

      val o = Observable.fromIterable(shakespeareWords)
        .filter((word) => scrabbleWords.contains(word))
        .filter((word) => first(checkBlanks(word)))
        .map((word) => {
          val key = first(score(word))
          var list = map.get(key);
          if (list == null) {
            list = new ArrayList[String]();
            map.put(key, list)
          }
          list.add(word);
          map
        })
        .takeLast(1)

      o
    }

    val finalList = new ArrayList[Map.Entry[Int, util.List[String]]]()

    first(
      buildHistoOnScore(score3)
        .flatMap((v) => Observable.fromIterable(v.entrySet().asScala))
        .take(3)
        .map((v) => {
          finalList.add(v)
          finalList
        })
        .takeLast(1)
    )

    finalList
  }

  def first[T](source: Observable[T]) : T = {
    val s = new BlockingFirstSubscriber[T]();
    source.toReactivePublisher(nowScheduler).subscribe(s)
    return s.blockingGet()
  }

  val nowScheduler = new NowScheduler()

  class NowScheduler extends monix.execution.Scheduler {
    override def execute(runnable: Runnable): Unit = runnable.run()

    override def reportFailure(t: Throwable): Unit = t.printStackTrace()

    override def scheduleOnce(initialDelay: Long, unit: TimeUnit, r: Runnable): Cancelable = throw new UnsupportedOperationException()

    override def scheduleWithFixedDelay(initialDelay: Long, delay: Long, unit: TimeUnit, r: Runnable): Cancelable = throw new UnsupportedOperationException()

    override def scheduleAtFixedRate(initialDelay: Long, period: Long, unit: TimeUnit, r: Runnable): Cancelable = throw new UnsupportedOperationException()

    override def currentTimeMillis(): Long = System.currentTimeMillis()

    override def executionModel: ExecutionModel = ExecutionModel.SynchronousExecution

    override def withExecutionModel(em: ExecutionModel): Scheduler = this
  }
}
