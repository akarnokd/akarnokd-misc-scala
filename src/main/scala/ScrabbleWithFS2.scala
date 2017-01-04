import java.io.File
import java.util
import java.util.concurrent.TimeUnit
import java.util.{ArrayList, Comparator, HashMap, Map}

import fs2._

import scala.collection.JavaConverters._
import scala.collection.mutable
import scala.io.Source

object ScrabbleWithFS2 {

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
      Stream.range(0, string.length).map((v: Int) => string.charAt(v.toInt))
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
      }).takeRight(1)
    }

    val blank = (entry: Map.Entry[Int, Long]) => {
      Math.max(0L, entry.getValue() - scrabbleAvailableLetters(entry.getKey() - 'a'))
    }

    val nBlanks = (word: String) => {
      histoOfLetters(word)
        .flatMap((v) => Stream.emits(v.entrySet().asScala.toSeq))
        .map(blank)
        .reduce((a, b) => a + b)
    }

    val checkBlanks = (word: String) => nBlanks(word).map((v: Long) => v <= 2L)

    val score2 = (word: String) => {
        histoOfLetters(word)
        .flatMap((v) => Stream.emits(v.entrySet().asScala.toSeq))
        .map(letterScore)
        .reduce((a, b) => a + b)
    }

    val first3 = (word: String) => toInteger(word).take(3)

    val last3 = (word: String) => toInteger(word).drop(3)

    val toBeMaxed = (word: String) => first3(word).append(last3(word))

    val bonusForDoubleLetter = (word: String) =>
      toBeMaxed(word)
        .map(scoreOfALetter)
        .reduce((a, b) => Math.max(a, b))


    val score3 = (word: String) => {
      if (double) {
          score2(word).append(score2(word))
          .append(bonusForDoubleLetter(word)).append(bonusForDoubleLetter(word))
          .append(Stream.emit(if (word.length == 7) {
                50
              } else {
                0
              }))
          .reduce((a, b) => a + b)
      } else {
        score2(word).map((v) => v * 2)
          .append(bonusForDoubleLetter(word).map((v) => v * 2))
          .append(Stream.emit(if (word.length == 7) {
            50
          } else {
            0
          }))
          .reduce((a, b) => a + b)
      }
    }

    val buildHistoOnScore = (score: (String) => Stream[Nothing, Int]) => {
      val map = new util.TreeMap[Int, util.List[String]](new Comparator[Int]() {
        override def compare(o1: Int, o2: Int): Int = Integer.compare(o2, o1)
      })

      val o = Stream.emits(shakespeareWords.toSeq)
              .filter((word) => scrabbleWords.contains(word))
              // filter((word) => first(checkBlanks(word)))
              .flatMap(word => checkBlanks(word).filter((b) => b).map(b => word))
              .map((word) => {
                val key = first(score(word))
                var list = map.get(key);
                if (list == null) {
                  list = new ArrayList[String]();
                  map.put(key, list)
                }
                list.add(word);
                map
              }).takeRight(1)
      o
    }

    val finalList = new ArrayList[Map.Entry[Int, util.List[String]]]()

    first(
      buildHistoOnScore(score3)
        .flatMap((v) => Stream.emits(v.entrySet().asScala.toSeq))
        .take(3)
        .map((v) => {
          finalList.add(v)
          finalList
        }).takeRight(1)
    )

    finalList
  }

  def first[T](source: Stream[Nothing, T]) : T = {
    source.toList{0}
  }
}
