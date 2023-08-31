/*
 *  Copyright 2023 Budapest University of Technology and Economics
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package hu.bme.mit.theta.analysis.algorithm.loopchecker;

import hu.bme.mit.theta.analysis.Prec;
import hu.bme.mit.theta.analysis.algorithm.cegar.RefinerResult;
import hu.bme.mit.theta.analysis.expr.ExprAction;
import hu.bme.mit.theta.analysis.expr.ExprState;
import hu.bme.mit.theta.analysis.expr.refinement.ExprTraceStatus;
import hu.bme.mit.theta.analysis.expr.refinement.ItpRefutation;
import hu.bme.mit.theta.analysis.expr.refinement.JoiningPrecRefiner;
import hu.bme.mit.theta.analysis.expr.refinement.RefutationToPrec;
import hu.bme.mit.theta.common.logging.Logger;
import hu.bme.mit.theta.common.logging.NullLogger;
import hu.bme.mit.theta.core.type.Expr;
import hu.bme.mit.theta.core.type.booltype.BoolType;
import hu.bme.mit.theta.solver.ItpSolver;

import static hu.bme.mit.theta.core.type.booltype.BoolExprs.True;

public final class LDGTraceRefiner<S extends ExprState, A extends ExprAction, P extends Prec> {
	private final ItpSolver solver;
	private final Expr<BoolType> init;
	private final JoiningPrecRefiner<S, A, P, ItpRefutation> refiner;
	private final Logger logger;

	private LDGTraceRefiner(ItpSolver solver, Expr<BoolType> init, RefutationToPrec<P, ItpRefutation> refToPrec, Logger logger) {
		this.solver = solver;
		this.init = init;
		this.logger = logger != null ? logger : NullLogger.getInstance();
		refiner = JoiningPrecRefiner.create(refToPrec);
	}

	public static <S extends ExprState, A extends ExprAction, P extends Prec> LDGTraceRefiner<S, A, P> create(ItpSolver solver, Expr<BoolType> init, RefutationToPrec<P, ItpRefutation> refToPrec, Logger logger) {
		return new LDGTraceRefiner<>(solver, init, refToPrec, logger);
	}

	public static <S extends ExprState, A extends ExprAction, P extends Prec> LDGTraceRefiner<S, A, P> create(ItpSolver solver, RefutationToPrec<P, ItpRefutation> refToPrec, Logger logger) {
		return create(solver, True(), refToPrec, logger);
	}

	public RefinerResult<S, A, P> check(final LDGTrace<S, A> ldgTrace, final P currentPrecision, RefinerStrategy refinerStrategy) {
		ExprTraceStatus<ItpRefutation> refutation = LDGTraceChecker.check(ldgTrace, solver, init, refinerStrategy, logger);
		if (refutation.isInfeasible()) {
			P refinedPrecision = refiner.refine(currentPrecision, ldgTrace.toTrace(), refutation.asInfeasible().getRefutation());
			return RefinerResult.spurious(refinedPrecision);
		}
		return RefinerResult.unsafe(ldgTrace.toTrace());
	}

}
