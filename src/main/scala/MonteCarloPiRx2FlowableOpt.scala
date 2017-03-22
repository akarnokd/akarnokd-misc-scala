import java.util.concurrent.Callable

import io.reactivex._
import hu.akarnokd.rxjava2.operators._
import swave.core.util.XorShiftRandom
import swave.core._
import _root_.io.reactivex.functions._


object MonteCarloPiRx2FlowableOpt {

  implicit val env = StreamEnv()

  val random = XorShiftRandom()

  var count : Int = 0;

  def main(args: Array[String]): Unit = {

    val gen = new Callable[Double]() {
      override def call() : Double = {
        random.nextDouble()
      }
    }

    val toPoint = new Function[java.util.List[Double], Point]() {
      override def apply(t: java.util.List[Double]) : Point = {
        return new Point(t.get(0), t.get(1))
      }
    }

    val isInner = new Predicate[Point]() {
      override def test(t: Point) : Boolean = t.isInner;
    }

    val toInnerSample = new Function[Point, Sample]() {
      override def apply(t: Point) : Sample = InnerSample;
    }

    val toOuterSample = new Function[Point, Sample]() {
      override def apply(t: Point) : Sample = OuterSample;
    }

    val isNotInner = new Predicate[Point]() {
      override def test(t: Point) : Boolean = !t.isInner;
    }

    val fanoutAndMerge = new Function[Flowable[Point], Flowable[Sample]]() {
      override def apply(t: Flowable[Point]) : Flowable[Sample] = {
        return Flowable.mergeArray(
          t.filter(isInner).map[Sample](toInnerSample),
          t.filter(isNotInner).map[Sample](toOuterSample)
        )
      }
    }

    val stateWithNext = new BiFunction[State, Sample, State]() {
      override def apply(t: State, u: Sample) : State = {
        return t.withNextSample(u)
      }
    }

    val logCurrent = new Function[State, String]() {
      override def apply(state: State) : String = {
        return f"After ${state.totalSamples}%,10d samples π is approximated as ${state.π}%.6f"
      }
    }

    val printout = new Consumer[String]() {
      override def accept(s: String) : Unit = {
        println(s);
      }
    }

    Flowables.repeatCallable(gen)
      .buffer(2)
      .map[Point](toPoint)
      .publish(fanoutAndMerge)
      .scan(State(0, 0), stateWithNext)
      .skip(1)
      .compose[State](FlowableTransformers.every(1000000))
      .map[String](logCurrent)
      .take(50)
      .forEach(printout)
    ;

    val time = System.currentTimeMillis() - env.startTime
    println(f"Done. Total time: $time%,6d ms, Throughput: ${50000.0 / time}%.3fM samples/sec\n")

  }


}
