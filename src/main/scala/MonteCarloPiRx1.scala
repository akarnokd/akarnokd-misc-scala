import _root_.rx.functions._
import rx._
import rx.observables.SyncOnSubscribe
import swave.core._
import swave.core.util.XorShiftRandom


object MonteCarloPiRx1 {

  implicit val env = StreamEnv()

  val random = XorShiftRandom()

  var count : Int = 0;

  def main(args: Array[String]): Unit = {

    /*val gen = new Func1[Integer, Double]() {
      override def call(e: Integer) : Double = {
        return random.nextDouble()
      }
    }*/

    val gen = new SyncOnSubscribe[Void, Double]() {
      override def next(state: Void, observer: Observer[_ >: Double]): Void = {
        observer.onNext(random.nextDouble())
        return null;
      }

      override def generateState(): Void = null
    }

    val toPoint = new Func1[java.util.List[Double], Point]() {
      override def call(t: java.util.List[Double]) : Point = {
        return new Point(t.get(0), t.get(1))
      }
    }

    val isInner = new Func1[Point, java.lang.Boolean]() {
      override def call(t: Point) : java.lang.Boolean = t.isInner;
    }

    val toInnerSample = new Func1[Point, Sample]() {
      override def call(t: Point) : Sample = InnerSample;
    }

    val toOuterSample = new Func1[Point, Sample]() {
      override def call(t: Point) : Sample = OuterSample;
    }

    val isNotInner = new Func1[Point, java.lang.Boolean]() {
      override def call(t: Point) : java.lang.Boolean = !t.isInner;
    }

    val fanoutAndMergeO = new Func1[Observable[Point], Observable[Sample]]() {
      override def call(t: Observable[Point]) : Observable[Sample] = {
        return Observable.merge(
          t.filter(isInner).map[Sample](toInnerSample),
          t.filter(isNotInner).map[Sample](toOuterSample)
        )
      }
    }

    val stateWithNext = new Func2[State, Sample, State]() {
      override def call(t: State, u: Sample) : State = {
        return t.withNextSample(u)
      }
    }

    val takeLastOneO = new Func1[Observable[State], Observable[State]]() {
      override def call(t: Observable[State]) : Observable[State] = {
        return t.takeLast(1)
      }
    }

    val logCurrent = new Func1[State, String]() {
      override def call(state: State) : String = {
        return f"After ${state.totalSamples}%,10d samples π is approximated as ${state.π}%.6f"
      }
    }

    val printout = new Action1[String]() {
      override def call(s: String) : Unit = {
        println(s);
      }
    }

    ;

    rx.Observable.create(gen)
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
