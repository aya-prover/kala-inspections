//TIP To <b>Run</b> code, press <shortcut actionId="Run"/> or
// click the <icon src="AllIcons.Actions.Execute"/> icon in the gutter.
public class Main {
    public interface Term {}

    public enum Unit implements Term { INSTANCE }

    @Bound
    public record SubTerm(Term inheritSubTerm, @Closed Term closedSubTerm, int integer) implements Term {
        public void doSomething() {
            // warning
            acceptClosedTerm(inheritSubTerm);
            // ok
            acceptBoundTerm(inheritSubTerm);
            // ok
            acceptClosedTerm(closedSubTerm);
        }
    }

    public static void acceptClosedTerm(@Closed Term term) {}

    public static void acceptBoundTerm(@Bound Term term) {}

    public static void acceptClosedInt(@Closed int i) {}

    public void testSubterm(@Bound SubTerm sub) {
        if (sub instanceof SubTerm(var inheritSubTerm, _, _)) {
            acceptClosedTerm(inheritSubTerm);

            acceptBoundTerm(inheritSubTerm);
        }

        switch (sub) {
            case SubTerm(@Bound var inheritSubTerm, _, _) -> {
                acceptClosedTerm(inheritSubTerm);
            }
        }

        // sub.inheritSubTerm inherits the db-closeness from the receiver,
        // thus it is Bound
        acceptClosedTerm(sub.inheritSubTerm());

        // ok
        acceptClosedTerm(sub.closedSubTerm());

        // ok, closed term can be used as bound term
        acceptBoundTerm(sub.closedSubTerm());

        // this work cause `i` is inherit, NOT cause `i` is inferred to `Bound`
        var i = sub.inheritSubTerm();
        acceptClosedTerm(i);
        // ok, `Inherit` is treated as `Bound` at rhs
        acceptBoundTerm(i);

        // ok, even `i` is `Inherit`, this is the only way to cast an `Inherit` to `Closed`
        @Closed Term j = i;

        // the return type of any method will inherit the db-closeness from the receiver, even types don't match
        @Closed int ii = sub.integer();

        // SubTerm is annotated as Bound, thus the new expression is considered Bound
        @Closed Term s = new SubTerm(null, null, 0);
    }

    public void inconsistent() {
        @Closed int i = 0;
        var j = i;
        // `j` is considered a `@Closed int`
        acceptClosedInt(j);

        @Closed Term k = Unit.INSTANCE;
        var l = k;
        // `l` is considered a `Term`, `@Closed` is lost.
        acceptClosedTerm(l);
    }
}