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

import com.google.common.collect.ImmutableList;
import hu.bme.mit.theta.analysis.Trace;
import hu.bme.mit.theta.analysis.algorithm.loopchecker.ldg.LDGEdge;
import hu.bme.mit.theta.analysis.expl.ExplPrec;
import hu.bme.mit.theta.analysis.expl.ExplState;
import hu.bme.mit.theta.analysis.expr.ExprAction;
import hu.bme.mit.theta.analysis.expr.ExprState;
import hu.bme.mit.theta.analysis.expr.ExprStates;
import hu.bme.mit.theta.analysis.expr.StmtAction;
import hu.bme.mit.theta.analysis.expr.refinement.ExprTraceStatus;
import hu.bme.mit.theta.analysis.expr.refinement.ItpRefutation;
import hu.bme.mit.theta.analysis.multi.ExprMultiAction;
import hu.bme.mit.theta.common.container.Containers;
import hu.bme.mit.theta.common.logging.Logger;
import hu.bme.mit.theta.common.logging.NullLogger;
import hu.bme.mit.theta.core.decl.VarDecl;
import hu.bme.mit.theta.core.model.Valuation;
import hu.bme.mit.theta.core.type.DomainSize;
import hu.bme.mit.theta.core.type.Expr;
import hu.bme.mit.theta.core.type.Type;
import hu.bme.mit.theta.core.type.booltype.BoolType;
import hu.bme.mit.theta.core.utils.ExprUtils;
import hu.bme.mit.theta.core.utils.PathUtils;
import hu.bme.mit.theta.core.utils.indexings.VarIndexing;
import hu.bme.mit.theta.core.utils.indexings.VarIndexingFactory;
import hu.bme.mit.theta.solver.Interpolant;
import hu.bme.mit.theta.solver.ItpMarker;
import hu.bme.mit.theta.solver.ItpPattern;
import hu.bme.mit.theta.solver.ItpSolver;
import hu.bme.mit.theta.solver.z3.Z3SolverFactory;

import java.util.*;
import java.util.function.Function;
import java.util.function.Predicate;

import static hu.bme.mit.theta.core.type.abstracttype.AbstractExprs.Eq;
import static hu.bme.mit.theta.core.type.booltype.BoolExprs.True;

public final class LDGTraceChecker<S extends ExprState, A extends ExprAction> {

	public static int BOUND = 100;

	private final ItpSolver solver;
	private final Expr<BoolType> init;
	private final LDGTrace<S, A> ldgTrace;
	private final Trace<S, A> trace;
	private final int stateCount;
	private final List<VarIndexing> indexings;
	private final ItpMarker satMarker;
	private final ItpMarker unreachableMarker;
	private final ItpPattern pattern;
	private final Set<VarDecl<?>> variables;
	private final Logger logger;

	private LDGTraceChecker(ItpSolver solver, Expr<BoolType> init, LDGTrace<S, A> ldgTrace, Logger logger) {
		this.solver = solver;
		this.init = init;
		this.ldgTrace = ldgTrace;
		this.logger = logger != null ? logger : NullLogger.getInstance();
		trace = ldgTrace.toTrace();
		stateCount = trace.getStates().size();
		indexings = new ArrayList<>(stateCount);
		satMarker = solver.createMarker();
		unreachableMarker = solver.createMarker();
		pattern = solver.createBinPattern(satMarker, unreachableMarker);
		variables = new HashSet<>();
	}

	public static <S extends ExprState, A extends ExprAction> ExprTraceStatus<ItpRefutation> check(final LDGTrace<S, A> ldgTrace, final ItpSolver solver, final Expr<BoolType> init, RefinerStrategy refinerStrategy, Logger logger) {
		ExprTraceStatus<ItpRefutation> status = new LDGTraceChecker<>(solver, init, ldgTrace, logger).check(refinerStrategy);
		solver.reset();
		return status;
	}

	public static <S extends ExprState, A extends ExprAction> ExprTraceStatus<ItpRefutation> check(final LDGTrace<S, A> ldgTrace, final ItpSolver solver, RefinerStrategy refinerStrategy, Logger logger) {
		return check(ldgTrace, solver, True(), refinerStrategy, logger);
	}

	private ExprTraceStatus<ItpRefutation> check(RefinerStrategy strategy) {
		solver.push();
		indexings.add(VarIndexingFactory.indexing(0));
		solver.add(satMarker, PathUtils.unfold(init, indexings.get(0)));
		solver.add(satMarker, PathUtils.unfold(trace.getState(0).toExpr(), indexings.get(0)));

		final int satIndex = findSatIndex();
		if (satIndex < stateCount - 1)
			return infeasibleAsLoopIsUnreachable(satIndex);
		return switch (strategy) {
			case MILANO -> evaluateLoopCutHondaRepeatability(solver.getModel());
			case BOUNDED_UNROLLING -> boundedUnrolling();
		};
	}

