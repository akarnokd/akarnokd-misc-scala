import scala.util.{Failure, Success}
import akka.NotUsed
import akka.actor.ActorSystem
import akka.stream.scaladsl._
import akka.stream.{ActorMaterializer, ActorMaterializerSettings, FlowShape}

import swave.core._
import swave.core.util.XorShiftRandom



object MonteCarloPiAkka {

  implicit val env = StreamEnv()

  val random = XorShiftRandom()

  var count : Int = 0;

  def main(args: Array[String]): Unit = {

    implicit val system = ActorSystem("AkkaPi")

    val settings : ActorMaterializerSettings = ActorMaterializerSettings(system)
      .withSyncProcessingLimit(Int.MaxValue)
      .withInputBuffer(256, 256)

    import system.dispatcher

    implicit val materializer = ActorMaterializer(settings)

    val random = XorShiftRandom()

    Source
      .fromIterator(() ⇒ Iterator.continually(random.nextDouble()))
      .grouped(2)
      .map { case x +: y +: Nil ⇒ Point(x, y) }
      .via(broadcastFilterMerge)
      //.async
      .scan(State(0, 0)) { _ withNextSample _ }
      .splitWhen(_.totalSamples % 1000000 == 1)
      .drop(999999)
      .concatSubstreams
      .map(state ⇒ f"After ${state.totalSamples}%,10d samples π is approximated as ${state.π}%.6f")
      .take(30)
      .runForeach(println)
      .onComplete {
        case Success(_) =>
          val time = System.currentTimeMillis() - system.startTime
          println(f"Done. Total time: $time%,6d ms, Throughput: ${30000.0 / time}%.3fM samples/sec\n")
          system.terminate()

        case Failure(e) => println(e)
      }

  }

  def broadcastFilterMerge: Flow[Point, Sample, NotUsed] =
    Flow.fromGraph(GraphDSL.create() { implicit b =>
      import GraphDSL.Implicits._

      val broadcast   = b.add(Broadcast[Point](2)) // split one upstream into 2 downstreams
    val filterInner = b.add(Flow[Point].filter(_.isInner).map(_ => InnerSample))
      val filterOuter = b.add(Flow[Point].filterNot(_.isInner).map(_ => OuterSample))
      val merge       = b.add(Merge[Sample](2)) // merge 2 upstreams into one downstream

      broadcast.out(0) ~> filterInner ~> merge.in(0)
      broadcast.out(1) ~> filterOuter ~> merge.in(1)

      FlowShape(broadcast.in, merge.out)
    })

}
