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

import hu.bme.mit.theta.analysis.Analysis;
import hu.bme.mit.theta.analysis.LTS;
import hu.bme.mit.theta.analysis.Prec;
import hu.bme.mit.theta.analysis.Trace;
import hu.bme.mit.theta.analysis.algorithm.cegar.RefinerResult;
import hu.bme.mit.theta.analysis.algorithm.loopchecker.ldg.LDG;
import hu.bme.mit.theta.analysis.expr.ExprAction;
import hu.bme.mit.theta.analysis.expr.ExprState;
import hu.bme.mit.theta.analysis.expr.refinement.ItpRefutation;
import hu.bme.mit.theta.analysis.expr.refinement.RefutationToPrec;
import hu.bme.mit.theta.common.logging.Logger;
import hu.bme.mit.theta.core.type.Expr;
import hu.bme.mit.theta.core.type.booltype.BoolType;
import hu.bme.mit.theta.solver.ItpSolver;

import java.util.Optional;

public final class LDGCegarVerifier<S extends ExprState, A extends ExprAction, P extends Prec> {
	private final LDGAbstractor<S, A, P> abstractor;
	private final LDGTraceRefiner<S, A, P> refiner;
	private final Logger logger;

	private LDGCegarVerifier(LDGAbstractor<S, A, P> abstractor, LDGTraceRefiner<S, A, P> refiner, Logger logger) {
		this.abstractor = abstractor;
		this.refiner = refiner;
		this.logger = logger;
	}

	public static <S extends ExprState, A extends ExprAction, P extends Prec> LDGCegarVerifier<S, A, P> of(
			Analysis<S, ? super A, ? super P> analysis,
			LTS<? super S, ? extends A> lts,
			AcceptancePredicate<S, A> target,
			Logger logger,
			ItpSolver solver,
			RefutationToPrec<P, ItpRefutation> refToPrec) {
		return new LDGCegarVerifier<>(LDGAbstractor.create(analysis, lts, target, logger), LDGTraceRefiner.create(solver, refToPrec, logger), logger);
	}

	public static <S extends ExprState, A extends ExprAction, P extends Prec> LDGCegarVerifier<S, A, P> of(
			Analysis<S, ? super A, ? super P> analysis,
			LTS<? super S, ? extends A> lts,
			AcceptancePredicate<S, A> target,
			Logger logger,
			ItpSolver solver,
			Expr<BoolType> init,
			RefutationToPrec<P, ItpRefutation> refToPrec) {
		return new LDGCegarVerifier<>(LDGAbstractor.create(analysis, lts, target, logger), LDGTraceRefiner.create(solver, init, refToPrec, logger), logger);
	}

	public Optional<Trace<S, A>> verify(P initialPrecision, SearchStrategy searchStrategy, RefinerStrategy refinerStrategy) {
		int i = 1;
		P currentPrecision = initialPrecision;
		while (true) {
			logger.write(Logger.Level.MAINSTEP, "%d. iteration: Abstracting with precision %s%n", i++, currentPrecision);
			Optional<LDGTrace<S, A>> abstractResult = abstractor.onTheFlyCheck(currentPrecision, searchStrategy);
			if (abstractResult.isEmpty()) {
				logger.write(Logger.Level.RESULT, "Abstractor found no abstract counterexample%n");
				return Optional.empty();
			}
			LDGTrace<S, A> abstractTrace = abstractResult.get();
			logger.write(Logger.Level.MAINSTEP, "Abstract counterexample found%n");
			abstractTrace.print(logger, Logger.Level.DETAIL);
			RefinerResult<S, A, P> refinerResult = refiner.check(abstractTrace, currentPrecision, refinerStrategy);
			if (refinerResult.isUnsafe()) {
				logger.write(Logger.Level.RESULT, "Refiner found counterexample to be feasible%n");
				Trace<S, A> counterexample = refinerResult.asUnsafe().getCex();
				logger.write(Logger.Level.DETAIL, "%s%n", counterexample);
				return Optional.of(counterexample);
			}
			logger.write(Logger.Level.MAINSTEP, "Counterexample is infeasible, continue with refined precision%n");
			currentPrecision = refinerResult.asSpurious().getRefinedPrec();
		}
	}

	public LDG<S, A> getCurrentLdg() {
		return abstractor.getLdg();
	}

}
