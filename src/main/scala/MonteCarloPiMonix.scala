import java.util.concurrent.TimeUnit

import ScrabbleWithMonix.NowScheduler
import monix.execution.Scheduler
import monix.reactive.Observable
import monix.reactive.observables.ConnectableObservable
import swave.core.StreamEnv
import swave.core.util.XorShiftRandom

import scala.concurrent.Await
import scala.concurrent.duration.Duration


object MonteCarloPiMonix {

  implicit val env = StreamEnv()

  val random = XorShiftRandom()

  def main(args: Array[String]): Unit = {

    val nowScheduler = new NowScheduler()

    val fanout : ConnectableObservable[Point] = Observable.range(0, Integer.MAX_VALUE)
      .map(_ => random.nextDouble())
      .bufferSliding(2, 2)
      .map[Point]((f) => {
        val arr = f.toArray
        new Point(arr{0}, arr{1})
      })
      .publish(nowScheduler)

      val fanIn = Observable.merge[Sample](
        fanout.filter((p) => p.isInner).map((p) => InnerSample),
        fanout.filter((p) => !p.isInner).map((p) => OuterSample)
      )
      .scan[State](State(0, 0)) { (t: State, u: Sample) => t.withNextSample(u) }
      .drop(1)
      .bufferSliding(1000000, 1000000)
      .map((b) => b.last)
      .map((state) => f"After ${state.totalSamples}%,10d samples π is approximated as ${state.π}%.6f")
      .take(50)
      .foreach(println)(Scheduler.Implicits.global)


      fanout.connect()

      Await.result(fanIn, Duration.apply(2, TimeUnit.MINUTES))

    ;

    val time = System.currentTimeMillis() - env.startTime
    println(f"Done. Total time: $time%,6d ms, Throughput: ${50000.0 / time}%.3fM samples/sec\n")

  }

}
