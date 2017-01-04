import java.util.concurrent.TimeUnit

import monix.execution.Scheduler
import monix.execution.ExecutionModel.SynchronousExecution
import monix.reactive.Observable
import monix.reactive.OverflowStrategy.BackPressure
import swave.core.StreamEnv
import swave.core.util.XorShiftRandom
import scala.concurrent.Await
import scala.concurrent.duration.Duration

object MonteCarloPiMonix {
  implicit val env = StreamEnv()
  val random = XorShiftRandom()

  def main(args: Array[String]): Unit = {
    implicit val scheduler = Scheduler.global
    implicit val strategy = BackPressure(256)

    val subscription = Observable
      .repeat(())
      .map(_ => random.nextDouble())
      .bufferTumbling(2)
      .map { seq => Point(seq(0),seq(1)) }
      .publishSelector(source =>
        Observable.merge[Sample](
          source.filter((p) => p.isInner).map(_ => InnerSample),
          source.filter((p) => !p.isInner).map(_ => OuterSample)
        ))
      .scan[State](State(0, 0)) { (t: State, u: Sample) => t.withNextSample(u) }
      .drop(1)
      .takeEveryNth(1000000)
      .map((state) => f"After ${state.totalSamples}%,10d samples π is approximated as ${state.π}%.6f")
      .take(50)
      .foreach(println)

    Await.result(subscription, Duration.apply(2, TimeUnit.MINUTES))

    val time = System.currentTimeMillis() - env.startTime
    println(f"Done. Total time: $time%,6d ms, Throughput: ${50000.0 / time}%.3fM samples/sec\n")
  }
}
