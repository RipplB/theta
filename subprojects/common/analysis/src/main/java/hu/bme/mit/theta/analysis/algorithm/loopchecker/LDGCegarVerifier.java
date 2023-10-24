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

import com.google.common.base.Stopwatch;
import hu.bme.mit.theta.analysis.Analysis;
import hu.bme.mit.theta.analysis.LTS;
import hu.bme.mit.theta.analysis.Prec;
import hu.bme.mit.theta.analysis.Trace;
import hu.bme.mit.theta.analysis.algorithm.ARG;
import hu.bme.mit.theta.analysis.algorithm.SafetyChecker;
import hu.bme.mit.theta.analysis.algorithm.SafetyResult;
import hu.bme.mit.theta.analysis.algorithm.cegar.CegarStatistics;
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

import java.util.Collection;
import java.util.concurrent.TimeUnit;

public final class LDGCegarVerifier<S extends ExprState, A extends ExprAction, P extends Prec> implements SafetyChecker<S, A, P> {
	private final LDGAbstractor<S, A, P> abstractor;
	private final LDGTraceRefinerSupplier<S, A, P> refinerFactory;
	private final Logger logger;

	private LDGCegarVerifier(LDGAbstractor<S, A, P> abstractor, LDGTraceRefinerSupplier<S, A, P> refinerFactory, Logger logger) {
		this.abstractor = abstractor;
		this.refinerFactory = refinerFactory;
		this.logger = logger;
	}

	public static <S extends ExprState, A extends ExprAction, P extends Prec> LDGCegarVerifier<S, A, P> of(
			Analysis<S, ? super A, ? super P> analysis,
			LTS<? super S, ? extends A> lts,
			AcceptancePredicate<S, A> target,
			Logger logger,
			ItpSolver solver,
			RefutationToPrec<P, ItpRefutation> refToPrec) {
		return new LDGCegarVerifier<>(LDGAbstractor.create(analysis, lts, target, logger), LDGTraceRefinerSupplier.create(solver, refToPrec, logger), logger);
	}

	public static <S extends ExprState, A extends ExprAction, P extends Prec> LDGCegarVerifier<S, A, P> of(
			Analysis<S, ? super A, ? super P> analysis,
			LTS<? super S, ? extends A> lts,
			AcceptancePredicate<S, A> target,
			Logger logger,
			ItpSolver solver,
			Expr<BoolType> init,
			RefutationToPrec<P, ItpRefutation> refToPrec) {
		return new LDGCegarVerifier<>(LDGAbstractor.create(analysis, lts, target, logger), LDGTraceRefinerSupplier.create(solver, refToPrec, init, logger), logger);
	}

	public SafetyResult<S, A> verify(P initialPrecision, SearchStrategy searchStrategy, RefinerStrategy refinerStrategy) {
		ARG<S, A> mockArg = ARG.create(abstractor.analysis.getPartialOrd());
		int i = 1;
		P currentPrecision = initialPrecision;
		LDGTraceRefiner<S, A, P> refiner = refinerFactory.createRecommended(searchStrategy);
		long abstractorTime = 0;
		long refinerTime = 0;
		final Stopwatch stopwatch = Stopwatch.createStarted();
		while (true) {
			logger.write(Logger.Level.MAINSTEP, "%d. iteration: Abstracting with precision %s%n", i, currentPrecision);
			final long abstractorStartTime = stopwatch.elapsed(TimeUnit.MILLISECONDS);
			Collection<LDGTrace<S, A>> abstractResult = abstractor.onTheFlyCheck(currentPrecision, searchStrategy);
			abstractorTime += stopwatch.elapsed(TimeUnit.MILLISECONDS) - abstractorStartTime;
			if (abstractResult.isEmpty()) {
				logger.write(Logger.Level.RESULT, "Abstractor found no abstract counterexample%n");
				return SafetyResult.safe(mockArg, new CegarStatistics(stopwatch.elapsed(TimeUnit.MILLISECONDS),
						abstractorTime,
						refinerTime, i));
			}
			logger.write(Logger.Level.MAINSTEP, "Abstract counterexample(s) found%n");
			final long refinerStartTime = stopwatch.elapsed(TimeUnit.MILLISECONDS);
			RefinerResult<S, A, P> refinerResult = refiner.check(abstractResult, currentPrecision, refinerStrategy);
			refinerTime += stopwatch.elapsed(TimeUnit.MILLISECONDS) - refinerStartTime;
			if (refinerResult.isUnsafe()) {
				logger.write(Logger.Level.RESULT, "Refiner found counterexample to be feasible%n");
				Trace<S, A> counterexample = refinerResult.asUnsafe().getCex();
				logger.write(Logger.Level.DETAIL, "%s%n", counterexample);
				return SafetyResult.unsafe(counterexample, mockArg, new CegarStatistics(stopwatch.elapsed(TimeUnit.MILLISECONDS),
						abstractorTime,
						refinerTime, i));
			}
			if (refinerResult.asSpurious().getRefinedPrec().equals(currentPrecision))
				throw new RuntimeException("Precision didn't change");
			currentPrecision = refinerResult.asSpurious().getRefinedPrec();

			logger.write(Logger.Level.MAINSTEP, "Counterexample is infeasible, continue with refined precision%n");
			++i;
		}
	}

	public LDG<S, A> getCurrentLdg() {
		return abstractor.getLdg();
	}

	@Override
	public SafetyResult<S, A> check(P prec) {
		return verify(prec, SearchStrategy.defaultValue(), RefinerStrategy.defaultValue());
	}
}
