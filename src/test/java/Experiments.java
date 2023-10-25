import kala.collection.Seq;
import kala.collection.SeqView;
import kala.collection.immutable.ImmutableMap;
import kala.collection.mutable.MutableList;
import kala.tuple.Tuple;

public interface Experiments {
  static void main(String... args) {
    Seq.of(1).toImmutableMap();
    MutableList.of(1, 2).toImmutableMap();
    SeqView.of(1).toImmutableMap();
    ImmutableMap.from(SeqView.of(Tuple.of(1, 2)));
    ImmutableMap.from(Seq.of(Tuple.of(1, 2)));
  }
}
