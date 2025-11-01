//TIP To <b>Run</b> code, press <shortcut actionId="Run"/> or
// click the <icon src="AllIcons.Actions.Execute"/> icon in the gutter.
public class Main {
    public sealed interface Term {
        default @NoInherit Term inst() {
            return null;
        }
    }

    public enum Unit implements Term { INSTANCE }

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

    @Bound
    public record AnnotatedTerm(Term term) implements Term {}

    public record NormalTerm(Term sub0) implements Term {}

    public static void acceptClosedTerm(@Closed Term term) {}
    public static void acceptClosedTerms(@Closed Term... terms) {}

    public static void acceptBoundTerm(@Bound Term term) {}

    public static void acceptClosedInt(@Closed int i) {}

    public void testSubterm(@Bound SubTerm sub) {
        if (sub instanceof SubTerm(var ifInstanceofInherit, _, _)) {
            acceptClosedTerm(ifInstanceofInherit);

            acceptBoundTerm(ifInstanceofInherit);
        }

        switch (sub) {
            case SubTerm(@Bound var switchInherit, _, _) -> {
                acceptClosedTerm(switchInherit);
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

        // AnnotatedTerm is annotated as Bound, thus any expression with type AnnotatedTerm is considered Bound
        @Closed Term closed = new AnnotatedTerm(null);
        AnnotatedTerm ss = new AnnotatedTerm(null);
        acceptClosedTerm(ss);

        // `null` is not Inherit, Bound or Closed
        acceptClosedTerm(null);
        acceptBoundTerm(null);

        // vararg case
        acceptClosedTerms(sub, null, Unit.INSTANCE, closed);

        // `inst` is marked with `NoInherit`, which cause the inspection on such method call is disabled
        // in this case, the user should check the dblity
        closed = sub.inst();
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

    public void switchCase(@Closed Term term) {
        switch (term) {
            case AnnotatedTerm annotatedTerm -> {
                // class annotation has higher priority
                // TODO: in fact, we should report a warn in this case
                acceptClosedTerm(annotatedTerm);
            }
            case NormalTerm normalTerm -> {
                // normalTerm is considered Closed
                acceptClosedTerm(normalTerm);
            }
            case SubTerm(var inherit, _, _) -> {
                // inherit is considered Closed
                acceptClosedTerm(inherit);
            }
            case Unit unit -> {}
        }
    }
}