	private int findSatIndex() {
		for (int i = 1; i < stateCount; ++i) {
			solver.push();
			indexings.add(indexings.get(i - 1).add(trace.getAction(i - 1).nextIndexing()));
			solver.add(satMarker, PathUtils.unfold(trace.getState(i).toExpr(), indexings.get(i)));
			solver.add(satMarker, PathUtils.unfold(trace.getAction(i - 1).toExpr(), indexings.get(i - 1)));
			variables.addAll(ExprUtils.getVars(trace.getState(i).toExpr()));
			variables.addAll(ExprUtils.getVars(trace.getAction(i - 1).toExpr()));
			if (solver.check().isUnsat()) {
				solver.pop();
				return i - 1;
			}
		}
		return stateCount - 1;
	}

	private ExprTraceStatus<ItpRefutation> infeasibleAsLoopIsUnreachable(int satPrefix) {
		logger.write(Logger.Level.INFO, "Loop was unreachable%n");
		solver.add(unreachableMarker, PathUtils.unfold(trace.getState(satPrefix + 1).toExpr(), indexings.get(satPrefix + 1)));
		solver.add(unreachableMarker, PathUtils.unfold(trace.getAction(satPrefix).toExpr(), indexings.get(satPrefix)));
		return infeasibleThroughInterpolant(satPrefix, indexings.get(satPrefix));
	}

	private ExprTraceStatus<ItpRefutation> infeasibleThroughInterpolant(int satPrefix, VarIndexing indexing) {
		solver.check();
		final Interpolant interpolant = solver.getInterpolant(pattern);
		Expr<BoolType> interpolantExpr = interpolant.eval(satMarker);
		logInterpolant(interpolantExpr);
		try {
			final Expr<BoolType> itpFolded = PathUtils.foldin(interpolantExpr, indexing);
			return ExprTraceStatus.infeasible(ItpRefutation.binary(itpFolded, satPrefix, stateCount));
		} catch (IllegalArgumentException e) {
			logger.write(Logger.Level.INFO, "Interpolant expression: %s; indexing: %s%n", interpolantExpr, indexing);
			throw e;
		}
	}

	private ExprTraceStatus<ItpRefutation> evaluateLoopCutHondaRepeatability(Valuation valuation) {
		for (VarDecl<? extends Type> variable :
				variables) {
			solver.add(unreachableMarker, Eq(PathUtils.unfold(variable.getRef(), indexings.get(ldgTrace.getTail().size())), PathUtils.unfold(variable.getRef(), indexings.get(stateCount - 1))));
			if (solver.check().isSat())
				continue;
			final Interpolant interpolant = solver.getInterpolant(pattern);
			ItpFolder folder = new ItpFolder(indexings.get(stateCount - 1), valuation);
			Expr<BoolType> interpolantExpr = folder.foldIn(interpolant.eval(satMarker));
			logInterpolant(interpolantExpr);
			return ExprTraceStatus.infeasible(ItpRefutation.binary(interpolantExpr, stateCount - 1, stateCount));
		}
		return getItpRefutationFeasible();
	}

	private ExprTraceStatus.Feasible<ItpRefutation> getItpRefutationFeasible() {
		final Valuation finalModel = solver.getModel();
		final ImmutableList.Builder<Valuation> builder = ImmutableList.builder();
		for (final VarIndexing indexing : indexings) {
			builder.add(PathUtils.extractValuation(finalModel, indexing));
		}
		return ExprTraceStatus.feasible(Trace.of(builder.build(), trace.getActions()));
	}

