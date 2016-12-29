import _root_.io.reactivex.functions._
import io.reactivex._
import swave.core._
import swave.core.util.XorShiftRandom


object MonteCarloPiRx2Observable {

  implicit val env = StreamEnv()

  val random = XorShiftRandom()

  var count : Int = 0;

  def main(args: Array[String]): Unit = {

    val gen = new Consumer[Emitter[Double]]() {
      override def accept(e: Emitter[Double]) : Unit = {
        e.onNext(random.nextDouble())
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

    val fanoutAndMergeO = new Function[Observable[Point], Observable[Sample]]() {
      override def apply(t: Observable[Point]) : Observable[Sample] = {
        return Observable.mergeArray(
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

    val takeLastOneO = new Function[Observable[State], Observable[State]]() {
      override def apply(t: Observable[State]) : Observable[State] = {
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

    Observable.generate(gen)
      .buffer(2)
      .map[Point](toPoint)
      .publish(fanoutAndMergeO)
      .scan(State(0, 0), stateWithNext)
      .skip(1)
      .window(1000000)
      .flatMap(takeLastOneO)
      .map[String](logCurrent)
      .take(50)
      .forEach(printout)
    ;

    val time = System.currentTimeMillis() - env.startTime
    println(f"Done. Total time: $time%,6d ms, Throughput: ${50000.0 / time}%.3fM samples/sec\n")

  }


}
