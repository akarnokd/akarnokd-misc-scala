import java.io.File
import java.util
import java.util.concurrent.{CountDownLatch, TimeUnit}
import java.util.concurrent.atomic.AtomicReference
import java.util.{ArrayList, Comparator, HashMap, Map}

import io.reactors._
import io.reactors.Events._
import io.reactors.protocol._

import scala.collection.mutable
import scala.io.Source
import scala.collection.JavaConverters._

object ScrabbleWithReactorsIO {

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
      // (0 until string.length).toEvents
      chars(string)
    }

    val histoOfLetters = (word: String) => {
      toInteger(word)
          .reducePast(new HashMap[Int, Long]())((map, value) => {
            val current = map.get(value.toInt);
            if (current == null) {
              map.put(value, 1L)
            } else {
              map.put(value, current + 1L)
            }
            map
          })
    }

    val blank = (entry: Map.Entry[Int, Long]) => {
      Math.max(0L, entry.getValue() - scrabbleAvailableLetters(entry.getKey() - 'a'))
    }

    val nBlanks = (word: String) => {
      flatMap[HashMap[Int, Long], Map.Entry[Int, Long]](histoOfLetters(word), (v) => v.entrySet())
        .map(blank)
        .reducePast[Long](0)((a, b) => a + b)
    }

    val checkBlanks = (word: String) => nBlanks(word).map((v: Long) => v <= 2L)

    val score2 = (word: String) => {
      flatMap[HashMap[Int, Long], Map.Entry[Int, Long]](histoOfLetters(word), (v) => v.entrySet())
        .map(letterScore)
        .reducePast[Int](0)((a, b) => a + b)
    }

    val first3 = (word: String) => toInteger(word).take(3)

    val last3 = (word: String) => toInteger(word).drop(3)

    val toBeMaxed = (word: String) => first3(word).union(last3(word))

    val bonusForDoubleLetter = (word: String) =>
      toBeMaxed(word)
        .map(scoreOfALetter)
        .reducePast[Int](0)((a, b) => Math.max(a, b))


    val score3 = (word: String) => {
      val e1 : Events[Int] = score2(word)
      val e2 : Events[Int] = bonusForDoubleLetter(word)

      (if (double) {
          e1.union(e1).union(e2).union(e2)
      } else {
        e1.union(e2).map((v) => v * 2)
      }).reducePast[Int](0)((a, b) => a + b)
        .map((v) => v + (if (word.length == 7) {
          50
        } else {
          0
        }))

    }

    val buildHistoOnScore = (score: (String) => Events[Int]) => {
      val map = new util.TreeMap[Int, util.List[String]](new Comparator[Int]() {
        override def compare(o1: Int, o2: Int): Int = Integer.compare(o2, o1)
      })

      val o = fromIterable(shakespeareWords)
        .filter((word) => scrabbleWords.contains(word))
        .filter((word) => first(checkBlanks(word)))
          .reducePast(map)((map, word) => {
          val key = first(score(word))
          var list = map.get(key);
          if (list == null) {
            list = new ArrayList[String]();
            map.put(key, list)
          }
          list.add(word);
          map
        })

      o
    }

    val finalList = new ArrayList[Map.Entry[Int, util.List[String]]]()

    first(
      flatMap[util.TreeMap[Int, util.List[String]], util.Map.Entry[Int, util.List[String]]](buildHistoOnScore(score3), (v) => v.entrySet())
        .take(3)
        .reducePast(finalList)((f, v) => {
          f.add(v)
          f
        })
    )

    finalList
  }

  def chars(string: String) : Events[Char] = {
    (0 until string.length).toEvents.map((i) => string.charAt(i))
  }

  def flatMap[T, R](source: Events[T], mapper: (T) => java.lang.Iterable[R]): Events[R] = {
    source.map((v) => mapper(v).asScala.toEvents).union
  }

  def fromIterable[T](source: Iterable[T]) : Events[T] = {
    source.toEvents
  }

  def first[T](source: Events[T]) : T = {
    val cdl = new CountDownLatch(1)
    val ref = new AtomicReference[T];

    source.onEvent((t) => {
      if (ref.get() == null) {
        ref.lazySet(t)
        cdl.countDown()
      }
    })

    cdl.await()

    return ref.get()
  }
}
