package hu.bme.mit.theta.analysis.algorithm.loopchecker;

import hu.bme.mit.theta.analysis.Prec;
import hu.bme.mit.theta.analysis.algorithm.cegar.Refiner;
import hu.bme.mit.theta.analysis.algorithm.cegar.RefinerResult;
import hu.bme.mit.theta.analysis.algorithm.loopchecker.ldg.LDG;
import hu.bme.mit.theta.analysis.expr.ExprAction;
import hu.bme.mit.theta.analysis.expr.ExprState;

import java.util.Collection;

public interface LDGTraceRefiner<S extends ExprState, A extends ExprAction, P extends Prec> extends Refiner<S, A, P, LDG<S, A>, LDGTrace<S, A>> {
	RefinerResult<S, A, P, LDGTrace<S, A>> check(final Collection<LDGTrace<S, A>> ldgTraces, final P currentPrecision);
}