	private ExprTraceStatus<ItpRefutation> boundedUnrolling() {
		Set<VarDecl<?>> usedVariables = Containers.createSet();
		VarIndexing indexingBeforeLoop = indexings.get(ldgTrace.getTail().size());
		VarIndexing indexingAfterLoop = indexings.get(trace.length());
		VarIndexing deltaIndexing = indexingAfterLoop.sub(indexingBeforeLoop);
		expandUsedVariables(usedVariables);
		ExplPrec usedVariablesPrecision = ExplPrec.of(usedVariables);
		int requiredLoops = findSmallestAbstractState(0, BOUND + 1, usedVariablesPrecision);
		if (requiredLoops == BOUND + 1) {
			logger.write(Logger.Level.INFO, "Can't check trace within bound %d, using fallback method%n", BOUND);
			return evaluateLoopCutHondaRepeatability(solver.getModel());
		}
		logger.write(Logger.Level.INFO, "Unrolling loop of trace at most %d times%n", requiredLoops);
		solver.reset();
		VarIndexing loopIndexing = VarIndexingFactory.indexing(0);
		for (int i = 0; i < requiredLoops; i++) {
			solver.push();
			putLoopOnSolver(satMarker, loopIndexing);
			if (solver.check().isUnsat()) {
				solver.pop();
				putLoopOnSolver(unreachableMarker, loopIndexing);
				logger.write(Logger.Level.INFO, "Unrolled loop of trace %d times%n", i + 1);
				return infeasibleThroughInterpolant(ldgTrace.getTail().size(), loopIndexing);
			}
			loopIndexing = loopIndexing.add(deltaIndexing);
			solver.push();
			VarIndexing finalLoopIndexing = loopIndexing;
			variables.forEach(variable -> solver.add(unreachableMarker, Eq(PathUtils.unfold(variable.getRef(), VarIndexingFactory.indexing(0)), PathUtils.unfold(variable.getRef(), finalLoopIndexing))));
			if (solver.check().isSat()) {
				logger.write(Logger.Level.INFO, "Unrolled loop of trace %d times%n", i + 1);
				return getItpRefutationFeasible();
			}
			solver.pop();
		}
		VarIndexing finalLoopIndexing = loopIndexing;
		variables.forEach(variable -> solver.add(unreachableMarker, Eq(PathUtils.unfold(variable.getRef(), VarIndexingFactory.indexing(0)), PathUtils.unfold(variable.getRef(), finalLoopIndexing))));
		return infeasibleThroughInterpolant(ldgTrace.getTail().size(), loopIndexing);
	}

	private int findSmallestAbstractState(int i, int bound, ExplPrec usedVariablesPrecision) {
		var loop = ldgTrace.getLoop();
		if (i == loop.size())
			return bound;
		Function<? super Valuation, ? extends ExplState> func = usedVariablesPrecision::createState;
		Expr<BoolType> expr = loop.get(i).source().getState().toExpr();
		Collection<? extends ExplState> statesForExpr = ExprStates.createStatesForExpr(Z3SolverFactory.getInstance().createSolver(), expr, 0, func, VarIndexingFactory.indexing(0), bound);
		DomainSize currentSize = statesForExpr
				.stream()
				.map(state -> usedVariablesPrecision
						.getVars()
						.stream()
						.filter(Predicate.not(ExprUtils.getVars(state.toExpr())::contains))
						.map(VarDecl::getType)
						.map(Type::getDomainSize)
						.reduce(DomainSize.ONE, DomainSize::multiply)
				)
				.reduce(DomainSize.ZERO, DomainSize::add);
		if (currentSize.isInfinite() || currentSize.isBiggerThan(bound))
			return findSmallestAbstractState(i + 1, bound, usedVariablesPrecision);
		return findSmallestAbstractState(i + 1, currentSize.getFiniteSize().intValue(), usedVariablesPrecision);
	}

	private Set<VarDecl<?>> expandUsedVariables(Set<VarDecl<?>> usedVariables) {
		int currentSize = usedVariables.size();
		variables
				.stream()
				.filter(Predicate.not(usedVariables::contains))
				.forEach(varDecl -> ldgTrace.getLoop().forEach(edge -> {
					if (edge.action() instanceof StmtAction action)
						VarCollectorStmtVisitor.visitAll(action.getStmts(), usedVariables);
					if (edge.action() instanceof ExprMultiAction<?,?> multiAction && (multiAction.getAction() instanceof StmtAction action))
							{VarCollectorStmtVisitor.visitAll(action.getStmts(), usedVariables);
					}
				}));
		if (usedVariables.size() > currentSize)
			return expandUsedVariables(usedVariables);
		return usedVariables;
	}

	private void putLoopOnSolver(ItpMarker marker, VarIndexing startIndexing) {
		VarIndexing loopIndexing = startIndexing;
		for (LDGEdge<S, A> ldgEdge : ldgTrace.getLoop()) {
			solver.add(marker, PathUtils.unfold(ldgEdge.source().getState().toExpr(), loopIndexing));
			A action = ldgEdge.action();
			solver.add(marker, PathUtils.unfold(action.toExpr(), loopIndexing));
			loopIndexing = loopIndexing.add(action.nextIndexing());
		}
		solver.add(marker, PathUtils.unfold(ldgTrace.getLoop().get(ldgTrace.getLoop().size() - 1).target().getState().toExpr(), loopIndexing));
	}

	private void logInterpolant(Expr<BoolType> interpolant) {
		logger.write(Logger.Level.INFO, "Created interpolant %s%n", interpolant);
	}

}
