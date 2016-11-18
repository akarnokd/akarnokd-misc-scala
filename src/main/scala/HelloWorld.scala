import swave.core.Spout

/**
  * Created by akarnokd on 2016.11.18..
  */
object HelloWorld {
  def main(args: Array[String]): Unit = {
    System.out.println("Hello world!")

    val s = Spout.one(1);

    System.out.println(ScrabbleWithSwave.publisher(s).blockingFirst())
    System.out.println(ScrabbleWithSwave.publisher(s).blockingFirst())
  }
}
