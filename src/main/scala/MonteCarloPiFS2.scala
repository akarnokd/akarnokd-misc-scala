import java.util.concurrent.TimeUnit

import fs2._

import swave.core.StreamEnv
import swave.core.util.XorShiftRandom

object MonteCarloPiFS2 {

  implicit val env = StreamEnv()

  val random = XorShiftRandom()

  def main(args: Array[String]): Unit = {

    val n = 1; // currently this is extremely slow so 1 million is enough for now

    Stream.range(0, Int.MaxValue)
      .map(_ => (random.nextDouble(), random.nextDouble()))
      // can't find a buffer(2) operator, sliding(N) is Rx.buffer(N, 1)
      .map((v) => new Point(v._1, v._2))
      // I can't find any fan-out operator
      .map((p) => if (p.isInner) InnerSample else OuterSample)
      // lets say we are now fan-in
      .scan(State(0, 0))((t, u) => t.withNextSample(u))
      .drop(1)
      .filter((s) => s.totalSamples % 1000000 == 0)
      .map((state) => f"After ${state.totalSamples}%,10d samples π is approximated as ${state.π}%.6f")
      .take(n)
      .map((s) => println(s))
      .toList
    ;

    val time = System.currentTimeMillis() - env.startTime
    println(f"Done. Total time: $time%,6d ms, Throughput: ${n * 1000.0 / time}%.3fM samples/sec\n")

  }

}
