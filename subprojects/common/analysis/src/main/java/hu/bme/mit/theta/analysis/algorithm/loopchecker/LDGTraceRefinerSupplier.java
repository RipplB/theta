package hu.bme.mit.theta.analysis.algorithm.loopchecker;

import hu.bme.mit.theta.analysis.Prec;
import hu.bme.mit.theta.analysis.expr.ExprAction;
import hu.bme.mit.theta.analysis.expr.ExprState;
import hu.bme.mit.theta.analysis.expr.refinement.ItpRefutation;
import hu.bme.mit.theta.analysis.expr.refinement.RefutationToPrec;
import hu.bme.mit.theta.common.logging.Logger;
import hu.bme.mit.theta.core.type.Expr;
import hu.bme.mit.theta.core.type.booltype.BoolType;
import hu.bme.mit.theta.solver.ItpSolver;

import static hu.bme.mit.theta.core.type.booltype.BoolExprs.True;

public final class LDGTraceRefinerSupplier<S extends ExprState, A extends ExprAction, P extends Prec> {

	private final ItpSolver solver;
	private final RefutationToPrec<P, ItpRefutation> refToPrec;
	private final Expr<BoolType> init;
	private final Logger logger;

	private LDGTraceRefinerSupplier(ItpSolver solver, RefutationToPrec<P, ItpRefutation> refToPrec, Expr<BoolType> init, Logger logger){
		this.solver = solver;
		this.refToPrec = refToPrec;
		this.init = init;
		this.logger = logger;
	}

	public static <S extends ExprState, A extends ExprAction, P extends Prec> LDGTraceRefinerSupplier<S, A, P>
		create(ItpSolver solver, RefutationToPrec<P, ItpRefutation> refToPrec, Expr<BoolType> init, Logger logger) {
		return new LDGTraceRefinerSupplier<>(solver, refToPrec, init, logger);
	}

	public static <S extends ExprState, A extends ExprAction, P extends Prec> LDGTraceRefinerSupplier<S, A, P>
	create(ItpSolver solver, RefutationToPrec<P, ItpRefutation> refToPrec, Logger logger) {
		return create(solver, refToPrec, True(), logger);
	}

	public LDGTraceRefiner<S, A, P> createDefault() {
		return BasicLDGTraceRefiner.create(solver, init, refToPrec, logger);
	}

	public LDGTraceRefiner<S, A, P> createRecommended(SearchStrategy searchStrategy) {
		if (searchStrategy == SearchStrategy.FULL)
			return MultiTraceLDGTraceRefiner.create(solver, init, refToPrec, logger);
		return BasicLDGTraceRefiner.create(solver, init, refToPrec, logger);
	}

	public static <S extends ExprState, A extends ExprAction, P extends Prec> LDGTraceRefiner<S, A, P> createDefault(ItpSolver solver, Expr<BoolType> init, RefutationToPrec<P, ItpRefutation> refToPrec, Logger logger) {
		return BasicLDGTraceRefiner.create(solver, init, refToPrec, logger);
	}

	public static <S extends ExprState, A extends ExprAction, P extends Prec> LDGTraceRefiner<S, A, P> createRecommended(SearchStrategy searchStrategy, ItpSolver solver, Expr<BoolType> init, RefutationToPrec<P, ItpRefutation> refToPrec, Logger logger) {
		if (searchStrategy == SearchStrategy.FULL)
			return MultiTraceLDGTraceRefiner.create(solver, init, refToPrec, logger);
		return createDefault(solver, init, refToPrec, logger);
	}

}
