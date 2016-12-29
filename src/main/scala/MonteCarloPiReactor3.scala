import _root_.reactor.core._
import reactor.core.publisher._
import java.util.function._
import swave.core._
import swave.core.util.XorShiftRandom


object MonteCarloPiReactor3 {

  implicit val env = StreamEnv()

  val random = XorShiftRandom()

  var count : Int = 0;

  def main(args: Array[String]): Unit = {

    val gen = new Consumer[SynchronousSink[Double]]() {
      override def accept(e: SynchronousSink[Double]) : Unit = {
        e.next(random.nextDouble())
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

    val fanoutAndMerge = new Function[Flux[Point], Flux[Sample]]() {
      override def apply(t: Flux[Point]) : Flux[Sample] = {
        return Flux.merge(
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

    val takeLastOne = new Function[Flux[State], Flux[State]]() {
      override def apply(t: Flux[State]) : Flux[State] = {
        return t.takeLast(1)
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

    Flux.generate(gen)
      .buffer(2)
      .map[Point](toPoint)
      .publish(fanoutAndMerge)
      .scan(State(0, 0), stateWithNext)
      .skip(1)
      .window(1000000)
      .flatMap(takeLastOne)
      .map[String](logCurrent)
      .take(50)
      .subscribe(printout)
    ;

    val time = System.currentTimeMillis() - env.startTime
    println(f"Done. Total time: $time%,6d ms, Throughput: ${50000.0 / time}%.3fM samples/sec\n")

  }


}
