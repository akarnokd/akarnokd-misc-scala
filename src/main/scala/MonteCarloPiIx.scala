import _root_.ix._
import swave.core._
import swave.core.util.XorShiftRandom


object MonteCarloPiIx {

  implicit val env = StreamEnv()

  val random = XorShiftRandom()

  var count : Int = 0;

  def main(args: Array[String]): Unit = {

    val gen = new IxConsumer[IxEmitter[Double]]() {
      override def accept(e: IxEmitter[Double]) : Unit = {
        e.onNext(random.nextDouble())
      }
    }

    val toPoint = new IxFunction[java.util.List[Double], Point]() {
      override def apply(t: java.util.List[Double]) : Point = {
        return new Point(t.get(0), t.get(1))
      }
    }

    /*
    val isInner = new IxPredicate[Point]() {
      override def test(t: Point) : Boolean = t.isInner;
    }

    val toInnerSample = new IxFunction[Point, Sample]() {
      override def apply(t: Point) : Sample = InnerSample;
    }

    val toOuterSample = new IxFunction[Point, Sample]() {
      override def apply(t: Point) : Sample = OuterSample;
    }

    val isNotInner = new IxPredicate[Point]() {
      override def test(t: Point) : Boolean = !t.isInner;
    }
    */

    val mapSample = new IxFunction[Point, Sample]() {
      override def apply(t: Point) : Sample = {
        if (t.isInner) {
          return InnerSample;
        }
        return OuterSample;
      }
    }

    val fanoutAndMerge = new IxFunction[Ix[Point], Ix[Sample]]() {
      override def apply(t: Ix[Point]) : Ix[Sample] = {
        return t.map(mapSample)
        /* this doesn't work in pull-streams anymore:

        return Ix.concatArray(
          t.filter(isInner).map[Sample](toInnerSample),
          t.filter(isNotInner).map[Sample](toOuterSample)
        );
         */
      }
    }

    val stateWithNext = new IxFunction2[State, Sample, State]() {
      override def apply(t: State, u: Sample) : State = {
        return t.withNextSample(u)
      }
    }

    val takeLastOne = new IxFunction[Ix[State], Ix[State]]() {
      override def apply(t: Ix[State]) : Ix[State] = {
        return t.takeLast(1)
      }
    }

    val logCurrent = new IxFunction[State, String]() {
      override def apply(state: State) : String = {
        return f"After ${state.totalSamples}%,10d samples π is approximated as ${state.π}%.6f"
      }
    }

    val printout = new IxConsumer[String]() {
      override def accept(s: String) : Unit = {
        println(s);
      }
    }

    val initialState = new IxSupplier[State]() {
      override def get() : State = State(0, 0)
    }

    Ix.generate(gen)
      .buffer(2)
      .map[Point](toPoint)
      .publish(fanoutAndMerge)
      .scan(initialState, stateWithNext)
      .skip(1)
      .window(1000000)
      .flatMap(takeLastOne)
      .map[String](logCurrent)
      .take(50)
      .foreach(printout)
    ;

    val time = System.currentTimeMillis() - env.startTime
    println(f"Done. Total time: $time%,6d ms, Throughput: ${50000.0 / time}%.3fM samples/sec\n")

  }


}
